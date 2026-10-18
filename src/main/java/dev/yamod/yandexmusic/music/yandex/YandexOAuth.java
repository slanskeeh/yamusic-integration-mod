package dev.yamod.yandexmusic.music.yandex;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yamod.yandexmusic.music.MusicException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Вход через OAuth Device Flow официального клиента Яндекс Музыки.
 * Пароль пользователя сюда не передаётся. Токен из ответа не пишется в сообщения.
 */
public final class YandexOAuth {
    static final String CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d";
    private static final String CLIENT_SECRET = "53bc75238f0c4d08a118e51fe9203300";
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final HttpTransport transport;

    public YandexOAuth(HttpTransport transport) {
        if (transport == null) {
            throw new MusicException("HTTP-транспорт не задан.");
        }
        this.transport = transport;
    }

    public DeviceLogin requestCode() {
        String body = form(
                "client_id", CLIENT_ID,
                "device_id", deviceId(),
                "device_name", "Minecraft"
        );
        HttpResponseData response = transport.exchange(new HttpRequestData(
                "POST",
                "https://oauth.yandex.ru/device/code",
                headers(),
                body
        ));
        return parseCode(response.status(), response.body());
    }

    public Optional<String> poll(String deviceCode) {
        if (deviceCode == null || !deviceCode.matches("[A-Za-z0-9._\\-]{8,256}")) {
            throw new MusicException("Код устройства отклонён.");
        }
        String body = form(
                "grant_type", "device_code",
                "code", deviceCode,
                "client_id", CLIENT_ID,
                "client_secret", CLIENT_SECRET
        );
        HttpResponseData response = transport.exchange(new HttpRequestData(
                "POST",
                "https://oauth.yandex.ru/token",
                headers(),
                body
        ));
        return parseToken(response.status(), response.body());
    }

    static DeviceLogin parseCode(int status, String body) {
        JsonObject object = object(body);
        if (object.has("error")) {
            throw new MusicException(oauthError(text(object, "error")));
        }
        if (status < 200 || status >= 300) {
            throw new MusicException("Яндекс не выдал код входа.");
        }
        String deviceCode = text(object, "device_code");
        String userCode = text(object, "user_code");
        String page = first(object, "verification_uri_complete", "verification_url", "verification_uri");
        if (!deviceCode.matches("[A-Za-z0-9._\\-]{8,256}") || !userCode.matches("[A-Za-z0-9\\-]{4,64}")) {
            throw new MusicException("Яндекс прислал код входа неожиданного вида.");
        }
        if (!allowedPage(page)) {
            throw new MusicException("Страница подтверждения ведёт не на Яндекс и была отклонена.");
        }
        int expires = number(object, "expires_in", 300);
        int interval = number(object, "interval", 5);
        return new DeviceLogin(deviceCode, userCode, page, Math.min(expires, 1200), Math.max(5, Math.min(interval, 30)));
    }

    static Optional<String> parseToken(int status, String body) {
        JsonObject object = object(body);
        if (object.has("error")) {
            String error = text(object, "error");
            if ("authorization_pending".equals(error)) {
                return Optional.empty();
            }
            throw new MusicException(oauthError(error));
        }
        if (status < 200 || status >= 300) {
            throw new MusicException("Яндекс не подтвердил вход.");
        }
        String token = text(object, "access_token");
        if (token.length() < 20 || token.length() > 2000 || token.chars().anyMatch(ch -> ch <= 32 || ch > 126)) {
            throw new MusicException("Яндекс не вернул токен.");
        }
        return Optional.of(token);
    }

    public static boolean allowedPage(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException exception) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("yandex.ru") || host.endsWith(".yandex.ru")
                || host.equals("yandex.com") || host.endsWith(".yandex.com")
                || host.equals("yandex.net") || host.endsWith(".yandex.net")
                || host.equals("ya.ru") || host.endsWith(".ya.ru");
    }

    private static String oauthError(String error) {
        return switch (error) {
            case "access_denied" -> "Вход на странице Яндекса отменён.";
            case "expired_token" -> "Код входа истёк. Начните ещё раз.";
            case "invalid_client" -> "Яндекс не принял этот способ входа.";
            default -> "Яндекс не подтвердил вход.";
        };
    }

    private static JsonObject object(String body) {
        if (body == null || body.isBlank()) {
            throw new MusicException("Яндекс не ответил на вход.");
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                throw new MusicException("Яндекс ответил на вход неожиданным текстом.");
            }
            return root.getAsJsonObject();
        } catch (MusicException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MusicException("Яндекс ответил на вход неожиданным текстом.");
        }
    }

    private static String text(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull() || !object.get(field).isJsonPrimitive()) {
            return "";
        }
        return object.get(field).getAsString();
    }

    private static String first(JsonObject object, String... fields) {
        for (String field : fields) {
            String value = text(object, field);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static int number(JsonObject object, String field, int fallback) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(field).getAsInt();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static Map<String, String> headers() {
        return Map.of(
                "User-Agent", "yandexmusic-minecraft/0.1.0",
                "Accept", "application/json"
        );
    }

    private static String form(String... pairs) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (i > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8));
            body.append('=');
            body.append(URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private static String deviceId() {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    public record DeviceLogin(String deviceCode, String userCode, String page, int expiresInSeconds, int intervalSeconds) {
    }
}
