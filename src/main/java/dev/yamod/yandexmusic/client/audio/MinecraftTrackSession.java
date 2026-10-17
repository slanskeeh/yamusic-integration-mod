package dev.yamod.yandexmusic.client.audio;

import dev.yamod.yandexmusic.YandexMusicMod;
import dev.yamod.yandexmusic.audio.AudioException;
import dev.yamod.yandexmusic.audio.AudioSession;
import dev.yamod.yandexmusic.audio.ExternalMp3Decoder;
import dev.yamod.yandexmusic.audio.Mp3Probe;
import dev.yamod.yandexmusic.audio.PlaybackState;

import javax.sound.sampled.AudioFormat;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Декодирует незашифрованный mp3 и отдаёт его звуковому движку Minecraft.
 */
public final class MinecraftTrackSession implements AudioSession {
    private static final int QUEUE_CAPACITY = 256;

    private final InputStream input;
    private final BlockingQueue<byte[]> chunks = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicInteger bufferedBytes = new AtomicInteger();
    private final AtomicInteger consumedBytes = new AtomicInteger();
    private final AtomicInteger decodedFrames = new AtomicInteger();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final OpenAlPlayback playback = new OpenAlPlayback();
    private volatile AudioFormat format;
    private volatile String error = "";
    private volatile boolean stopRequested;
    private volatile ExternalMp3Decoder external;
    private Thread decoder;

    public MinecraftTrackSession(InputStream input) {
        if (input == null) {
            throw new AudioException("Аудиопоток не передан.");
        }
        this.input = input;
    }

    @Override
    public void play() {
        if (playback.state() != PlaybackState.IDLE) {
            return;
        }
        decoder = new Thread(this::decodeLoop, "yandexmusic-audio");
        decoder.setDaemon(true);
        decoder.start();
        try {
            waitForAudio();
            playback.start(new PcmAudioStream(format, chunks, bufferedBytes, consumedBytes, finished));
            YandexMusicMod.LOGGER.info(
                    "Yandex track attached to Minecraft audio: {} Hz, {} ch",
                    (int) format.getSampleRate(),
                    format.getChannels()
            );
        } catch (RuntimeException exception) {
            if (error.isEmpty()) {
                error = exception.getMessage() == null ? "Не удалось начать воспроизведение." : exception.getMessage();
            }
            close();
            throw exception;
        }
    }

    @Override
    public long positionMillis() {
        javax.sound.sampled.AudioFormat current = format;
        if (current == null || current.getSampleRate() <= 0 || current.getFrameSize() <= 0) {
            return 0;
        }
        double frames = consumedBytes.get() / (double) current.getFrameSize();
        return (long) (frames / current.getSampleRate() * 1000.0);
    }

    @Override
    public void setVolume(float volume) {
        playback.setVolume(volume);
    }

    @Override
    public void pause() {
        playback.pause();
    }

    @Override
    public void resume() {
        playback.resume();
    }

    @Override
    public PlaybackState state() {
        return playback.state();
    }

    @Override
    public String errorMessage() {
        return error.isEmpty() ? playback.errorMessage() : error;
    }

    @Override
    public void close() {
        stopRequested = true;
        finished.set(true);
        chunks.clear();
        playback.close();
        ExternalMp3Decoder current = external;
        if (current != null) {
            current.close();
        }
        Thread worker = decoder;
        if (worker != null && worker != Thread.currentThread()) {
            worker.interrupt();
        }
        try {
            input.close();
        } catch (Exception ignored) {
            // Поток уже мог закрыть декодер.
        }
    }

    private void waitForAudio() {
        long started = System.nanoTime();
        long deadline = started + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < deadline) {
            if (!error.isEmpty()) {
                throw new AudioException(error);
            }
            AudioFormat current = format;
            if (current != null) {
                int needed = (int) current.getSampleRate() * current.getChannels() * 2;
                if (bufferedBytes.get() >= needed || (finished.get() && bufferedBytes.get() > 0)) {
                    return;
                }
            }
            if (finished.get()) {
                break;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AudioException("Ожидание звука прервано.");
            }
        }
        if (!error.isEmpty()) {
            throw new AudioException(error);
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        int frames = decodedFrames.get();
        throw new AudioException(frames == 0
                ? "Декодер закрыл файл через " + elapsedMs + " мс и не отдал ни одного кадра. Таймер на 25 секунд здесь не ждал."
                : "За " + elapsedMs + " мс накопилось только " + frames + " кадров mp3, этого мало для старта.");
    }

    private void decodeLoop() {
        ExternalMp3Decoder decoder = null;
        try {
            byte[] data = input.readAllBytes();
            int preview = Math.min(4, data.length);
            byte[] magic = new byte[preview];
            System.arraycopy(data, 0, magic, 0, preview);
            YandexMusicMod.LOGGER.info("Yandex mp3 buffered: {} bytes, magic {}", data.length, hex(magic));
            if (!Mp3Probe.looksLikeMp3(magic)) {
                throw new AudioException(Mp3Probe.describe(magic));
            }
            decoder = ExternalMp3Decoder.start(data);
            external = decoder;
            if (stopRequested) {
                return;
            }
            format = decoder.readFormat();
            YandexMusicMod.LOGGER.info(
                    "Yandex external decoder format: {} Hz, {} ch",
                    (int) format.getSampleRate(),
                    format.getChannels()
            );
            byte[] buffer = new byte[8192];
            byte[] pending = new byte[1];
            boolean hasPending = false;
            while (!stopRequested) {
                int count = decoder.read(buffer);
                if (count < 0) {
                    break;
                }
                if (count == 0) {
                    continue;
                }
                int offset = 0;
                if (hasPending) {
                    byte[] first = new byte[] {pending[0], buffer[0]};
                    offer(first);
                    offset = 1;
                    hasPending = false;
                }
                int even = offset + ((count - offset) & ~1);
                if (even > offset) {
                    offer(java.util.Arrays.copyOfRange(buffer, offset, even));
                }
                if (even < count) {
                    pending[0] = buffer[count - 1];
                    hasPending = true;
                }
            }
            if (decodedFrames.get() == 0 && !stopRequested) {
                throw new AudioException("Внешний декодер не вернул звук из файла на " + data.length + " байт.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!stopRequested && error.isEmpty()) {
                error = "Декодирование прервано.";
                YandexMusicMod.LOGGER.error("Failed to decode Yandex track", exception);
            }
        } catch (Exception exception) {
            if (!stopRequested) {
                String message = exception.getMessage();
                error = message == null || message.isBlank()
                        ? "Звук не декодирован: " + exception.getClass().getSimpleName()
                        : message;
                YandexMusicMod.LOGGER.error("Failed to decode Yandex track", exception);
            }
        } finally {
            finished.set(true);
            if (decoder != null) {
                decoder.close();
            }
        }
    }

    private void offer(byte[] pcm) throws InterruptedException {
        bufferedBytes.addAndGet(pcm.length);
        decodedFrames.incrementAndGet();
        while (!stopRequested && !chunks.offer(pcm, 100, TimeUnit.MILLISECONDS)) {
            // Очередь полна: пауза или звуковой движок ещё не забрал данные.
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.isEmpty() ? "empty" : builder.toString();
    }
}
