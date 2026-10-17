package dev.yamod.yandexmusic.music.yandex;

import dev.yamod.yandexmusic.music.MusicException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Подпись {@code GET /get-file-info} из открытого клиента Яндекс Музыки.
 * Это не секрет пользователя и не ключ расшифровки.
 */
public final class FileInfoSign {
    public static final String DESKTOP_CLIENT = "YandexMusicDesktopAppWindows/5.95.0";
    private static final String DESKTOP_SIGN_KEY = "kzqU4XhfCaY6B6JTHODeq5";

    private FileInfoSign() {
    }

    public static String value(String trackId, String quality, String codec, String transport, long timestamp) {
        String message = timestamp + trackId + quality + codec + transport;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(DESKTOP_SIGN_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String encoded = Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
            return encoded.substring(0, encoded.length() - 1);
        } catch (Exception exception) {
            throw new MusicException("Не удалось подписать запрос файла.");
        }
    }
}
