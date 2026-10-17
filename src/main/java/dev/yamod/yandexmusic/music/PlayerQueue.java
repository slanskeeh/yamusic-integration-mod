package dev.yamod.yandexmusic.music;

import dev.yamod.yandexmusic.music.model.TrackMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Очередь текущей сессии: порядок, перемешивание и повтор живут здесь, а не в сервисе.
 */
public final class PlayerQueue {
    private List<TrackMetadata> tracks;
    private final Random random;
    private RepeatMode repeat = RepeatMode.OFF;
    private boolean shuffled;
    private int[] order = new int[0];
    private int cursor;

    public PlayerQueue(List<TrackMetadata> tracks) {
        this(tracks, new Random());
    }

    public PlayerQueue(List<TrackMetadata> tracks, Random random) {
        this.tracks = tracks == null ? List.of() : new ArrayList<>(tracks);
        this.random = random == null ? new Random() : random;
        this.order = identity(this.tracks.size());
    }

    public static PlayerQueue empty() {
        return new PlayerQueue(List.of());
    }

    public synchronized int size() {
        return tracks.size();
    }

    public synchronized int index() {
        return tracks.isEmpty() ? 0 : order[cursor];
    }

    public synchronized RepeatMode repeat() {
        return repeat;
    }

    public synchronized void setRepeat(RepeatMode mode) {
        repeat = mode == null ? RepeatMode.OFF : mode;
    }

    public synchronized boolean shuffled() {
        return shuffled;
    }

    public synchronized void setShuffle(boolean enabled) {
        if (enabled == shuffled || tracks.isEmpty()) {
            shuffled = enabled;
            return;
        }
        int current = order[cursor];
        shuffled = enabled;
        if (enabled) {
            List<Integer> rest = new ArrayList<>();
            for (int i = 0; i < tracks.size(); i++) {
                if (i != current) {
                    rest.add(i);
                }
            }
            Collections.shuffle(rest, random);
            order = new int[tracks.size()];
            order[0] = current;
            for (int i = 0; i < rest.size(); i++) {
                order[i + 1] = rest.get(i);
            }
            cursor = 0;
            return;
        }
        order = identity(tracks.size());
        cursor = current;
    }

    public synchronized boolean select(int trackIndex) {
        if (trackIndex < 0 || trackIndex >= tracks.size()) {
            return false;
        }
        for (int i = 0; i < order.length; i++) {
            if (order[i] == trackIndex) {
                cursor = i;
                return true;
            }
        }
        return false;
    }

    public synchronized Optional<TrackMetadata> current() {
        if (tracks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tracks.get(order[cursor]));
    }

    public synchronized List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (TrackMetadata track : tracks) {
            ids.add(track.id());
        }
        return List.copyOf(ids);
    }

    public synchronized void append(List<TrackMetadata> more) {
        if (more == null || more.isEmpty()) {
            return;
        }
        int offset = tracks.size();
        List<TrackMetadata> combined = new ArrayList<>(tracks);
        combined.addAll(more);
        tracks = combined;
        int[] expanded = Arrays.copyOf(order, order.length + more.size());
        for (int i = 0; i < more.size(); i++) {
            expanded[order.length + i] = offset + i;
        }
        order = expanded;
    }

    /**
     * Шаг без повтора и без перехода через край. Нужен старым проверкам очереди.
     */
    public synchronized Optional<TrackMetadata> move(int delta) {
        if (tracks.isEmpty()) {
            return Optional.empty();
        }
        int next = cursor + delta;
        if (next < 0 || next >= order.length) {
            return Optional.empty();
        }
        cursor = next;
        return current();
    }

    public synchronized Optional<TrackMetadata> nextTrack() {
        return step(1, true);
    }

    public synchronized Optional<TrackMetadata> previousTrack() {
        return step(-1, false);
    }

    /**
     * Конец трека. Повтор одного трека возвращает его же. Кнопка «Следующий» так не делает.
     */
    public synchronized Optional<TrackMetadata> onFinished() {
        if (tracks.isEmpty()) {
            return Optional.empty();
        }
        if (repeat == RepeatMode.TRACK) {
            return current();
        }
        return step(1, true);
    }

    private Optional<TrackMetadata> step(int delta, boolean allowSingleTrackWrap) {
        if (tracks.isEmpty()) {
            return Optional.empty();
        }
        int next = cursor + delta;
        if (next >= 0 && next < order.length) {
            cursor = next;
            return current();
        }
        boolean wrap = repeat == RepeatMode.QUEUE && (order.length > 1 || allowSingleTrackWrap);
        if (!wrap) {
            return Optional.empty();
        }
        cursor = delta > 0 ? 0 : order.length - 1;
        return current();
    }

    private static int[] identity(int size) {
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = i;
        }
        return values;
    }
}
