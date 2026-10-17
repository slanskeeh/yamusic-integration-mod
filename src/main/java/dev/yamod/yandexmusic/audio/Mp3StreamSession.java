package dev.yamod.yandexmusic.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Потоковое воспроизведение незашифрованного mp3. Файл целиком в память не загружается.
 */
public final class Mp3StreamSession implements AudioSession {
    private final InputStream input;
    private final AtomicReference<PlaybackState> state = new AtomicReference<>(PlaybackState.IDLE);
    private final Object lineLock = new Object();
    private volatile String error = "";
    private volatile boolean paused;
    private volatile boolean stopRequested;
    private boolean resourcesClosed;
    private Thread thread;
    private SourceDataLine line;
    private Bitstream bitstream;

    public Mp3StreamSession(InputStream input) {
        if (input == null) {
            throw new AudioException("Аудиопоток не передан.");
        }
        this.input = input;
    }

    @Override
    public synchronized void play() {
        if (state.get() != PlaybackState.IDLE) {
            return;
        }
        state.set(PlaybackState.PLAYING);
        thread = new Thread(this::decodeLoop, "yandexmusic-audio");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void pause() {
        if (state.get() == PlaybackState.PLAYING) {
            paused = true;
        }
    }

    @Override
    public void resume() {
        if (state.get() == PlaybackState.PAUSED) {
            paused = false;
        }
    }

    @Override
    public PlaybackState state() {
        return state.get();
    }

    @Override
    public String errorMessage() {
        return error;
    }

    @Override
    public void close() {
        stopRequested = true;
        paused = false;
        SourceDataLine current;
        synchronized (lineLock) {
            current = line;
        }
        if (current != null) {
            current.stop();
            current.flush();
        }
        Thread worker = thread;
        if (worker != null && worker != Thread.currentThread()) {
            worker.interrupt();
            try {
                worker.join(1_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        closeResources();
        state.compareAndSet(PlaybackState.PLAYING, PlaybackState.STOPPED);
        state.compareAndSet(PlaybackState.PAUSED, PlaybackState.STOPPED);
        state.compareAndSet(PlaybackState.IDLE, PlaybackState.STOPPED);
    }

    private void decodeLoop() {
        try {
            bitstream = new Bitstream(input);
            Decoder decoder = new Decoder();
            while (!stopRequested) {
                if (paused) {
                    pauseLine();
                    state.compareAndSet(PlaybackState.PLAYING, PlaybackState.PAUSED);
                    Thread.sleep(30);
                    continue;
                }
                state.compareAndSet(PlaybackState.PAUSED, PlaybackState.PLAYING);
                Header header = bitstream.readFrame();
                if (header == null) {
                    break;
                }
                if (!(decoder.decodeFrame(header, bitstream) instanceof SampleBuffer buffer)) {
                    throw new AudioException("Декодер mp3 вернул неподдерживаемый буфер.");
                }
                write(buffer);
                bitstream.closeFrame();
            }
            state.compareAndSet(PlaybackState.PLAYING, PlaybackState.STOPPED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            state.compareAndSet(PlaybackState.PLAYING, PlaybackState.STOPPED);
        } catch (Exception exception) {
            if (!stopRequested) {
                error = "Воспроизведение прервано. Поток закрыт, ресурсы освобождены.";
                state.set(PlaybackState.FAILED);
            }
        } finally {
            closeResources();
        }
    }

    private void write(SampleBuffer buffer) {
        SourceDataLine current;
        synchronized (lineLock) {
            if (stopRequested) {
                return;
            }
            ensureLine(buffer);
            current = line;
            if (!current.isRunning()) {
                current.start();
            }
        }
        byte[] pcm = pcm(buffer);
        current.write(pcm, 0, pcm.length);
    }

    private void ensureLine(SampleBuffer buffer) {
        if (line != null) {
            return;
        }
        AudioFormat format = new AudioFormat(buffer.getSampleFrequency(), 16, buffer.getChannelCount(), true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try {
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
        } catch (Exception exception) {
            throw new AudioException("Не удалось открыть аудиоустройство.");
        }
    }

    private void pauseLine() {
        synchronized (lineLock) {
            if (line != null && line.isRunning()) {
                line.stop();
            }
        }
    }

    private static byte[] pcm(SampleBuffer buffer) {
        short[] samples = buffer.getBuffer();
        int length = buffer.getBufferLength();
        byte[] bytes = new byte[length * 2];
        for (int index = 0; index < length; index++) {
            short sample = samples[index];
            bytes[index * 2] = (byte) (sample & 0xff);
            bytes[index * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }
        return bytes;
    }

    private void closeResources() {
        synchronized (lineLock) {
            if (resourcesClosed) {
                return;
            }
            resourcesClosed = true;
            if (line != null) {
                line.stop();
                line.flush();
                line.close();
                line = null;
            }
            if (bitstream != null) {
                try {
                    bitstream.close();
                } catch (Exception ignored) {
                    // Поток уже закрывается ниже.
                }
                bitstream = null;
            }
            try {
                input.close();
            } catch (Exception ignored) {
                // Повторное закрытие потока не является ошибкой сессии.
            }
        }
    }
}
