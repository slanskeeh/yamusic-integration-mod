package dev.yamod.yandexmusic.client;

import dev.yamod.yandexmusic.YandexMusicMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class PrototypeKeyMappings {
    public static final String CATEGORY = "key.categories." + YandexMusicMod.MOD_ID;
    public static final KeyMapping OPEN_PROTOTYPE = new KeyMapping(
            "key.yandexmusic.open_prototype",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            CATEGORY
    );

    private PrototypeKeyMappings() {
    }
}
