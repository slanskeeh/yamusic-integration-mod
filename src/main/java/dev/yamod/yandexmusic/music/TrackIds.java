package dev.yamod.yandexmusic.music;

/**
 * Идентификатор любимого трека в API иногда приходит как {@code trackId:albumId}.
 * Для запросов метаданных и download-info нужна только числовая часть трека.
 */
public final class TrackIds {
    private TrackIds() {
    }

    public static String apiId(String raw) {
        if (raw == null || !raw.matches("[0-9]{1,20}(:[0-9]{1,20})?")) {
            throw new MusicException("Идентификатор трека имеет неожиданный вид и был отклонён.");
        }
        int colon = raw.indexOf(':');
        return colon < 0 ? raw : raw.substring(0, colon);
    }
}
