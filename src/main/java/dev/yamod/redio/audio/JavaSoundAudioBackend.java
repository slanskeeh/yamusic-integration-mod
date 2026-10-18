package dev.yamod.redio.audio;

import java.io.InputStream;

public final class JavaSoundAudioBackend implements AudioBackend {
    @Override
    public AudioSession openTone() {
        return new ToneSession();
    }

    @Override
    public AudioSession openMp3(InputStream stream) {
        return new Mp3StreamSession(stream);
    }
}
