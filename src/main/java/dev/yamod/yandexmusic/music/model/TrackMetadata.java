package dev.yamod.yandexmusic.music.model;

/**
 * Метаданные одного трека, достаточные для прототипа.
 */
public record TrackMetadata(String id, String title, String artists, Boolean available, long durationMs) {
    public TrackMetadata {
        id = id == null ? "" : id;
        title = title == null || title.isBlank() ? "Без названия" : title;
        artists = artists == null || artists.isBlank() ? "Исполнитель неизвестен" : artists;
    }

    public String describe() {
        String availability = available == null
                ? "доступность не указана"
                : available ? "доступен" : "помечен как недоступный";
        long seconds = Math.max(0, durationMs) / 1000;
        return "Трек: " + artists + " — " + title + " (" + availability + ", " + seconds + " с, id " + id + ").";
    }
}
