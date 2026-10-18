package dev.yamod.redio.music;

/**
 * Убирает значение токена из текста, который может попасть в интерфейс или лог.
 */
public final class Secrets {
    private Secrets() {
    }

    public static String redact(String text, String secret) {
        if (text == null || text.isEmpty() || secret == null || secret.length() < 4) {
            return text == null ? "" : text;
        }
        return text.replace(secret, "<redacted>");
    }
}
