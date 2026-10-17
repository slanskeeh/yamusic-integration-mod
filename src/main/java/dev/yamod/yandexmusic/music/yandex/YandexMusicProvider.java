package dev.yamod.yandexmusic.music.yandex;

import dev.yamod.yandexmusic.music.MusicException;
import dev.yamod.yandexmusic.music.model.AccountSnapshot;
import dev.yamod.yandexmusic.music.model.StreamAccess;
import dev.yamod.yandexmusic.music.model.StreamDecision;
import dev.yamod.yandexmusic.music.model.TrackMetadata;

import java.io.InputStream;
import java.util.List;

/**
 * Первая реализация провайдера. Умеет только то, что нужно для проверки фазы 1.
 */
public final class YandexMusicProvider {
    private final YandexApiClient client;

    public YandexMusicProvider(YandexApiClient client) {
        this.client = client;
    }

    public static YandexMusicProvider create(String token) {
        return new YandexMusicProvider(new YandexApiClient(new JavaHttpTransport(), token));
    }

    public AccountSnapshot account() {
        return client.accountStatus();
    }

    public static final int LIKED_TRACK_LIMIT = 30;

    public TrackMetadata firstLikedTrack() {
        List<TrackMetadata> tracks = likedTracks();
        return tracks.get(0);
    }

    public List<TrackMetadata> likedTracks() {
        AccountSnapshot account = account();
        List<String> ids = client.likedTrackIds(account.uid());
        if (ids.isEmpty()) {
            throw new MusicException(
                    "В любимых треках пусто. Для плеера нужен хотя бы один любимый трек; каталог целиком ещё не делается."
            );
        }
        if (ids.size() > LIKED_TRACK_LIMIT) {
            ids = ids.subList(0, LIKED_TRACK_LIMIT);
        }
        List<TrackMetadata> tracks = client.tracks(ids);
        if (tracks.isEmpty()) {
            throw new MusicException("Сервис не вернул метаданные любимых треков.");
        }
        return tracks;
    }

    public StreamDecision availability(String trackId) {
        try {
            client.rawMp3Url(trackId);
            return new StreamDecision(
                    StreamAccess.UNENCRYPTED_MP3,
                    null,
                    "Найден незашифрованный mp3. Ссылка запрашивается заново в момент воспроизведения."
            );
        } catch (MusicException exception) {
            String message = exception.getMessage() == null ? "Незашифрованный mp3 недоступен." : exception.getMessage();
            StreamAccess access = message.contains("encraw") ? StreamAccess.ENCRYPTED_REFUSED : StreamAccess.UNAVAILABLE;
            return new StreamDecision(access, null, message);
        }
    }

    public InputStream openUnencryptedMp3(String trackId) {
        return client.openAudio(client.rawMp3Url(trackId));
    }
}
