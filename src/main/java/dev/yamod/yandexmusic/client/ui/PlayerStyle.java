package dev.yamod.yandexmusic.client.ui;

/**
 * Цвета и переключатели внешнего вида. Живут только в этой сессии клиента.
 */
public final class PlayerStyle {
    public enum Theme {
        DARK,
        LIGHT
    }

    public static final int[] ACCENTS = {
            0xFF8B3DFF,
            0xFFFF7A18,
            0xFFFFC400,
            0xFF3D7EFF,
            0xFF3DDC97,
            0xFF2EC4B6
    };

    private static Theme theme = Theme.DARK;
    private static int accent = ACCENTS[0];
    private static boolean english;

    private PlayerStyle() {
    }

    public static Theme theme() {
        return theme;
    }

    public static void theme(Theme value) {
        theme = value == null ? Theme.DARK : value;
    }

    public static int accent() {
        return accent;
    }

    public static void accent(int color) {
        accent = color;
    }

    public static boolean english() {
        return english;
    }

    public static void english(boolean value) {
        english = value;
    }

    public static String text(String russian, String englishText) {
        return english ? englishText : russian;
    }

    public static int window() {
        return theme == Theme.DARK ? 0xFF121212 : 0xFFF4F4F4;
    }

    public static int sidebar() {
        return theme == Theme.DARK ? 0xFF0E0E0E : 0xFFE7E7E7;
    }

    public static int bar() {
        return theme == Theme.DARK ? 0xFF181818 : 0xFFFCFCFC;
    }

    public static int card() {
        return theme == Theme.DARK ? 0xFF1C1C1C : 0xFFFFFFFF;
    }

    public static int cardHover() {
        return theme == Theme.DARK ? 0xFF282828 : 0xFFF3F3F3;
    }

    public static int field() {
        return theme == Theme.DARK ? 0xFF2A2A2A : 0xFFFFFFFF;
    }

    public static int line() {
        return theme == Theme.DARK ? 0xFF2C2C2C : 0xFFD5D5D5;
    }

    public static int ink() {
        return theme == Theme.DARK ? 0xFFF5F5F5 : 0xFF161616;
    }

    public static int muted() {
        return theme == Theme.DARK ? 0xFF9A9A9A : 0xFF5E5E5E;
    }

    public static int track() {
        return theme == Theme.DARK ? 0xFF3A3A3A : 0xFFD8D8D8;
    }
}
