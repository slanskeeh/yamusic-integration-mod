package dev.yamod.redio.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;

/**
 * Короткий синусоидальный тон. Это проверка жизненного цикла звука, а не музыка Яндекса.
 */
public final class ToneSession implements AudioSession {
    private Clip clip;
    private PlaybackState state = PlaybackState.IDLE;
    private String error = "";

    @Override
    public synchronized void play() {
        if (state != PlaybackState.IDLE) {
            return;
        }
        try {
            clip = AudioSystem.getClip();
            clip.open(tone());
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            state = PlaybackState.PLAYING;
        } catch (Exception exception) {
            closeClip();
            state = PlaybackState.FAILED;
            error = "Не удалось открыть аудиоустройство для тестового тона.";
            throw new AudioException(error);
        }
    }

    @Override
    public synchronized void pause() {
        if (state == PlaybackState.PLAYING && clip != null) {
            clip.stop();
            state = PlaybackState.PAUSED;
        }
    }

    @Override
    public synchronized void resume() {
        if (state == PlaybackState.PAUSED && clip != null) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            state = PlaybackState.PLAYING;
        }
    }

    @Override
    public synchronized PlaybackState state() {
        return state;
    }

    @Override
    public synchronized String errorMessage() {
        return error;
    }

    @Override
    public synchronized void close() {
        closeClip();
        if (state != PlaybackState.FAILED) {
            state = PlaybackState.STOPPED;
        }
    }

    private void closeClip() {
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }

    private static AudioInputStream tone() {
        int sampleRate = 44_100;
        int frames = sampleRate;
        byte[] pcm = new byte[frames * 2];
        for (int index = 0; index < frames; index++) {
            short sample = (short) (Math.sin(2 * Math.PI * 440 * index / sampleRate) * 4_000);
            pcm[index * 2] = (byte) (sample & 0xff);
            pcm[index * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        return new AudioInputStream(new ByteArrayInputStream(pcm), format, frames);
    }
}
