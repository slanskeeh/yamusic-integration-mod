package dev.yamod.yandexmusic.client;

import dev.yamod.yandexmusic.audio.PlaybackState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicManager;

/**
 * Пока играет сессия мода, фоновая музыка Minecraft не запускается.
 */
public final class GameMusic {
    private static boolean holding;

    private GameMusic() {
    }

    public static void tick() {
        PlaybackState state = PrototypeAudio.controller().state();
        boolean active = state == PlaybackState.PLAYING || state == PlaybackState.PAUSED;
        MusicManager music = Minecraft.getInstance().getMusicManager();
        if (active) {
            if (!holding) {
                music.stopPlaying();
                holding = true;
            }
            music.nextSongDelay = 200;
            return;
        }
        if (holding) {
            music.nextSongDelay = 40;
            holding = false;
        }
    }
}
