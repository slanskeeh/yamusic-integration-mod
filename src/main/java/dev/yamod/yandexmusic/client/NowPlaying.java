package dev.yamod.yandexmusic.client;

import dev.yamod.yandexmusic.audio.PlaybackState;
import dev.yamod.yandexmusic.music.MusicException;
import dev.yamod.yandexmusic.music.PlaybackTime;
import dev.yamod.yandexmusic.music.PlayerQueue;
import dev.yamod.yandexmusic.music.RepeatMode;
import dev.yamod.yandexmusic.music.model.CatalogRef;
import dev.yamod.yandexmusic.music.model.SearchPage;
import dev.yamod.yandexmusic.music.model.TrackMetadata;
import dev.yamod.yandexmusic.music.model.WavePage;
import dev.yamod.yandexmusic.music.yandex.YandexMusicProvider;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Состояние личного плеера, которое живёт дольше экрана.
 */
public final class NowPlaying {
    private static volatile PlayerQueue queue = PlayerQueue.empty();
    private static volatile List<TrackMetadata> listed = List.of();
    private static volatile List<TrackMetadata> likes = List.of();
    private static volatile List<TrackMetadata> waveTracks = List.of();
    private static final Set<String> likedIds = Collections.synchronizedSet(new HashSet<>());
    private static volatile String waveSession = "";
    private static volatile boolean wave;
    private static volatile String status = "Нажмите «Играть» или откройте вкладку коллекции.";
    private static final AtomicBoolean busy = new AtomicBoolean();
    private static volatile boolean endHandled;
    private static volatile boolean userStop;

    private NowPlaying() {
    }

    public static void tick() {
        if (userStop || endHandled || busy.get() || playback() == PlaybackState.PAUSED) {
            return;
        }
        String error = PrototypeAudio.controller().errorMessage();
        if (error != null && !error.isBlank()) {
            return;
        }
        if (PrototypeAudio.controller().naturallyEnded()) {
            endHandled = true;
            onEnded();
        }
    }

    public static List<TrackMetadata> likes() {
        return likes;
    }

    public static List<TrackMetadata> listedTracks() {
        return listed;
    }

    public static List<TrackMetadata> waveTracks() {
        return waveTracks;
    }

    public static boolean currentLiked() {
        TrackMetadata track = track();
        return track != null && likedIds.contains(track.id());
    }

    public static boolean shuffled() {
        return queue.shuffled();
    }

    public static RepeatMode repeat() {
        return queue.repeat();
    }

    public static long positionMillis() {
        return PrototypeAudio.controller().positionMillis();
    }

    public static long durationMillis() {
        TrackMetadata track = track();
        return track == null ? 0 : track.durationMs();
    }

    public static void setVolume(float level) {
        PersonalVolume.set(level);
        PrototypeAudio.controller().setVolume(PersonalVolume.get());
    }

    public static void playAt(List<TrackMetadata> tracks, int index, boolean keepWave) {
        List<TrackMetadata> copy = tracks == null ? List.of() : List.copyOf(tracks);
        run("Трек", () -> playTracks(copy, index, keepWave && wave, keepWave ? waveSession : ""));
    }

    public static TrackMetadata track() {
        return queue.current().orElse(null);
    }

    public static String place() {
        int size = queue.size();
        if (size == 0) {
            return "";
        }
        return (queue.index() + 1) + " из " + size;
    }

    public static String timeLine() {
        TrackMetadata track = track();
        long duration = track == null ? 0 : track.durationMs();
        return PlaybackTime.line(PrototypeAudio.controller().positionMillis(), duration);
    }

    public static double fraction() {
        TrackMetadata track = track();
        if (track == null || track.durationMs() <= 0) {
            return 0;
        }
        return Math.min(1.0, PrototypeAudio.controller().positionMillis() / (double) track.durationMs());
    }

    public static String status() {
        String error = PrototypeAudio.controller().errorMessage();
        if (error != null && !error.isBlank()) {
            return error;
        }
        return status;
    }

    public static PlaybackState playback() {
        return PrototypeAudio.controller().state();
    }

