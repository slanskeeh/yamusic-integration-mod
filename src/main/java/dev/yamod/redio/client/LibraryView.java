package dev.yamod.redio.client;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Список, который экран только рисует. Сетевые запросы сюда не кладутся.
 */
public final class LibraryView {
    public enum Tab {
        PLAYER,
        LIKES,
        SEARCH,
        PLAYLISTS
    }

    public enum Kind {
        TRACK,
        ALBUM,
        ARTIST,
        PLAYLIST
    }

    public record Row(String label, Kind kind, String id, String ownerId, int trackIndex) {
    }

    private static volatile Tab tab = Tab.PLAYER;
    private static volatile String query = "";
    private static volatile int scroll;
    private static volatile String heading = "";
    private static volatile List<Row> rows = List.of();
    private static final AtomicInteger version = new AtomicInteger();

    private LibraryView() {
    }

    public static Tab tab() {
        return tab;
    }

    public static void tab(Tab value) {
        tab = value == null ? Tab.PLAYER : value;
    }

    public static String query() {
        return query;
    }

    public static void query(String value) {
        query = value == null ? "" : value;
    }

    public static int scroll() {
        return scroll;
    }

    public static void scrollBy(int delta) {
        scroll = Math.max(0, scroll + delta);
    }

    public static void resetScroll() {
        scroll = 0;
    }

    public static String heading() {
        return heading;
    }

    public static List<Row> rows() {
        return rows;
    }

    public static int version() {
        return version.get();
    }

    public static void show(String title, List<Row> next) {
        heading = title == null ? "" : title;
        rows = next == null ? List.of() : List.copyOf(next);
        version.incrementAndGet();
    }
}
