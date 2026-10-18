package dev.yamod.yandexmusic.client;

import dev.yamod.yandexmusic.audio.PlaybackState;
import dev.yamod.yandexmusic.music.MusicException;
import dev.yamod.yandexmusic.music.PlaybackTime;
import dev.yamod.yandexmusic.music.PlayerQueue;
import dev.yamod.yandexmusic.music.RepeatMode;
import dev.yamod.yandexmusic.music.model.AccountSnapshot;
import dev.yamod.yandexmusic.music.model.CatalogRef;
import dev.yamod.yandexmusic.music.model.SearchPage;
import dev.yamod.yandexmusic.music.model.TrackMetadata;
import dev.yamod.yandexmusic.music.model.WavePage;
import dev.yamod.yandexmusic.music.yandex.JavaHttpTransport;
import dev.yamod.yandexmusic.music.yandex.YandexMusicProvider;
import dev.yamod.yandexmusic.music.yandex.YandexOAuth;

import java.io.IOException;
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
    private static volatile List<CatalogRef> albums = List.of();
    private static volatile List<TrackMetadata> openedAlbum = List.of();
    private static volatile boolean albumOpen;
    private static final Set<String> likedIds = Collections.synchronizedSet(new HashSet<>());
    private static volatile String waveSession = "";
    private static volatile String waveBatch = "";
    private static volatile String waveTrackId = "";
    private static volatile boolean waveIntroSent;
    private static volatile boolean wave;
    private static volatile WaveNotice pendingLeave;
    private static volatile String status = "Нажмите «Играть» или откройте вкладку коллекции.";
    private static final AtomicBoolean busy = new AtomicBoolean();
    private static volatile boolean endHandled;
    private static volatile boolean userStop;
    private static volatile String accountName = "";
    private static volatile int tokenState;
    private static volatile String loginCode = "";
    private static volatile String loginPage = "";
    private static volatile String pendingLoginPage = "";
    private static volatile boolean cancelSignIn;

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

    public static List<CatalogRef> albums() {
        return albums;
    }

    public static List<TrackMetadata> openedAlbum() {
        return openedAlbum;
    }

    public static boolean albumOpen() {
        return albumOpen;
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
            boolean askedStation = false;
            if (track.isEmpty() && wave) {
                leaveWaveTrack(true);
                askedStation = true;
                track = extendWave();
            }
            if (track.isEmpty()) {
                throw new MusicException("Это последний трек в очереди.");
            }
            pendingLeave = askedStation ? null : takeWaveNotice(true);
            return start(track.get());
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
            pendingLeave = takeWaveNotice(true);
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
            pendingLeave = takeWaveNotice(true);
            listed = page.tracks();
            waveTracks = listed;
            waveBatch = page.batchId();
            waveIntroSent = false;
            LibraryView.show("Моя волна", trackRows(page.tracks()));
            return playTracks(page.tracks(), 0, true, page.sessionId())
                    + " Станция узнает, какие треки дослушаны и пропущены.";
        });
    }

    public static String accountLabel() {
        if (!loginCode.isEmpty()) {
            return loginCode;
        }
        if (!accountName.isEmpty()) {
            return accountName;
        }
        return signedIn() ? "Аккаунт сохранён" : "Вход не выполнен";
    }

    public static boolean signedIn() {
        if (tokenState == 0) {
            try {
                tokenState = ClientTokenStore.readToken().isPresent() ? 2 : 1;
            } catch (IOException exception) {
                tokenState = 1;
            }
        }
        return tokenState == 2;
    }

    public static boolean signingIn() {
        return !loginCode.isEmpty();
    }

    public static String loginCode() {
        return loginCode;
    }

    public static String loginPage() {
        return loginPage;
    }

    public static String takeLoginPage() {
        String page = pendingLoginPage;
        pendingLoginPage = "";
        return page == null || page.isBlank() ? null : page;
    }

    public static void explain(String message) {
        if (message != null && !message.isBlank()) {
            status = message;
        }
    }

    public static void beginSignIn() {
        cancelSignIn = false;
        run("Вход", () -> {
            try {
                YandexOAuth oauth = new YandexOAuth(new JavaHttpTransport());
                YandexOAuth.DeviceLogin code = oauth.requestCode();
                if (cancelSignIn) {
                    return null;
                }
                loginCode = code.userCode();
                loginPage = code.page();
                pendingLoginPage = code.page();
                status = "Код " + code.userCode() + ". Подтвердите его на странице Яндекса. Пароль в игру не вводится.";
                long deadline = System.currentTimeMillis() + code.expiresInSeconds() * 1000L;
                Optional<String> access = Optional.empty();
                while (access.isEmpty()) {
                    if (cancelSignIn) {
                        return null;
                    }
                    if (System.currentTimeMillis() >= deadline) {
                        throw new MusicException("Код входа истёк. Начните ещё раз.");
                    }
                    access = oauth.poll(code.deviceCode());
                    if (access.isEmpty()) {
                        pauseUntil(code.intervalSeconds());
                    }
                }
                if (cancelSignIn) {
                    return null;
                }
                AccountSnapshot account = YandexMusicProvider.create(access.get()).account();
                if (cancelSignIn) {
                    return null;
                }
                try {
                    ClientTokenStore.save(access.get());
                } catch (IOException exception) {
                    throw new MusicException("Не удалось сохранить токен в папке настроек.");
                }
                tokenState = 2;
                accountName = account.displayName();
                return "Вход выполнен: " + account.displayName() + ".";
            } finally {
                loginCode = "";
                loginPage = "";
                pendingLoginPage = "";
            }
        });
    }

    public static void cancelSignIn() {
        cancelSignIn = true;
        loginCode = "";
        loginPage = "";
        pendingLoginPage = "";
        status = "Вход отменён.";
    }

    public static void signIn(String pasted) {
        String token = dev.yamod.yandexmusic.music.TokenFiles.extract(pasted);
        if (token == null) {
            status = "Не вижу токен. Вставьте адрес страницы после входа или сам токен.";
            return;
        }
        run("Вход", () -> {
            AccountSnapshot account = YandexMusicProvider.create(token).account();
            try {
                ClientTokenStore.save(token);
            } catch (IOException exception) {
                throw new MusicException("Не удалось сохранить токен в папке настроек.");
            }
            tokenState = 2;
            accountName = account.displayName();
            return "Вход выполнен: " + account.displayName() + ".";
        });
    }

    public static void signOut() {
        cancelSignIn = true;
        loginCode = "";
        loginPage = "";
        pendingLoginPage = "";
        stop();
        likes = List.of();
        listed = List.of();
        waveTracks = List.of();
        albums = List.of();
        openedAlbum = List.of();
        albumOpen = false;
        likedIds.clear();
        queue = PlayerQueue.empty();
        accountName = "";
        LibraryView.show("Аккаунт", List.of());
        try {
            ClientTokenStore.clearFile();
        } catch (IOException exception) {
            status = "Не удалось удалить файл токена.";
            return;
        }
        if (ClientTokenStore.environmentSuppliesToken()) {
            tokenState = 2;
            status = "Файл токена удалён. Переменная YANDEX_MUSIC_TOKEN всё ещё выполняет вход. Уберите её и перезапустите игру.";
            return;
        }
        tokenState = 1;
        status = "Вы вышли из аккаунта. Сохранённый токен удалён.";
    }

    public static void showLikes() {
        run("Любимые", () -> {
            YandexMusicProvider music = provider();
            accountName = music.account().displayName();
            tokenState = 2;
            List<TrackMetadata> tracks = music.likedTracks();
            likes = List.copyOf(tracks);
            listed = likes;
            likedIds.clear();
            for (TrackMetadata track : tracks) {
                likedIds.add(track.id());
            }
            LibraryView.show("Любимые", trackRows(tracks));
            return "Любимых треков в списке: " + tracks.size() + ".";
        });
    }

    public static void showAlbums() {
        albumOpen = false;
        run("Альбомы", () -> {
            List<CatalogRef> found = provider().likedAlbums();
            albums = List.copyOf(found);
            if (found.isEmpty()) {
                return "В любимых альбомах пусто.";
            }
            return "Альбомов: " + found.size() + ".";
        });
    }

    public static void openAlbum(String albumId) {
        run("Альбом", () -> {
            List<TrackMetadata> tracks = provider().albumTracks(albumId);
            openedAlbum = List.copyOf(tracks);
            albumOpen = true;
            String title = "Альбом";
            for (CatalogRef album : albums) {
                if (album.id().equals(albumId)) {
                    title = album.title();
                    break;
                }
            }
            listed = openedAlbum;
            LibraryView.show(title, trackRows(tracks));
            return playTracks(tracks, 0, false, "");
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
        WaveNotice parked = pendingLeave;
        pendingLeave = null;
        if (parked != null) {
            ClientBackground.run(() -> sendWaveNotice(parked));
        }
        leaveWaveTrackLater(true);
        userStop = true;
        endHandled = true;
        wave = false;
        waveSession = "";
        waveBatch = "";
        waveIntroSent = false;
        PrototypeAudio.release();
        status = "Воспроизведение остановлено.";
    }

    private static void onEnded() {
        run("Дальше", () -> {
            if (queue.size() == 0) {
                return "Очередь пуста.";
            }
            WaveNotice finished = takeWaveNotice(false);
            Optional<TrackMetadata> next = queue.onFinished();
            if (next.isEmpty() && wave) {
                if (finished != null) {
                    sendWaveNotice(finished);
                    finished = null;
                }
                next = extendWave();
            }
            if (next.isEmpty()) {
                PrototypeAudio.release();
                wave = false;
                return "Очередь закончилась.";
            }
            pendingLeave = finished;
            return start(next.get());
        });
    }

    private static Optional<TrackMetadata> extendWave() throws Exception {
        if (waveSession.isEmpty()) {
            return Optional.empty();
        }
        WavePage page = provider().moreWave(waveSession, queue.ids());
        if (!page.batchId().isEmpty()) {
            waveBatch = page.batchId();
        }
        List<TrackMetadata> fresh = new ArrayList<>();
        Set<String> known = new HashSet<>();
        for (TrackMetadata existing : waveTracks) {
            known.add(existing.id());
        }
        for (TrackMetadata track : page.tracks()) {
            if (known.add(track.id())) {
                fresh.add(track);
            }
        }
        if (fresh.isEmpty()) {
            return Optional.empty();
        }
        queue.append(fresh);
        List<TrackMetadata> combined = new ArrayList<>(waveTracks);
        combined.addAll(fresh);
        waveTracks = List.copyOf(combined);
        listed = waveTracks;
        return queue.nextTrack();
    }

    private static String openTracks(List<TrackMetadata> tracks, String title) throws Exception {
        listed = List.copyOf(tracks);
        LibraryView.show(title, trackRows(tracks));
        return playTracks(tracks, 0, false, "");
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
        if (wave && !waveTrackId.isEmpty()) {
            pendingLeave = takeWaveNotice(true);
        }
        queue = next;
        wave = asWave;
        waveSession = asWave ? sessionId : "";
        if (!asWave) {
            waveBatch = "";
            waveIntroSent = false;
        }
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
        WaveNotice left = pendingLeave;
        pendingLeave = null;
        if (left != null) {
            sendWaveNotice(left);
        }
        announceWave(track);
        return "Играет: " + track.artists() + " — " + track.title();
    }

    private static void announceWave(TrackMetadata track) {
        if (!wave || waveSession.isEmpty()) {
            return;
        }
        try {
            YandexMusicProvider music = provider();
            if (!waveIntroSent) {
                try {
                    music.waveFeedback(waveSession, waveBatch, "radioStarted", "", 0);
                } catch (Exception ignored) {
                    // станция может не принять событие, локальный трек уже играет
                }
                waveIntroSent = true;
            }
            try {
                music.waveFeedback(waveSession, waveBatch, "trackStarted", track.id(), 0);
            } catch (Exception ignored) {
                // следующая партия всё равно уйдёт с очередью уже сыгранных идентификаторов
            }
        } catch (Exception ignored) {
            // без токена сюда не доходим: трек уже открыт
        }
        waveTrackId = track.id();
    }

    private static void leaveWaveTrack(boolean skipped) {
        WaveNotice notice = takeWaveNotice(skipped);
        if (notice != null) {
            sendWaveNotice(notice);
        }
    }

    private static void leaveWaveTrackLater(boolean skipped) {
        WaveNotice notice = takeWaveNotice(skipped);
        if (notice != null) {
            ClientBackground.run(() -> sendWaveNotice(notice));
        }
    }

    private static WaveNotice takeWaveNotice(boolean skipped) {
        if (!wave || waveSession.isEmpty() || waveTrackId.isEmpty()) {
            waveTrackId = "";
            return null;
        }
        double played = Math.max(0, PrototypeAudio.controller().positionMillis() / 1000.0);
        WaveNotice notice = new WaveNotice(waveSession, waveBatch, waveTrackId, skipped, played);
        waveTrackId = "";
        return notice;
    }

    private static void sendWaveNotice(WaveNotice notice) {
        try {
            provider().waveFeedback(
                    notice.sessionId,
                    notice.batchId,
                    notice.skipped ? "skip" : "trackFinished",
                    notice.trackId,
                    notice.playedSeconds
            );
        } catch (Exception ignored) {
            // пропуск отчёта не останавливает локальное воспроизведение
        }
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

    private static void pauseUntil(int seconds) throws InterruptedException {
        for (int i = 0; i < seconds; i++) {
            if (cancelSignIn) {
                return;
            }
            Thread.sleep(1000L);
        }
    }

    private static void run(String label, Task task) {
        if (!busy.compareAndSet(false, true)) {
            status = "Предыдущий запрос ещё выполняется.";
            return;
        }
        status = label + "...";
        ClientBackground.run(() -> {
            try {
                String result = task.run();
                if (result != null) {
                    status = result;
                }
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

    private record WaveNotice(String sessionId, String batchId, String trackId, boolean skipped, double playedSeconds) {
    }
}