    public static String repeatLabel() {
        return "Повтор: " + queue.repeat().label();
    }

    public static String shuffleLabel() {
        return queue.shuffled() ? "Перемешать: вкл" : "Перемешать: выкл";
    }

    public static String likeLabel() {
        TrackMetadata track = track();
        if (track != null && likedIds.contains(track.id())) {
            return "Убрать лайк";
        }
        return "Лайк";
    }

    public static void playOrResume() {
        PlaybackState state = playback();
        if (state == PlaybackState.PLAYING) {
            PrototypeAudio.controller().togglePause();
            status = "Пауза.";
            return;
        }
        if (state == PlaybackState.PAUSED) {
            PrototypeAudio.controller().togglePause();
            status = "Продолжаю.";
            return;
        }
        run("Открываю трек", () -> {
            ensureQueue();
            return start(queue.current().orElseThrow(() -> new MusicException("В любимых треках пусто.")));
        });
    }

    public static void next() {
        run("Следующий трек", () -> {
            if (queue.size() == 0) {
                throw new MusicException("Сначала выберите трек.");
            }
            Optional<TrackMetadata> track = queue.nextTrack();
            if (track.isEmpty() && wave) {
                track = extendWave();
            }
            return start(track.orElseThrow(() -> new MusicException("Это последний трек в очереди.")));
        });
    }

    public static void previous() {
        run("Предыдущий трек", () -> {
            if (queue.size() == 0) {
                throw new MusicException("Сначала выберите трек.");
            }
            TrackMetadata track = queue.previousTrack().orElseThrow(() -> new MusicException(
                    "Это первый трек в очереди."
            ));
            return start(track);
        });
    }

    public static void cycleRepeat() {
        queue.setRepeat(queue.repeat().next());
        status = "Повтор: " + queue.repeat().label() + ".";
    }

    public static void toggleShuffle() {
        queue.setShuffle(!queue.shuffled());
        status = queue.shuffled()
                ? "Перемешивание включено. Текущий трек остаётся, дальше порядок другой."
                : "Перемешивание выключено.";
    }

    public static void seekFraction(double fraction) {
        TrackMetadata track = track();
        if (track == null || track.durationMs() <= 0) {
            status = "Длительность неизвестна, перемотка недоступна.";
            return;
        }
        PlaybackState state = playback();
        if (state != PlaybackState.PLAYING && state != PlaybackState.PAUSED) {
            status = "Перемотка доступна, пока трек играет или стоит на паузе.";
            return;
        }
        double clamped = Math.max(0, Math.min(1, fraction));
        long target = (long) (clamped * track.durationMs());
        PrototypeAudio.controller().seek(target);
        status = "Перемотка: " + PlaybackTime.clock(target) + ".";
    }

    public static void toggleLike() {
        TrackMetadata track = track();
        if (track == null) {
            status = "Нет трека, который можно отметить.";
            return;
        }
        boolean liked = likedIds.contains(track.id());
        run(liked ? "Снимаю лайк" : "Ставлю лайк", () -> {
            provider().setTrackLiked(track.id(), !liked);
            if (liked) {
                likedIds.remove(track.id());
                return "Лайк снят.";
            }
            likedIds.add(track.id());
            return "Лайк поставлен.";
        });
    }

    public static void playWave() {
        run("Моя волна", () -> {
            WavePage page = provider().myWave();
            listed = page.tracks();
            waveTracks = listed;
            LibraryView.show("Моя волна", trackRows(page.tracks()));
            return playTracks(page.tracks(), 0, true, page.sessionId())
                    + " Повтор очереди зациклит уже загруженную партию.";
        });
    }

    public static void showLikes() {
        run("Любимые", () -> {
            List<TrackMetadata> tracks = provider().likedTracks();
            likes = List.copyOf(tracks);
            listed = likes;
            likedIds.clear();
            for (TrackMetadata track : tracks) {
                likedIds.add(track.id());
            }
            LibraryView.show("Любимые", trackRows(tracks));
            String tail = tracks.size() >= YandexMusicProvider.COLLECTION_LIMIT
                    ? " Показаны первые " + YandexMusicProvider.COLLECTION_LIMIT + "."
                    : "";
            return "Любимых треков в списке: " + tracks.size() + "." + tail;
        });
    }

