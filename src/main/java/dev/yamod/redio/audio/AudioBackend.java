package dev.yamod.redio.audio;

import java.io.InputStream;

/**
 * Низкоуровневый звук. Конкретная реализация выбрана для прототипа и ещё не встроена в микшер Minecraft.
 */
public interface AudioBackend {
    AudioSession openTone();

    AudioSession openMp3(InputStream stream);
}
