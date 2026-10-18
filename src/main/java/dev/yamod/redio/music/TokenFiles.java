package dev.yamod.redio.music;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Читает токен из переменной окружения или из локального файла.
 * Значение не печатается и не возвращается в сообщениях об ошибках.
 */
public final class TokenFiles {
    private TokenFiles() {
    }

    public static Optional<String> read(String environmentValue, Path file) throws IOException {
        String fromEnvironment = normalize(environmentValue);
        if (fromEnvironment != null) {
            return Optional.of(fromEnvironment);
        }
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        String fromFile = Files.readString(file, StandardCharsets.UTF_8);
        if (fromFile.startsWith("\uFEFF")) {
            fromFile = fromFile.substring(1);
        }
        for (String line : fromFile.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String token = normalize(trimmed);
            return token == null ? Optional.empty() : Optional.of(token);
        }
        return Optional.empty();
    }

    /**
     * Достаёт сам токен из вставленного адреса или строки. Пароль и лишний текст не сохраняются.
     */
    public static String extract(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("\uFEFF")) {
            trimmed = trimmed.substring(1).trim();
        }
        int marker = lower(trimmed).indexOf("access_token=");
        if (marker >= 0) {
            String tail = trimmed.substring(marker + "access_token=".length());
            int cut = tail.length();
            for (int i = 0; i < tail.length(); i++) {
                char ch = tail.charAt(i);
                if (ch == '&' || ch == '#' || ch == ' ' || ch == '"' || ch == '\'') {
                    cut = i;
                    break;
                }
            }
            trimmed = tail.substring(0, cut).trim();
        }
        if (trimmed.regionMatches(true, 0, "OAuth ", 0, 6)) {
            trimmed = trimmed.substring(6).trim();
        }
        if (trimmed.length() < 20 || trimmed.length() > 2000) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch <= 32 || ch > 126) {
                return null;
            }
        }
        return trimmed;
    }

    private static String lower(String value) {
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
