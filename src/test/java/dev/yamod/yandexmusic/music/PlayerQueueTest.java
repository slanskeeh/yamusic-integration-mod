package dev.yamod.yandexmusic.music;

import dev.yamod.yandexmusic.music.model.TrackMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerQueueTest {
    @Test
    void nextAndPreviousStayInsideLoadedTracks() {
        PlayerQueue queue = new PlayerQueue(List.of(track("1", "Первый"), track("2", "Второй")));
        assertEquals("Первый", queue.current().orElseThrow().title());
        assertEquals("Второй", queue.move(1).orElseThrow().title());
        assertTrue(queue.move(1).isEmpty());
        assertEquals("Второй", queue.current().orElseThrow().title());
        assertEquals("Первый", queue.move(-1).orElseThrow().title());
        assertTrue(queue.move(-1).isEmpty());
    }

    @Test
    void emptyQueueDoesNotInventATrack() {
        assertEquals(Optional.empty(), PlayerQueue.empty().current());
        assertEquals(Optional.empty(), PlayerQueue.empty().move(1));
    }

    @Test
    void clockShowsMinutes() {
        assertEquals("0:00", PlaybackTime.clock(0));
        assertEquals("3:05", PlaybackTime.clock(185_000));
        assertEquals("1:00 / 3:00", PlaybackTime.line(60_000, 180_000));
    }

    private static TrackMetadata track(String id, String title) {
        return new TrackMetadata(id, title, "Исполнитель", true, 180_000);
    }
}
