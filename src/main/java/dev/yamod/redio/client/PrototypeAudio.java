package dev.yamod.redio.client;

import dev.yamod.redio.audio.AudioBackend;
import dev.yamod.redio.audio.PlaybackController;
import dev.yamod.redio.client.audio.MinecraftAudioBackend;

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
