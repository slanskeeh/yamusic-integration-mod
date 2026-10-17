package dev.yamod.yandexmusic.music.yandex;

import com.google.gson.JsonParser;
import dev.yamod.yandexmusic.music.CoverUrls;
import dev.yamod.yandexmusic.music.MusicException;
import dev.yamod.yandexmusic.music.model.SearchPage;
import dev.yamod.yandexmusic.music.model.WavePage;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YandexCollectionTest {
    private static final String TOKEN = "test-token";

    @Test
    void searchKeepsTracksAlbumsArtistsAndPlaylists() {
        SearchPage page = YandexJson.search(JsonParser.parseString("""
                {"tracks":{"results":[{"id":"10","title":"Песня","artists":[{"name":"А"}],"coverUri":"avatars.yandex.net/get-music/%%"}]},
                 "albums":{"results":[{"id":20,"title":"Альбом","artists":[{"name":"Б"}]}]},
                 "artists":{"results":[{"id":30,"name":"Исполнитель"}]},
                 "playlists":{"results":[{"uid":7,"kind":3,"title":"Список"}]}}
                """));
        assertEquals("10", page.tracks().get(0).id());
        assertEquals("avatars.yandex.net/get-music/%%", page.tracks().get(0).coverUri());
        assertEquals("20", page.albums().get(0).id());
        assertEquals("30", page.artists().get(0).id());
        assertEquals("3", page.playlists().get(0).id());
        assertEquals("7", page.playlists().get(0).ownerId());
    }

    @Test
    void albumAndPlaylistReadNestedTracks() {
        var album = YandexJson.albumTracks(JsonParser.parseString("""
                {"volumes":[[{"id":"5","title":"Один","artists":[{"name":"А"}]}]]}
                """));
        assertEquals("5", album.get(0).id());
        var playlist = YandexJson.playlistTracks(JsonParser.parseString("""
                {"tracks":[{"id":1,"track":{"id":"6","title":"Два","artists":[{"name":"Б"}]}}]}
                """));
        assertEquals("6", playlist.get(0).id());
        assertEquals(List.of("8"), YandexJson.playlistTrackIds(JsonParser.parseString("""
                {"tracks":[{"id":"8"}]}
                """)));
    }

    @Test
    void waveReadsSessionAndSkipsForeignIds() {
        WavePage page = YandexJson.wave(JsonParser.parseString("""
                {"radioSessionId":"session-1","sequence":[
                  {"track":{"id":"not-a-track","title":"Подкаст"}},
                  {"track":{"id":"15","title":"Песня","artists":[{"name":"А"}]}}
                ]}
                """));
        assertEquals("session-1", page.sessionId());
        assertEquals(1, page.tracks().size());
        assertEquals("15", page.tracks().get(0).id());
    }

    @Test
    void likeAndWaveUseTheDocumentedPaths() {
        AtomicReference<HttpRequestData> seen = new AtomicReference<>();
        YandexApiClient client = new YandexApiClient(new FakeTransport(seen), TOKEN);
        client.likeTrack(7, "37699623:4532", false);
        assertTrue(seen.get().url().endsWith("/users/7/likes/tracks/add-multiple"));
        assertTrue(seen.get().body().contains("track-ids=37699623"));
        assertFalse(seen.get().headers().containsKey("Content-Type"));

        client.myWave();
        assertTrue(seen.get().url().endsWith("/rotor/session/new"));
        assertEquals("application/json", seen.get().headers().get("Content-Type"));
        assertTrue(seen.get().body().contains("user:onyourwave"));
        assertFalse(seen.get().body().contains(TOKEN));
    }

    @Test
    void likeWithoutRevisionIsNotSuccess() {
        YandexApiClient client = new YandexApiClient(new FakeTransport(null), TOKEN);
        assertThrows(MusicException.class, () -> client.likeTrack(7, "10", false));
    }

    @Test
    void coverUrlStaysOnYandexHttps() {
        assertEquals(
                "https://avatars.yandex.net/get-music/200x200",
                CoverUrls.fromCoverUri("avatars.yandex.net/get-music/%%").orElseThrow()
        );
        assertTrue(CoverUrls.fromCoverUri("https://example.invalid/cover.jpg").isEmpty());
        assertTrue(CoverUrls.fromCoverUri("http://avatars.yandex.net/get-music/%%").isEmpty());
    }

    private static final class FakeTransport implements HttpTransport {
        private final AtomicReference<HttpRequestData> seen;
        private final AtomicInteger calls = new AtomicInteger();

        private FakeTransport(AtomicReference<HttpRequestData> seen) {
            this.seen = seen;
        }

        @Override
        public HttpResponseData exchange(HttpRequestData request) {
            if (seen != null) {
                seen.set(request);
            }
            calls.incrementAndGet();
            if (request.url().contains("/likes/")) {
                if (calls.get() == 1 && seen == null) {
                    return new HttpResponseData(200, "{\"result\":\"ok\"}");
                }
                return new HttpResponseData(200, "{\"result\":{\"revision\":4}}");
            }
            return new HttpResponseData(200, """
                    {"result":{"radioSessionId":"session-1","sequence":[{"track":{"id":"15","title":"Песня","artists":[{"name":"А"}]}}]}}
                    """);
        }

        @Override
        public byte[] getBytes(String url) {
            return new byte[0];
        }

        @Override
        public InputStream openStream(String url) {
            return InputStream.nullInputStream();
        }
    }
}
