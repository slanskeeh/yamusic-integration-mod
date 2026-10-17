package dev.yamod.yandexmusic.music.yandex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.yamod.yandexmusic.music.MusicException;
import dev.yamod.yandexmusic.music.TrackIds;
import dev.yamod.yandexmusic.music.model.AccountSnapshot;
import dev.yamod.yandexmusic.music.model.DownloadOption;
import dev.yamod.yandexmusic.music.model.TrackMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Разбор только тех полей, которые нужны прототипу. Неизвестные поля, включая ключ encraw, игнорируются.
 */
public final class YandexJson {
    private YandexJson() {
    }

    public static AccountSnapshot account(JsonElement result) {
        JsonObject account = object(result, "account");
        if (account == null || !account.has("uid") || !account.get("uid").isJsonPrimitive()) {
            throw new MusicException("Сервис не подтвердил аккаунт: в ответе нет uid. Токен не принят или запрос ушёл как гостевой.");
        }
        long uid = account.get("uid").getAsLong();
        String login = string(account, "login");
        String displayName = firstText(account, "displayName", "fullName", "login");
        return new AccountSnapshot(uid, login, displayName);
    }

    public static Optional<String> firstLikedTrackId(JsonElement result) {
        return likedTrackIds(result).stream().findFirst();
    }

    public static List<String> likedTrackIds(JsonElement result) {
        if (result == null || !result.isJsonObject()) {
            return List.of();
        }
        JsonObject library = object(result.getAsJsonObject(), "library");
        if (library == null || !library.has("tracks") || !library.get("tracks").isJsonArray()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (JsonElement element : library.getAsJsonArray("tracks")) {
            if (!element.isJsonObject()) {
                continue;
            }
            String raw = string(element.getAsJsonObject(), "id");
            if (TrackIds.looksLikeTrackId(raw)) {
                ids.add(TrackIds.apiId(raw));
            }
        }
        return List.copyOf(ids);
    }

    public static TrackMetadata track(JsonElement result) {
        JsonObject track = firstObject(result);
        if (track == null) {
            throw new MusicException("API не вернуло метаданные трека.");
        }
        return fromTrack(track);
    }

    public static List<TrackMetadata> tracks(JsonElement result) {
        if (result == null || result.isJsonNull()) {
            return List.of();
        }
        if (result.isJsonObject()) {
            return List.of(fromTrack(result.getAsJsonObject()));
        }
        if (!result.isJsonArray()) {
            throw new MusicException("Ответ со списком треков имеет неожиданный вид.");
        }
        List<TrackMetadata> tracks = new ArrayList<>();
        for (JsonElement element : result.getAsJsonArray()) {
            JsonObject track = firstObject(element);
            if (track != null && TrackIds.looksLikeTrackId(string(track, "id"))) {
                tracks.add(fromTrack(track));
            }
        }
        return List.copyOf(tracks);
    }

    private static TrackMetadata fromTrack(JsonObject track) {
        String id = TrackIds.apiId(string(track, "id"));
        String title = string(track, "title");
        Boolean available = track.has("available") && track.get("available").isJsonPrimitive()
                ? track.get("available").getAsBoolean()
                : null;
        long duration = track.has("durationMs") && track.get("durationMs").isJsonPrimitive()
                ? track.get("durationMs").getAsLong()
                : 0L;
        return new TrackMetadata(id, title, artistNames(track), available, duration);
    }

    public static List<DownloadOption> downloadOptions(JsonElement result) {
        if (result == null || result.isJsonNull()) {
            return List.of();
        }
        if (!result.isJsonArray()) {
            throw new MusicException("Ответ download-info имеет неожиданный вид.");
        }
        List<DownloadOption> options = new ArrayList<>();
        for (JsonElement element : result.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            int bitrate = item.has("bitrateInKbps") && item.get("bitrateInKbps").isJsonPrimitive()
                    ? item.get("bitrateInKbps").getAsInt()
                    : 0;
            boolean preview = item.has("preview") && item.get("preview").isJsonPrimitive() && item.get("preview").getAsBoolean();
            options.add(new DownloadOption(
                    string(item, "codec"),
                    bitrate,
                    preview,
                    nullable(item, "transport"),
                    string(item, "downloadInfoUrl")
            ));
        }
        return List.copyOf(options);
    }

    public static String rawMp3Url(JsonElement result) {
        JsonObject info = object(result, "downloadInfo");
        if (info == null) {
            info = object(result, "download_info");
        }
        if (info == null && result != null && result.isJsonObject() && result.getAsJsonObject().has("codec")) {
            info = result.getAsJsonObject();
        }
        if (info == null) {
            throw new MusicException("Сервис не вернул данные файла.");
        }
        String transport = string(info, "transport");
        if ("encraw".equalsIgnoreCase(transport)) {
            throw new MusicException("Сервис отдал зашифрованный поток encraw. Расшифровка не выполняется.");
        }
        String codec = string(info, "codec");
        if (!"mp3".equalsIgnoreCase(codec)) {
            String shown = codec.isEmpty() ? "неизвестный формат" : codec;
            throw new MusicException("Сервис отдал " + shown + ", а не незашифрованный mp3.");
        }
        if (!transport.isEmpty() && !"raw".equalsIgnoreCase(transport)) {
            throw new MusicException("Сервис отдал доставку " + transport + ". Нужен незашифрованный mp3.");
        }
        String url = firstHttpsUrl(info);
        if (url.isEmpty()) {
            throw new MusicException("Сервис не дал ссылку на mp3.");
        }
        return url;
    }

    private static String artistNames(JsonObject track) {
        if (!track.has("artists") || !track.get("artists").isJsonArray()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (JsonElement artist : track.getAsJsonArray("artists")) {
            if (artist.isJsonObject()) {
                String name = string(artist.getAsJsonObject(), "name");
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return String.join(", ", names);
    }

    private static JsonObject firstObject(JsonElement result) {
        if (result == null || result.isJsonNull()) {
            return null;
        }
        if (result.isJsonObject()) {
            return result.getAsJsonObject();
        }
        if (result.isJsonArray() && !result.getAsJsonArray().isEmpty() && result.getAsJsonArray().get(0).isJsonObject()) {
            return result.getAsJsonArray().get(0).getAsJsonObject();
        }
        return null;
    }

    private static JsonObject object(JsonElement result, String field) {
        if (result == null || !result.isJsonObject()) {
            return null;
        }
        return object(result.getAsJsonObject(), field);
    }

    private static JsonObject object(JsonObject parent, String field) {
        if (!parent.has(field) || !parent.get(field).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(field);
    }

    private static String string(JsonObject object, String field) {
        String value = nullable(object, field);
        return value == null ? "" : value;
    }

    private static String nullable(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull() || !object.get(field).isJsonPrimitive()) {
            return null;
        }
        return object.get(field).getAsString();
    }

    private static String firstHttpsUrl(JsonObject info) {
        String direct = string(info, "url");
        if (direct.startsWith("https://")) {
            return direct;
        }
        if (info.has("urls") && info.get("urls").isJsonArray()) {
            for (JsonElement element : info.getAsJsonArray("urls")) {
                if (element.isJsonPrimitive()) {
                    String url = element.getAsString();
                    if (url.startsWith("https://")) {
                        return url;
                    }
                }
            }
        }
        return "";
    }

    private static String firstText(JsonObject object, String... fields) {
        for (String field : fields) {
            String value = string(object, field);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }
}
