package dev.yamod.yandexmusic.client.audio;

import dev.yamod.yandexmusic.audio.AudioSession;
import dev.yamod.yandexmusic.audio.PlaybackState;
import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Короткий тон через звук Minecraft. Нужен, чтобы проверить устройство без трека Яндекса.
 */
public final class MinecraftToneSession implements AudioSession {
    private final OpenAlPlayback playback = new OpenAlPlayback();
    private final AtomicBoolean closed = new AtomicBoolean();

    @Override
    public void play() {
        playback.start(new ToneStream(closed));
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
        return playback.errorMessage();
    }

    @Override
    public void close() {
        closed.set(true);
        playback.close();
    }

    private static final class ToneStream implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(44_100, 16, 1, true, false);
        private final AtomicBoolean closed;
        private int frame;

        private ToneStream(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer read(int size) {
            if (closed.get()) {
                return null;
            }
            int samples = Math.max(1, size / 2);
            ByteBuffer buffer = ByteBuffer.allocateDirect(samples * 2);
            for (int index = 0; index < samples; index++) {
                short sample = (short) (Math.sin(2 * Math.PI * 440 * frame / 44_100) * 4_000);
                frame++;
                buffer.put((byte) (sample & 0xff));
                buffer.put((byte) ((sample >> 8) & 0xff));
            }
            buffer.flip();
            return buffer;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
