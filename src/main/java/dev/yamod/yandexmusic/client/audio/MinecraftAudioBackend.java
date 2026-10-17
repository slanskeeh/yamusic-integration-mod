package dev.yamod.yandexmusic.client.audio;

import dev.yamod.yandexmusic.audio.AudioBackend;
import dev.yamod.yandexmusic.audio.AudioSession;

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
