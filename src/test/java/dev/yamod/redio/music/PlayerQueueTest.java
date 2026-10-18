package dev.yamod.redio.music;

import dev.yamod.redio.music.model.TrackMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void previousLeavesTheCurrentTrack() {
        PlayerQueue queue = new PlayerQueue(List.of(track("1", "Первый"), track("2", "Второй")));
        queue.nextTrack();
        assertEquals("Первый", queue.previousTrack().orElseThrow().title());
        assertTrue(queue.previousTrack().isEmpty());
        assertEquals("Первый", queue.current().orElseThrow().title());
    }

    @Test
    void shuffleKeepsCurrentThenPlaysSomethingElse() {
        PlayerQueue queue = new PlayerQueue(List.of(
                track("1", "Первый"),
                track("2", "Второй"),
                track("3", "Третий"),
                track("4", "Четвёртый"),
                track("5", "Пятый")
        ), new Random(1));
        queue.setShuffle(true);
        assertEquals("1", queue.current().orElseThrow().id());
        assertNotEquals("1", queue.nextTrack().orElseThrow().id());
    }

    @Test
    void repeatTrackRestartsOnlyWhenTheSongEnds() {
        PlayerQueue queue = new PlayerQueue(List.of(track("1", "Первый"), track("2", "Второй")));
        queue.setRepeat(RepeatMode.TRACK);
        assertEquals("1", queue.onFinished().orElseThrow().id());
        assertEquals("1", queue.current().orElseThrow().id());
        assertEquals("2", queue.nextTrack().orElseThrow().id());
    }

    @Test
    void repeatQueueWrapsAndOffStops() {
        PlayerQueue queue = new PlayerQueue(List.of(track("1", "Первый"), track("2", "Второй")));
        queue.nextTrack();
        assertTrue(queue.nextTrack().isEmpty());
        assertEquals("2", queue.current().orElseThrow().id());
        queue.setRepeat(RepeatMode.QUEUE);
        assertEquals("1", queue.nextTrack().orElseThrow().id());
        assertEquals("2", queue.previousTrack().orElseThrow().id());
    }

    @Test
    void singleTrackPreviousDoesNotRestartIt() {
        PlayerQueue queue = new PlayerQueue(List.of(track("1", "Первый")));
        queue.setRepeat(RepeatMode.QUEUE);
        assertTrue(queue.previousTrack().isEmpty());
        assertEquals("1", queue.onFinished().orElseThrow().id());
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
