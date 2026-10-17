package dev.yamod.yandexmusic.music.yandex;

import com.google.gson.JsonElement;
import dev.yamod.yandexmusic.music.MusicException;
import dev.yamod.yandexmusic.music.Secrets;
import dev.yamod.yandexmusic.music.TrackIds;
import dev.yamod.yandexmusic.music.model.AccountSnapshot;
import dev.yamod.yandexmusic.music.model.DownloadOption;
import dev.yamod.yandexmusic.music.model.TrackMetadata;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Минимальный клиент неофициального API: статус аккаунта, один трек и старый download-info.
 * Токен уходит только на {@code api.music.yandex.net}.
 */
public final class YandexApiClient {
    public static final String BASE_URL = "https://api.music.yandex.net";
    static final String CLIENT_HEADER = "YandexMusicAndroid/24023621";

    private final HttpTransport transport;
    private final String token;

    public YandexApiClient(HttpTransport transport, String token) {
        if (transport == null) {
            throw new MusicException("HTTP-транспорт не задан.");
        }
        if (token == null || token.isBlank()) {
            throw new MusicException("Токен пустой.");
        }
        this.transport = transport;
        this.token = token.trim();
    }

    public AccountSnapshot accountStatus() {
        return YandexJson.account(get("/account/status"));
    }

    public Optional<String> firstLikedTrackId(long uid) {
        if (uid <= 0) {
            throw new MusicException("Идентификатор аккаунта не получен.");
        }
        return YandexJson.firstLikedTrackId(get("/users/" + uid + "/likes/tracks?if-modified-since-revision=0"));
    }

    public TrackMetadata track(String trackId) {
        String id = TrackIds.apiId(trackId);
        String body = "track-ids=" + URLEncoder.encode(id, StandardCharsets.UTF_8) + "&with-positions=true";
        return YandexJson.track(post("/tracks", body));
    }

    public List<DownloadOption> downloadInfo(String trackId) {
        return YandexJson.downloadOptions(get("/tracks/" + TrackIds.apiId(trackId) + "/download-info"));
    }

    public String rawMp3Url(String trackId) {
        String id = TrackIds.apiId(trackId);
        MusicException last = null;
        for (String quality : List.of("hq", "nq")) {
            try {
                return requestRawMp3(id, quality);
            } catch (MusicException exception) {
                if (stopTryingQualities(exception)) {
                    throw exception;
                }
                last = exception;
            }
        }
        throw last == null ? new MusicException("Сервис не дал незашифрованный mp3.") : last;
    }

    public byte[] fetchXml(String url) {
        requireYandexHttps(url);
        try {
            return transport.getBytes(url);
        } catch (MusicException exception) {
            throw redact(exception);
        }
    }

    public InputStream openAudio(String url) {
        requireYandexHttps(url);
        try {
            return transport.openStream(url);
        } catch (MusicException exception) {
            throw redact(exception);
        }
    }

    private String requestRawMp3(String trackId, String quality) {
        long timestamp = Instant.now().getEpochSecond();
        String sign = FileInfoSign.value(trackId, quality, "mp3", "raw", timestamp);
        String query = "ts=" + timestamp
                + "&trackId=" + URLEncoder.encode(trackId, StandardCharsets.UTF_8)
                + "&quality=" + quality
                + "&codecs=mp3"
                + "&transports=raw"
                + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8);
        JsonElement result = get("/get-file-info?" + query, Map.of(
                "X-Yandex-Music-Client", FileInfoSign.DESKTOP_CLIENT
        ));
        return YandexJson.rawMp3Url(result);
    }

    private static boolean stopTryingQualities(MusicException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        return message.contains("encraw") || message.contains("Авторизация");
    }

    private JsonElement get(String pathAndQuery) {
        return get(pathAndQuery, Map.of());
    }

    private JsonElement get(String pathAndQuery, Map<String, String> extraHeaders) {
        Map<String, String> headers = new HashMap<>(apiHeaders());
        headers.putAll(extraHeaders);
        HttpResponseData response = transport.exchange(new HttpRequestData(
                "GET",
                BASE_URL + pathAndQuery,
                headers,
                ""
        ));
        return finish(response);
    }

    private JsonElement post(String path, String form) {
        HttpResponseData response = transport.exchange(new HttpRequestData(
                "POST",
                BASE_URL + path,
                apiHeaders(),
                form
        ));
        return finish(response);
    }

    private Map<String, String> apiHeaders() {
        return Map.of(
                "Authorization", "OAuth " + token,
                "X-Yandex-Music-Client", CLIENT_HEADER,
                "Accept-Language", "ru",
                "User-Agent", "yandexmusic-minecraft/0.1.0",
                "Accept", "application/json"
        );
    }

    private JsonElement finish(HttpResponseData response) {
        if (response.status() == 401 || response.status() == 403) {
            throw new MusicException("Авторизация отклонена. Токен недействителен или истёк.");
        }
        if (response.status() < 200 || response.status() >= 300) {
            throw new MusicException("Запрос к API завершился с кодом " + response.status() + ". " + safeDetail(response.body()));
        }
        try {
            return ApiEnvelope.result(response.body());
        } catch (MusicException exception) {
            throw redact(exception);
        }
    }

    private String safeDetail(String body) {
        String detail;
        try {
            ApiEnvelope.result(body);
            detail = "Ответ не содержит result.";
        } catch (MusicException exception) {
            detail = exception.getMessage();
        }
        detail = Secrets.redact(detail, token);
        if (detail.length() > 180) {
            return detail.substring(0, 180);
        }
        return detail;
    }

    private MusicException redact(MusicException exception) {
        return new MusicException(Secrets.redact(exception.getMessage(), token), exception);
    }

    private static void requireYandexHttps(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException exception) {
            throw new MusicException("Ссылка на файл отклонена.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean yandexHost = host.equals("yandex.net") || host.endsWith(".yandex.net")
                || host.equals("yandex.ru") || host.endsWith(".yandex.ru")
                || host.equals("yandex.com") || host.endsWith(".yandex.com");
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !yandexHost) {
            throw new MusicException("Ссылка на файл ведёт не на домен Яндекса и была отклонена.");
        }
    }
}
