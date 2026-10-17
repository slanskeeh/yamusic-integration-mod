package dev.yamod.yandexmusic.music.yandex;

import dev.yamod.yandexmusic.music.MusicException;
import dev.yamod.yandexmusic.music.model.AccountSnapshot;
import dev.yamod.yandexmusic.music.model.CatalogRef;
import dev.yamod.yandexmusic.music.model.SearchPage;
import dev.yamod.yandexmusic.music.model.StreamAccess;
import dev.yamod.yandexmusic.music.model.StreamDecision;
import dev.yamod.yandexmusic.music.model.TrackMetadata;
import dev.yamod.yandexmusic.music.model.WavePage;

import java.io.InputStream;
import java.util.List;

/**
 * Провайдер Яндекс Музыки: коллекция, поиск, лайки, волна и незашифрованный mp3.
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

    public static final int COLLECTION_LIMIT = 100;

    public TrackMetadata firstLikedTrack() {
        List<TrackMetadata> tracks = likedTracks();
        return tracks.get(0);
    }

    public List<TrackMetadata> likedTracks() {
        AccountSnapshot account = account();
        List<String> ids = client.likedTrackIds(account.uid());
        if (ids.isEmpty()) {
            throw new MusicException("В любимых треках нет обычных треков, которые можно поставить в очередь.");
        }
        return loadTracks(ids);
    }

    public SearchPage search(String text) {
        return client.search(text);
    }

    public List<CatalogRef> playlists() {
        return client.playlists(account().uid());
    }

    public List<TrackMetadata> playlistTracks(long uid, long kind) {
        return cap(client.playlistTracks(uid, kind), "В плейлисте нет треков, которые можно поставить в очередь.");
    }

    public List<TrackMetadata> albumTracks(String albumId) {
        return cap(client.albumTracks(albumId), "В альбоме нет треков, которые можно поставить в очередь.");
    }

    public List<TrackMetadata> artistTracks(String artistId) {
        return cap(client.artistTracks(artistId), "У исполнителя не нашлось треков для очереди.");
    }

    public void setTrackLiked(String trackId, boolean liked) {
        client.likeTrack(account().uid(), trackId, !liked);
    }

    public WavePage myWave() {
        WavePage page = client.myWave();
        if (page.sessionId().isEmpty() || page.tracks().isEmpty()) {
            throw new MusicException("Моя волна не вернула треки. Сервис мог не принять этот запрос.");
        }
        return page;
    }

    public WavePage moreWave(String sessionId, List<String> queue) {
        return client.moreWave(sessionId, queue);
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

    private List<TrackMetadata> loadTracks(List<String> ids) {
        if (ids.size() > COLLECTION_LIMIT) {
            ids = ids.subList(0, COLLECTION_LIMIT);
        }
        List<TrackMetadata> tracks = client.tracks(ids);
        if (tracks.isEmpty()) {
            throw new MusicException("Сервис не вернул метаданные любимых треков.");
        }
        return tracks;
    }

    private static List<TrackMetadata> cap(List<TrackMetadata> tracks, String emptyMessage) {
        if (tracks == null || tracks.isEmpty()) {
            throw new MusicException(emptyMessage);
        }
        if (tracks.size() > COLLECTION_LIMIT) {
            return List.copyOf(tracks.subList(0, COLLECTION_LIMIT));
        }
        return tracks;
    }
}
