package dev.yamod.yandexmusic.music;

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

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
