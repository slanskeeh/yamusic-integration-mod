package dev.yamod.redio;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Точка входа мода. Общий класс не импортирует клиентский GUI и аудио:
 * серверная сторона только загружает мод и ничего не воспроизводит.
 */
@Mod(RedioMod.MOD_ID)
public final class RedioMod {
    public static final String MOD_ID = "redio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RedioMod() {
        LOGGER.info("Redio loaded. Encrypted streams are not played.");
    }
}
