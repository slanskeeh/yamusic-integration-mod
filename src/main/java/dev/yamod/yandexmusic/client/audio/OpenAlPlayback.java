package dev.yamod.yandexmusic.client.audio;

import com.mojang.blaze3d.audio.Library;
import dev.yamod.yandexmusic.audio.AudioException;
import dev.yamod.yandexmusic.audio.PlaybackState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.ChannelAccess;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Проигрывает поток через OpenAL Minecraft, то есть в то же устройство, что и игра.
 */
public final class OpenAlPlayback {
    private ChannelAccess.ChannelHandle handle;
    private AudioStream stream;
    private PlaybackState state = PlaybackState.IDLE;
    private String error = "";
    private float volume = 1.0F;

    public synchronized void start(AudioStream audioStream) {
        if (state != PlaybackState.IDLE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ChannelAccess access = minecraft.getSoundManager().soundEngine.channelAccess;
        ChannelAccess.ChannelHandle created;
        try {
            created = access.createHandle(Library.Pool.STREAMING).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw fail("Ожидание звукового канала Minecraft прервано.");
        } catch (ExecutionException | TimeoutException exception) {
            throw fail("Minecraft не выдал звуковой канал.");
        }
        if (created == null) {
            throw fail("Свободного звукового канала Minecraft нет.");
        }
        float appliedVolume = volume;
        created.execute(channel -> {
            channel.disableAttenuation();
            channel.setRelative(true);
            channel.setVolume(appliedVolume);
            channel.attachBufferStream(audioStream);
            channel.play();
        });
        this.handle = created;
        this.stream = audioStream;
        this.state = PlaybackState.PLAYING;
    }

    public synchronized void setVolume(float level) {
        volume = level;
        if (handle != null && (state == PlaybackState.PLAYING || state == PlaybackState.PAUSED)) {
            handle.execute(channel -> channel.setVolume(level));
        }
    }

    public synchronized void pause() {
        if (state == PlaybackState.PLAYING && handle != null) {
            handle.execute(channel -> channel.pause());
            state = PlaybackState.PAUSED;
        }
    }

    public synchronized void resume() {
        if (state == PlaybackState.PAUSED && handle != null) {
            handle.execute(channel -> channel.unpause());
            state = PlaybackState.PLAYING;
        }
    }

    public synchronized PlaybackState state() {
        return state;
    }

    public synchronized String errorMessage() {
        return error;
    }

    public synchronized void close() {
        ChannelAccess.ChannelHandle current = handle;
        AudioStream currentStream = stream;
        handle = null;
        stream = null;
        if (current != null) {
            // release() обнуляет канал сразу и с любого потока. Звуковой поток игры
            // потом вызывает updateStream у уже пустого канала и падает на каждом тике.
            // Остановка через execute остаётся в потоке звука: следующий тик сам освободит канал.
            current.execute(channel -> channel.stop());
        }
        if (currentStream != null) {
            try {
                currentStream.close();
            } catch (Exception ignored) {
                // Закрытие потока не должно срывать остановку сессии.
            }
        }
        if (state != PlaybackState.FAILED) {
            state = PlaybackState.STOPPED;
        }
    }

    private AudioException fail(String message) {
        error = message;
        state = PlaybackState.FAILED;
        return new AudioException(message);
    }
}
