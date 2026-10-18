package dev.yamod.redio.client.audio;

import dev.yamod.redio.RedioMod;
import dev.yamod.redio.audio.AudioException;
import dev.yamod.redio.audio.AudioSession;
import dev.yamod.redio.audio.ExternalMp3Decoder;
import dev.yamod.redio.audio.Mp3Probe;
import dev.yamod.redio.audio.PlaybackState;

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
    private final AtomicInteger seekGeneration = new AtomicInteger();
    private final AtomicInteger streamGeneration = new AtomicInteger();
    private final AtomicBoolean ended = new AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicLong skipBytes = new java.util.concurrent.atomic.AtomicLong();
    private final OpenAlPlayback playback = new OpenAlPlayback();
    private volatile AudioFormat format;
    private volatile byte[] audio = new byte[0];
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
        decoder = new Thread(this::decodeLoop, "redio-audio");
        decoder.setDaemon(true);
        decoder.start();
        try {
            waitForAudio();
            playback.start(new PcmAudioStream(
                    format,
                    chunks,
                    bufferedBytes,
                    consumedBytes,
                    finished,
                    streamGeneration,
                    ended
            ));
            RedioMod.LOGGER.info(
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
    public void seek(long millis) {
        AudioFormat current = format;
        if (current == null || audio.length == 0 || stopRequested) {
            return;
        }
        int frame = Math.max(1, current.getFrameSize());
        long frames = (long) (Math.max(0, millis) / 1000.0 * current.getSampleRate());
        long bytes = frames * frame;
        skipBytes.set(bytes);
        consumedBytes.set(bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes);
        bufferedBytes.set(0);
        ended.set(false);
        chunks.clear();
        streamGeneration.incrementAndGet();
        seekGeneration.incrementAndGet();
        ExternalMp3Decoder currentDecoder = external;
        if (currentDecoder != null) {
            currentDecoder.close();
        }
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
    public boolean naturallyEnded() {
        return ended.get() && !stopRequested;
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
            audio = data;
            int preview = Math.min(4, data.length);
            byte[] magic = new byte[preview];
            System.arraycopy(data, 0, magic, 0, preview);
            RedioMod.LOGGER.info("Yandex mp3 buffered: {} bytes, magic {}", data.length, hex(magic));
            if (!Mp3Probe.looksLikeMp3(magic)) {
                throw new AudioException(Mp3Probe.describe(magic));
            }
            while (!stopRequested) {
                int generation = seekGeneration.get();
                long skip = skipBytes.get();
                boolean restart = false;
                decoder = ExternalMp3Decoder.start(data);
                external = decoder;
                try {
                    AudioFormat decoded = decoder.readFormat();
                    if (format == null) {
                        format = decoded;
                    }
                    RedioMod.LOGGER.info(
                            "Yandex external decoder format: {} Hz, {} ch",
                            (int) decoded.getSampleRate(),
                            decoded.getChannels()
                    );
                    restart = pump(decoder, generation, skip);
                } catch (Exception exception) {
                    restart = !stopRequested && seekGeneration.get() != generation;
                    if (!restart) {
                        throw exception;
                    }
                } finally {
                    decoder.close();
                    if (external == decoder) {
                        external = null;
                    }
                    decoder = null;
                }
                if (!restart) {
                    break;
                }
                chunks.clear();
            }
            if (decodedFrames.get() == 0 && !stopRequested) {
                throw new AudioException("Внешний декодер не вернул звук из файла на " + data.length + " байт.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!stopRequested && error.isEmpty()) {
                error = "Декодирование прервано.";
                RedioMod.LOGGER.error("Failed to decode Yandex track", exception);
            }
        } catch (Exception exception) {
            if (!stopRequested) {
                String message = exception.getMessage();
                error = message == null || message.isBlank()
                        ? "Звук не декодирован: " + exception.getClass().getSimpleName()
                        : message;
                RedioMod.LOGGER.error("Failed to decode Yandex track", exception);
            }
        } finally {
            finished.set(true);
            if (decoder != null) {
                decoder.close();
            }
        }
    }

    private boolean pump(ExternalMp3Decoder decoder, int generation, long skip) throws Exception {
        byte[] buffer = new byte[8192];
        byte[] pending = new byte[1];
        boolean hasPending = false;
        long discarded = 0;
        while (!stopRequested && seekGeneration.get() == generation) {
            int count = decoder.read(buffer);
            if (count < 0) {
                break;
            }
            if (count == 0) {
                continue;
            }
            int offset = 0;
            if (hasPending) {
                discarded = take(new byte[] {pending[0], buffer[0]}, skip, discarded, generation);
                offset = 1;
                hasPending = false;
            }
            int even = offset + ((count - offset) & ~1);
            if (even > offset) {
                discarded = take(java.util.Arrays.copyOfRange(buffer, offset, even), skip, discarded, generation);
            }
            if (even < count) {
                pending[0] = buffer[count - 1];
                hasPending = true;
            }
        }
        return seekGeneration.get() != generation;
    }

    private long take(byte[] pcm, long skip, long discarded, int generation) throws InterruptedException {
        if (pcm.length == 0 || seekGeneration.get() != generation) {
            return discarded;
        }
        if (discarded < skip) {
            long need = skip - discarded;
            if (pcm.length <= need) {
                return discarded + pcm.length;
            }
            int drop = (int) need;
            int frame = format == null ? 2 : Math.max(1, format.getFrameSize());
            drop -= drop % frame;
            if (drop >= pcm.length) {
                return discarded + pcm.length;
            }
            pcm = java.util.Arrays.copyOfRange(pcm, drop, pcm.length - ((pcm.length - drop) % frame));
            discarded = skip;
        }
        if (pcm.length > 0 && seekGeneration.get() == generation) {
            offer(pcm, generation);
        }
        return discarded;
    }

    private void offer(byte[] pcm, int generation) throws InterruptedException {
        while (!stopRequested && seekGeneration.get() == generation && !chunks.offer(pcm, 100, TimeUnit.MILLISECONDS)) {
            // Очередь полна: пауза или звуковой движок ещё не забрал данные.
        }
        if (seekGeneration.get() != generation) {
            return;
        }
        bufferedBytes.addAndGet(pcm.length);
        decodedFrames.incrementAndGet();
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.isEmpty() ? "empty" : builder.toString();
    }
}
