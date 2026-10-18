package dev.yamod.redio.music.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StreamChooserTest {
    @Test
    void prefersHighestUnencryptedMp3() {
        StreamDecision decision = StreamChooser.choose(List.of(
                new DownloadOption("mp3", 128, false, null, "https://storage.mds.yandex.net/a"),
                new DownloadOption("mp3", 320, false, "raw", "https://storage.mds.yandex.net/b"),
                new DownloadOption("flac-mp4", 1400, false, "encraw", "https://storage.mds.yandex.net/c")
        ));
        assertEquals(StreamAccess.UNENCRYPTED_MP3, decision.access());
        assertEquals(320, decision.option().bitrateKbps());
    }

    @Test
    void refusesEncryptedOnlyResponse() {
        StreamDecision decision = StreamChooser.choose(List.of(
                new DownloadOption("flac-mp4", 1400, false, "encraw", "https://storage.mds.yandex.net/c")
        ));
        assertEquals(StreamAccess.ENCRYPTED_REFUSED, decision.access());
        assertNull(decision.option());
        assertTrueNoKey(decision.explanation());
    }

    @Test
    void previewAloneIsUnavailable() {
        StreamDecision decision = StreamChooser.choose(List.of(
                new DownloadOption("mp3", 128, true, "raw", "https://storage.mds.yandex.net/preview")
        ));
        assertEquals(StreamAccess.UNAVAILABLE, decision.access());
    }

    private static void assertTrueNoKey(String text) {
        if (text.contains("key") || text.contains("encraw-key")) {
            throw new AssertionError(text);
        }
    }
}