    public static void showPlaylists() {
        run("Плейлисты", () -> {
            List<CatalogRef> playlists = provider().playlists();
            if (playlists.isEmpty()) {
                LibraryView.show("Плейлисты", List.of());
                return "Плейлистов не нашлось.";
            }
            List<LibraryView.Row> rows = new ArrayList<>();
            for (CatalogRef playlist : playlists) {
                rows.add(new LibraryView.Row(shorten(playlist.title()), LibraryView.Kind.PLAYLIST, playlist.id(), playlist.ownerId(), -1));
            }
            LibraryView.show("Плейлисты", rows);
            return "Плейлистов: " + playlists.size() + ".";
        });
    }

    public static void search() {
        String text = LibraryView.query();
        run("Поиск", () -> {
            SearchPage page = provider().search(text);
            if (page.isEmpty()) {
                listed = List.of();
                LibraryView.show("Поиск", List.of());
                return "Ничего не найдено среди треков, альбомов, исполнителей и плейлистов.";
            }
            listed = page.tracks();
            List<LibraryView.Row> rows = new ArrayList<>(trackRows(page.tracks()));
            for (CatalogRef album : page.albums()) {
                rows.add(refRow("Альбом: ", album, LibraryView.Kind.ALBUM));
            }
            for (CatalogRef artist : page.artists()) {
                rows.add(refRow("Исполнитель: ", artist, LibraryView.Kind.ARTIST));
            }
            for (CatalogRef playlist : page.playlists()) {
                rows.add(refRow("Плейлист: ", playlist, LibraryView.Kind.PLAYLIST));
            }
            LibraryView.show("Поиск", rows);
            return "Найдено: треков " + page.tracks().size()
                    + ", альбомов " + page.albums().size()
                    + ", исполнителей " + page.artists().size()
                    + ", плейлистов " + page.playlists().size() + ".";
        });
    }

    public static void openRow(int index) {
        List<LibraryView.Row> rows = LibraryView.rows();
        if (index < 0 || index >= rows.size()) {
            return;
        }
        LibraryView.Row row = rows.get(index);
        switch (row.kind()) {
            case TRACK -> run("Трек", () -> playTracks(listed, row.trackIndex(), false, ""));
            case ALBUM -> run("Альбом", () -> openTracks(provider().albumTracks(row.id()), row.label()));
            case ARTIST -> run("Исполнитель", () -> openTracks(provider().artistTracks(row.id()), row.label()));
            case PLAYLIST -> run("Плейлист", () -> openTracks(
                    provider().playlistTracks(Long.parseLong(row.ownerId()), Long.parseLong(row.id())),
                    row.label()
            ));
        }
    }

    public static void changeVolume(float delta) {
        float level = PersonalVolume.change(delta);
        PrototypeAudio.controller().setVolume(level);
        status = "Громкость плеера: " + PersonalVolume.label() + ". Общая громкость Minecraft не менялась.";
    }

    public static void stop() {
        userStop = true;
        endHandled = true;
        wave = false;
        waveSession = "";
        PrototypeAudio.release();
        status = "Воспроизведение остановлено.";
    }

    private static void onEnded() {
        run("Дальше", () -> {
            if (queue.size() == 0) {
                return "Очередь пуста.";
            }
            Optional<TrackMetadata> next = queue.onFinished();
            if (next.isEmpty() && wave) {
                next = extendWave();
            }
            if (next.isEmpty()) {
                PrototypeAudio.release();
                wave = false;
                return "Очередь закончилась.";
            }
            return start(next.get());
        });
    }

    private static Optional<TrackMetadata> extendWave() throws Exception {
        if (waveSession.isEmpty()) {
            return Optional.empty();
        }
        WavePage page = provider().moreWave(waveSession, queue.ids());
        if (page.tracks().isEmpty()) {
            return Optional.empty();
        }
        queue.append(page.tracks());
        List<TrackMetadata> combined = new ArrayList<>(waveTracks);
        combined.addAll(page.tracks());
        waveTracks = List.copyOf(combined);
        return queue.nextTrack();
    }

