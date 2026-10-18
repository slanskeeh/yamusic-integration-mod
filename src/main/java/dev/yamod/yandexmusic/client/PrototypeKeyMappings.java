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
    public static final KeyMapping PLAY_PAUSE = new KeyMapping(
            "key.yandexmusic.play_pause",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
    );
    public static final KeyMapping PREVIOUS = new KeyMapping(
            "key.yandexmusic.previous",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    );
    public static final KeyMapping NEXT = new KeyMapping(
            "key.yandexmusic.next",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            CATEGORY
    );

    private PrototypeKeyMappings() {
    }
}
