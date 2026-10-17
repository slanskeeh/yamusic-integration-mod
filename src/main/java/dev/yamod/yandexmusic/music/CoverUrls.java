package dev.yamod.yandexmusic.music;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Собирает https-адрес обложки. Чужой хост и http отбрасываются.
 */
public final class CoverUrls {
    private CoverUrls() {
    }

    public static Optional<String> fromCoverUri(String coverUri) {
        if (coverUri == null || coverUri.isBlank()) {
            return Optional.empty();
        }
        String value = coverUri.trim().replace("%%", "200x200");
        if (value.contains("..") || value.contains("\\") || value.contains(" ")) {
            return Optional.empty();
        }
        if (value.startsWith("http://")) {
            return Optional.empty();
        }
        if (!value.startsWith("https://")) {
            value = "https://" + value;
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean yandex = host.equals("yandex.net") || host.endsWith(".yandex.net")
                || host.equals("yandex.ru") || host.endsWith(".yandex.ru")
                || host.equals("yandex.com") || host.endsWith(".yandex.com");
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !yandex) {
            return Optional.empty();
        }
        return Optional.of(uri.toString());
    }
}
