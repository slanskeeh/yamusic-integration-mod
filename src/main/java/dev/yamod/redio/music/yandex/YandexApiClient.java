package dev.yamod.redio.music.yandex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.yamod.redio.music.MusicException;
import dev.yamod.redio.music.Secrets;
import dev.yamod.redio.music.TrackIds;
import dev.yamod.redio.music.model.AccountSnapshot;
import dev.yamod.redio.music.model.CatalogRef;
import dev.yamod.redio.music.model.DownloadOption;
import dev.yamod.redio.music.model.SearchPage;
import dev.yamod.redio.music.model.TrackMetadata;
import dev.yamod.redio.music.model.WavePage;

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
import java.util.stream.Collectors;

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
        return likedTrackIds(uid).stream().findFirst();
    }

    public List<String> likedTrackIds(long uid) {
        if (uid <= 0) {
            throw new MusicException("Идентификатор аккаунта не получен.");
        }
        return YandexJson.likedTrackIds(get("/users/" + uid + "/likes/tracks?if-modified-since-revision=0"));
    }

    public List<CatalogRef> likedAlbums(long uid) {
        requireUid(uid);
        return YandexJson.likedAlbums(get("/users/" + uid + "/likes/albums"));
    }

    public TrackMetadata track(String trackId) {
        List<TrackMetadata> tracks = tracks(List.of(trackId));
        if (tracks.isEmpty()) {
            throw new MusicException("API не вернуло метаданные трека.");
        }
        return tracks.get(0);
    }

    public List<TrackMetadata> tracks(List<String> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            return List.of();
        }
        if (trackIds.size() <= 50) {
            return tracksOnce(trackIds);
        }
        List<TrackMetadata> all = new java.util.ArrayList<>();
        for (int offset = 0; offset < trackIds.size(); offset += 50) {
            all.addAll(tracksOnce(trackIds.subList(offset, Math.min(trackIds.size(), offset + 50))));
        }
        return List.copyOf(all);
    }

    public List<TrackMetadata> playlistTracks(long uid, long kind) {
        JsonElement result = playlist(uid, kind);
        List<TrackMetadata> nested = YandexJson.playlistTracks(result);
        if (!nested.isEmpty()) {
            return nested;
        }
        return tracks(YandexJson.playlistTrackIds(result));
    }

    private List<TrackMetadata> tracksOnce(List<String> trackIds) {
        String joined = trackIds.stream()
                .map(TrackIds::apiId)
                .map(id -> URLEncoder.encode(id, StandardCharsets.UTF_8))
                .collect(Collectors.joining(","));
        String body = "track-ids=" + joined + "&with-positions=true";
        return YandexJson.tracks(post("/tracks", body));
    }

    public SearchPage search(String text) {
        String query = text == null ? "" : text.trim();
        if (query.isEmpty() || query.length() > 120) {
            throw new MusicException("Введите запрос короче 120 символов.");
        }
        String path = "/search?text=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&type=all&page=0&nocorrect=false&playlist-in-best=true";
        return YandexJson.search(get(path));
    }

    public List<CatalogRef> playlists(long uid) {
        requireUid(uid);
        return YandexJson.playlistList(get("/users/" + uid + "/playlists/list"));
    }

    public JsonElement playlist(long uid, long kind) {
        requireUid(uid);
        requireKind(kind);
        return get("/users/" + uid + "/playlists/" + kind);
    }

    public List<TrackMetadata> albumTracks(String albumId) {
        String id = digits(albumId, "Идентификатор альбома отклонён.");
        return YandexJson.albumTracks(get("/albums/" + id + "/with-tracks"));
    }

    public List<TrackMetadata> artistTracks(String artistId) {
        String id = digits(artistId, "Идентификатор исполнителя отклонён.");
        return YandexJson.artistTracks(get("/artists/" + id + "/tracks?page=0&page-size=40"));
    }

    public void likeTrack(long uid, String trackId, boolean remove) {
        requireUid(uid);
        String id = TrackIds.apiId(trackId);
        String action = remove ? "remove" : "add-multiple";
        String body = "track-ids=" + URLEncoder.encode(id, StandardCharsets.UTF_8);
        JsonElement result = post("/users/" + uid + "/likes/tracks/" + action, body);
        if (!YandexJson.likeConfirmed(result)) {
            throw new MusicException(remove ? "Сервис не подтвердил снятие лайка." : "Сервис не подтвердил лайк.");
        }
    }

    public WavePage myWave() {
        JsonObject body = new JsonObject();
        JsonArray seeds = new JsonArray();
        seeds.add("user:onyourwave");
        body.add("seeds", seeds);
        body.addProperty("includeTracksInResponse", true);
        return YandexJson.wave(postJson("/rotor/session/new", body.toString()));
    }

    public void waveFeedback(String sessionId, String batchId, String type, String trackId, double playedSeconds) {
        String session = requireSessionId(sessionId);
        if (!type.equals("radioStarted") && !type.equals("trackStarted")
                && !type.equals("trackFinished") && !type.equals("skip")) {
            throw new MusicException("Событие волны отклонено.");
        }
        JsonObject event = new JsonObject();
        event.addProperty("type", type);
        event.addProperty("timestamp", Instant.now().toString());
        if (trackId != null && !trackId.isBlank()) {
            event.addProperty("trackId", TrackIds.apiId(trackId));
        }
        if (type.equals("trackFinished") || type.equals("skip")) {
            event.addProperty("totalPlayedSeconds", Math.max(0, playedSeconds));
        }
        JsonObject body = new JsonObject();
        body.add("event", event);
        if (batchId != null && !batchId.isBlank()) {
            body.addProperty("batchId", batchId);
        }
        String path = "/rotor/session/" + URLEncoder.encode(session, StandardCharsets.UTF_8) + "/feedback";
        postJson(path, body.toString());
    }

    public WavePage moreWave(String sessionId, List<String> queue) {
        sessionId = requireSessionId(sessionId);
        JsonObject body = new JsonObject();
        JsonArray played = new JsonArray();
        if (queue != null) {
            for (String id : queue) {
                if (TrackIds.looksLikeTrackId(id)) {
                    played.add(TrackIds.apiId(id));
                }
            }
        }
        body.add("queue", played);
        String path = "/rotor/session/" + URLEncoder.encode(sessionId, StandardCharsets.UTF_8) + "/tracks";
        return YandexJson.wave(postJson(path, body.toString())).withSession(sessionId);
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

    private JsonElement postJson(String path, String json) {
        Map<String, String> headers = new HashMap<>(apiHeaders());
        headers.put("Content-Type", "application/json");
        HttpResponseData response = transport.exchange(new HttpRequestData(
                "POST",
                BASE_URL + path,
                headers,
                json
        ));
        return finish(response);
    }

    private static String requireSessionId(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9._:-]{1,80}") || sessionId.contains("..")) {
            throw new MusicException("Идентификатор радиосессии отклонён.");
        }
        return sessionId;
    }

    private static void requireUid(long uid) {
        if (uid <= 0) {
            throw new MusicException("Идентификатор аккаунта не получен.");
        }
    }

    private static void requireKind(long kind) {
        if (kind < 0) {
            throw new MusicException("Идентификатор плейлиста отклонён.");
        }
    }

    private static String digits(String raw, String message) {
        if (raw == null || !raw.matches("[0-9]{1,20}")) {
            throw new MusicException(message);
        }
        return raw;
    }

    private Map<String, String> apiHeaders() {
        return Map.of(
                "Authorization", "OAuth " + token,
                "X-Yandex-Music-Client", CLIENT_HEADER,
                "Accept-Language", "ru",
                "User-Agent", "redio-minecraft/0.1.0",
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
