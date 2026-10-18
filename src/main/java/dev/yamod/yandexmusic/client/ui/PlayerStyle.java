package dev.yamod.yandexmusic.client.ui;

import dev.yamod.yandexmusic.client.ClientTokenStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Цвета и язык плеера. Пишутся в config/yandexmusic/appearance.properties, рядом с токеном, но не в него.
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
    private static boolean loaded;

    private PlayerStyle() {
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = appearanceFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            return;
        }
        theme = "LIGHT".equalsIgnoreCase(properties.getProperty("theme", "DARK")) ? Theme.LIGHT : Theme.DARK;
        english = Boolean.parseBoolean(properties.getProperty("english", "false"));
        String hex = properties.getProperty("accent", "");
        if (hex != null && hex.matches("[0-9A-Fa-f]{8}")) {
            accent = (int) Long.parseLong(hex, 16);
        }
    }

    public static Theme theme() {
        load();
        return theme;
    }

    public static void theme(Theme value) {
        load();
        theme = value == null ? Theme.DARK : value;
        save();
    }

    public static int accent() {
        load();
        return accent;
    }

    public static void accent(int color) {
        load();
        accent = color;
        save();
    }

    public static boolean english() {
        load();
        return english;
    }

    public static void english(boolean value) {
        load();
        english = value;
        save();
    }

    public static String text(String russian, String englishText) {
        load();
        return english ? englishText : russian;
    }

    public static int window() {
        load();
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
        load();
        return theme == Theme.DARK ? 0xFFF5F5F5 : 0xFF161616;
    }

    public static int muted() {
        return theme == Theme.DARK ? 0xFF9A9A9A : 0xFF5E5E5E;
    }

    public static int track() {
        return theme == Theme.DARK ? 0xFF3A3A3A : 0xFFD8D8D8;
    }

    private static void save() {
        Path file = appearanceFile();
        Properties properties = new Properties();
        properties.setProperty("theme", theme.name());
        properties.setProperty("english", Boolean.toString(english));
        properties.setProperty("accent", String.format("%08X", accent & 0xFFFFFFFFL));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "Yandex Music appearance");
            }
        } catch (IOException exception) {
            // внешний вид остаётся в памяти, если файл недоступен
        }
    }

    private static Path appearanceFile() {
        return ClientTokenStore.file().getParent().resolve("appearance.properties");
    }
}
