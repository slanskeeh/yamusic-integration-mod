package dev.yamod.yandexmusic;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Точка входа мода. Общий класс не импортирует клиентский GUI и аудио:
 * серверная сторона только загружает мод и ничего не воспроизводит.
 */
@Mod(YandexMusicMod.MOD_ID)
public final class YandexMusicMod {
    public static final String MOD_ID = "yandexmusic";
    public static final Logger LOGGER = LogUtils.getLogger();

    public YandexMusicMod() {
        LOGGER.info("Yandex Music Integration loaded. Phase 1 prototype: encrypted streams are not played.");
    }
}
