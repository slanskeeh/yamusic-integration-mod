package dev.yamod.redio.client.audio;

import dev.yamod.redio.audio.AudioBackend;
import dev.yamod.redio.audio.AudioSession;

import java.io.InputStream;

public final class MinecraftAudioBackend implements AudioBackend {
    @Override
    public AudioSession openTone() {
        return new MinecraftToneSession();
    }

    @Override
    public AudioSession openMp3(InputStream stream) {
        return new MinecraftTrackSession(stream);
    }
}
