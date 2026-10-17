package dev.yamod.yandexmusic.client;

import dev.yamod.yandexmusic.audio.AudioBackend;
import dev.yamod.yandexmusic.audio.PlaybackController;
import dev.yamod.yandexmusic.client.audio.MinecraftAudioBackend;

public final class PrototypeAudio {
    private static final PlaybackController CONTROLLER = new PlaybackController();
    private static final AudioBackend BACKEND = new MinecraftAudioBackend();

    private PrototypeAudio() {
    }

    public static PlaybackController controller() {
        return CONTROLLER;
    }

    public static AudioBackend backend() {
        return BACKEND;
    }

    public static void release() {
        CONTROLLER.release();
    }
}