    private static String openTracks(List<TrackMetadata> tracks, String title) throws Exception {
        listed = List.copyOf(tracks);
        LibraryView.show(title, trackRows(tracks));
        String tail = tracks.size() >= YandexMusicProvider.COLLECTION_LIMIT
                ? " Показаны первые " + YandexMusicProvider.COLLECTION_LIMIT + "."
                : "";
        return playTracks(tracks, 0, false, "") + tail;
    }

    private static String playTracks(List<TrackMetadata> tracks, int index, boolean asWave, String sessionId) throws Exception {
        if (tracks == null || tracks.isEmpty()) {
            throw new MusicException("В этом списке нет треков.");
        }
        int startAt = index < 0 || index >= tracks.size() ? 0 : index;
        RepeatMode repeat = queue.repeat();
        boolean shuffle = queue.shuffled();
        PlayerQueue next = new PlayerQueue(tracks);
        next.setRepeat(repeat);
        next.select(startAt);
        next.setShuffle(shuffle);
        queue = next;
        wave = asWave;
        waveSession = asWave ? sessionId : "";
        return start(next.current().orElseThrow(() -> new MusicException("В этом списке нет треков.")));
    }

    private static void ensureQueue() throws Exception {
        if (queue.size() > 0) {
            return;
        }
        List<TrackMetadata> tracks = provider().likedTracks();
        likes = List.copyOf(tracks);
        listed = likes;
        for (TrackMetadata track : tracks) {
            likedIds.add(track.id());
        }
        queue = new PlayerQueue(tracks);
    }

    private static String start(TrackMetadata track) throws Exception {
        userStop = false;
        endHandled = false;
        InputStream stream = provider().openUnencryptedMp3(track.id());
        try {
            var session = PrototypeAudio.backend().openMp3(stream);
            session.setVolume(PersonalVolume.get());
            PrototypeAudio.controller().start(session);
        } catch (RuntimeException exception) {
            stream.close();
            throw exception;
        }
        CoverArt.show(track);
        return "Играет: " + track.artists() + " — " + track.title();
    }

    private static List<LibraryView.Row> trackRows(List<TrackMetadata> tracks) {
        List<LibraryView.Row> rows = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            TrackMetadata track = tracks.get(i);
            rows.add(new LibraryView.Row(
                    shorten(track.artists() + " — " + track.title()),
                    LibraryView.Kind.TRACK,
                    track.id(),
                    "",
                    i
            ));
        }
        return rows;
    }

    private static LibraryView.Row refRow(String prefix, CatalogRef ref, LibraryView.Kind kind) {
        String subtitle = ref.subtitle().isBlank() ? "" : " — " + ref.subtitle();
        return new LibraryView.Row(shorten(prefix + ref.title() + subtitle), kind, ref.id(), ref.ownerId(), -1);
    }

    private static String shorten(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= 48) {
            return value;
        }
        return value.substring(0, 45) + "...";
    }

    private static void run(String label, Task task) {
        if (!busy.compareAndSet(false, true)) {
            status = "Предыдущий запрос ещё выполняется.";
            return;
        }
        status = label + "...";
        ClientBackground.run(() -> {
            try {
                status = task.run();
            } catch (Exception exception) {
                status = messageOf(exception);
            } finally {
                busy.set(false);
            }
        });
    }

    private static YandexMusicProvider provider() throws Exception {
        return ClientTokenStore.readToken()
                .map(YandexMusicProvider::create)
                .orElseThrow(() -> new MusicException(
                        "Токен не найден. Запишите его в " + ClientTokenStore.file()
                                + " или в переменную YANDEX_MUSIC_TOKEN. Пароль не нужен."
                ));
    }

    private static String messageOf(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Ошибка: " + exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    @FunctionalInterface
    private interface Task {
        String run() throws Exception;
    }
}
