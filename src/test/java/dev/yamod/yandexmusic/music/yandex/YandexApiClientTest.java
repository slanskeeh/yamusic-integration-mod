package dev.yamod.yandexmusic.music.yandex;

import dev.yamod.yandexmusic.music.MusicException;
import dev.yamod.yandexmusic.music.model.StreamAccess;
import dev.yamod.yandexmusic.music.model.TrackMetadata;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YandexApiClientTest {
    private static final String TOKEN = "secret-token";

    @Test
    void directLinkMatchesKnownSignature() {
        byte[] xml = """
                <download-info>
                  <host>storage.mds.yandex.net</host>
                  <path>/foo/bar.mp3</path>
                  <ts>123</ts>
                  <s>abc</s>
                </download-info>
                """.getBytes(StandardCharsets.UTF_8);
        assertEquals(
                "https://storage.mds.yandex.net/get-mp3/62cdbbe3ae4435a0326b62e815acf860/123/foo/bar.mp3",
                DirectLinkBuilder.build(xml)
        );
    }

    @Test
    void accountTrackAndAuthHeader() {
        FakeTransport transport = new FakeTransport();
        transport.onExchange = request -> {
            assertTrue(request.headers().get("Authorization").equals("OAuth " + TOKEN));
            assertFalse(request.url().contains(TOKEN));
            if (request.url().endsWith("/account/status")) {
                return new HttpResponseData(200, """
                        {"result":{"account":{"uid":42,"login":"listener","displayName":"Слушатель"}}}
                        """);
            }
            if (request.url().contains("/likes/tracks")) {
                return new HttpResponseData(200, """
                        {"result":{"library":{"tracks":[{"id":"37699623:4532"}]}}}
                        """);
            }
            if (request.url().endsWith("/tracks") && "POST".equals(request.method())) {
                assertFalse(request.body().contains(TOKEN));
                return new HttpResponseData(200, """
                        {"result":[{"id":"37699623","title":"Песня","available":true,"durationMs":180000,
                        "artists":[{"name":"Исполнитель"}]}]}
                        """);
            }
            return new HttpResponseData(404, "{\"error\":{\"name\":\"not-found\"}}");
        };
        YandexMusicProvider provider = new YandexMusicProvider(new YandexApiClient(transport, TOKEN));
        assertEquals("Слушатель", provider.account().displayName());
        TrackMetadata track = provider.firstLikedTrack();
        assertEquals("37699623", track.id());
        assertEquals("Исполнитель", track.artists());
        assertTrue(track.describe().contains("Песня"));
    }

    @Test
    void guestStatusWithoutUidIsRejected() {
        FakeTransport transport = new FakeTransport();
        transport.onExchange = request -> new HttpResponseData(200, """
                {"result":{"account":{"region":225,"serviceAvailable":true}}}
                """);
        YandexApiClient client = new YandexApiClient(transport, TOKEN);
        MusicException error = assertThrows(MusicException.class, client::accountStatus);
        assertTrue(error.getMessage().contains("uid"));
        assertFalse(error.getMessage().contains(TOKEN));
    }

    @Test
    void unauthorizedBodyDoesNotLeakToken() {
        FakeTransport transport = new FakeTransport();
        transport.onExchange = request -> new HttpResponseData(401, "{\"error\":\"secret-token\"}");
        YandexApiClient client = new YandexApiClient(transport, TOKEN);
        MusicException error = assertThrows(MusicException.class, client::accountStatus);
        assertFalse(error.getMessage().contains(TOKEN));
    }

    @Test
    void errorBodyTokenIsRedacted() {
        FakeTransport transport = new FakeTransport();
        transport.onExchange = request -> new HttpResponseData(500, """
                {"error":{"name":"bad","message":"secret-token"}}
                """);
        YandexApiClient client = new YandexApiClient(transport, TOKEN);
        MusicException error = assertThrows(MusicException.class, client::accountStatus);
        assertFalse(error.getMessage().contains(TOKEN));
        assertTrue(error.getMessage().contains("<redacted>"));
    }

    @Test
    void encryptedOptionNeverOpensAudioOrKeepsKey() {
        FakeTransport transport = new FakeTransport();
        transport.onExchange = request -> {
            assertTrue(request.url().contains("codecs=mp3"));
            assertTrue(request.url().contains("transports=raw"));
            assertFalse(request.url().contains("encraw"));
            assertEquals(FileInfoSign.DESKTOP_CLIENT, request.headers().get("X-Yandex-Music-Client"));
            return new HttpResponseData(200, """
                    {"result":{"downloadInfo":{"codec":"flac-mp4","bitrate":1400,"transport":"encraw",
                    "url":"https://strm.yandex.net/file","key":"encraw-key-value"}}}
                    """);
        };
        YandexMusicProvider provider = new YandexMusicProvider(new YandexApiClient(transport, TOKEN));
        var decision = provider.availability("15");
        assertEquals(StreamAccess.ENCRYPTED_REFUSED, decision.access());
        assertFalse(decision.explanation().contains("encraw-key-value"));
        MusicException error = assertThrows(MusicException.class, () -> provider.openUnencryptedMp3("15"));
        assertFalse(error.getMessage().contains("encraw-key-value"));
        assertEquals(0, transport.byteReads.get());
        assertEquals(0, transport.streamOpens.get());
    }

    @Test
    void rawMp3UrlIsOpenedWithoutToken() {
        FakeTransport transport = new FakeTransport();
        transport.onExchange = request -> {
            assertTrue(request.url().startsWith("https://api.music.yandex.net/get-file-info?"));
            assertTrue(request.headers().get("Authorization").equals("OAuth " + TOKEN));
            assertFalse(request.url().contains(TOKEN));
            return new HttpResponseData(200, """
                    {"result":{"downloadInfo":{"codec":"mp3","transport":"raw","bitrate":192,
                    "url":"https://strm.yandex.net/track.mp3","key":"encraw-key-value"}}}
                    """);
        };
        YandexMusicProvider provider = new YandexMusicProvider(new YandexApiClient(transport, TOKEN));
        provider.openUnencryptedMp3("15");
        assertEquals(List.of("https://strm.yandex.net/track.mp3"), transport.opened);
        assertFalse(transport.opened.get(0).contains(TOKEN));
    }

    @Test
    void fileInfoSignMatchesKnownVector() {
        assertEquals(
                "HL9iIjT7DV1QVt6xqgdqnqR3rH7QmLuNFZ4yP0unYac",
                FileInfoSign.value("117708948", "hq", "mp3", "raw", 1_724_399_849L)
        );
    }

    @Test
    void foreignAudioHostIsRejected() {
        FakeTransport transport = new FakeTransport();
        YandexApiClient client = new YandexApiClient(transport, TOKEN);
        assertThrows(MusicException.class, () -> client.openAudio("https://example.invalid/track.mp3"));
        assertEquals(0, transport.streamOpens.get());
    }

    private static final class FakeTransport implements HttpTransport {
        private Exchange onExchange = request -> new HttpResponseData(200, "{}");
        private final AtomicInteger byteReads = new AtomicInteger();
        private final AtomicInteger streamOpens = new AtomicInteger();
        private final List<String> opened = new ArrayList<>();

        @Override
        public HttpResponseData exchange(HttpRequestData request) {
            return onExchange.exchange(request);
        }

        @Override
        public byte[] getBytes(String url) {
            byteReads.incrementAndGet();
            return new byte[0];
        }

        @Override
        public InputStream openStream(String url) {
            streamOpens.incrementAndGet();
            opened.add(url);
            return InputStream.nullInputStream();
        }
    }

    @FunctionalInterface
    private interface Exchange {
        HttpResponseData exchange(HttpRequestData request);
    }
}
