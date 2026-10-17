package dev.yamod.yandexmusic.client;

import dev.yamod.yandexmusic.audio.PlaybackState;
import dev.yamod.yandexmusic.music.MusicException;
import dev.yamod.yandexmusic.music.PlaybackTime;
import dev.yamod.yandexmusic.music.PlayerQueue;
import dev.yamod.yandexmusic.music.model.TrackMetadata;
import dev.yamod.yandexmusic.music.yandex.YandexMusicProvider;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Состояние личного плеера, которое живёт дольше экрана.
 */
public final class NowPlaying {
    private static final long RESTART_AFTER_MILLIS = 3000;

    private static volatile PlayerQueue queue = PlayerQueue.empty();
    private static volatile String status = "Нажмите «Играть». В очередь попадут первые любимые треки.";
    private static final AtomicBoolean busy = new AtomicBoolean();

    private NowPlaying() {
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
            ensureQueue();
            TrackMetadata track = queue.move(1).orElseThrow(() -> new MusicException(
                    "Это последний из загруженных любимых треков. Полная коллекция ещё не делается."
            ));
            return start(track);
        });
    }

    public static void previous() {
        if (PrototypeAudio.controller().positionMillis() > RESTART_AFTER_MILLIS
                && playback() != PlaybackState.IDLE
                && playback() != PlaybackState.STOPPED) {
            TrackMetadata track = track();
            if (track != null) {
                run("Сначала трека", () -> start(track));
                return;
            }
        }
        run("Предыдущий трек", () -> {
            ensureQueue();
            TrackMetadata track = queue.move(-1).orElseThrow(() -> new MusicException(
                    "Это первый трек в загруженных любимых."
            ));
            return start(track);
        });
    }

    public static void changeVolume(float delta) {
        float level = PersonalVolume.change(delta);
        PrototypeAudio.controller().setVolume(level);
        status = "Громкость плеера: " + PersonalVolume.label() + ". Общая громкость Minecraft не менялась.";
    }

    public static void stop() {
        PrototypeAudio.release();
        status = "Воспроизведение остановлено.";
    }

    private static void ensureQueue() throws Exception {
        if (queue.size() > 0) {
            return;
        }
        queue = new PlayerQueue(provider().likedTracks());
    }

    private static String start(TrackMetadata track) throws Exception {
        InputStream stream = provider().openUnencryptedMp3(track.id());
        try {
            var session = PrototypeAudio.backend().openMp3(stream);
            session.setVolume(PersonalVolume.get());
            PrototypeAudio.controller().start(session);
        } catch (RuntimeException exception) {
            stream.close();
            throw exception;
        }
        return "Играет: " + track.artists() + " — " + track.title();
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
