package dev.yamod.redio.client;

/**
 * Громкость личного плеера. Общую громкость Minecraft не меняет.
 */
public final class PersonalVolume {
    private static float level = 1.0F;

    private PersonalVolume() {
    }

    public static synchronized float get() {
        return level;
    }

    public static synchronized float change(float delta) {
        level = Math.max(0.0F, Math.min(1.0F, level + delta));
        return level;
    }

    public static synchronized void set(float value) {
        level = Math.max(0.0F, Math.min(1.0F, value));
    }

    public static synchronized String label() {
        return Math.round(level * 100) + "%";
    }
}
