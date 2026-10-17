package dev.yamod.yandexmusic.music;

import dev.yamod.yandexmusic.music.model.TrackMetadata;

import java.util.List;
import java.util.Optional;

/**
 * Короткая очередь любимых треков. Это не каталог и не перемешивание.
 */
public final class PlayerQueue {
    private final List<TrackMetadata> tracks;
    private int index;

    public PlayerQueue(List<TrackMetadata> tracks) {
        this.tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }

    public static PlayerQueue empty() {
        return new PlayerQueue(List.of());
    }

    public synchronized int size() {
        return tracks.size();
    }

    public synchronized int index() {
        return tracks.isEmpty() ? 0 : index;
    }

    public synchronized Optional<TrackMetadata> current() {
        if (tracks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tracks.get(index));
    }

    public synchronized Optional<TrackMetadata> move(int delta) {
        if (tracks.isEmpty()) {
            return Optional.empty();
        }
        int next = index + delta;
        if (next < 0 || next >= tracks.size()) {
            return Optional.empty();
        }
        index = next;
        return Optional.of(tracks.get(index));
    }
}
