package dev.yamod.yandexmusic.music.model;

/**
 * Ссылка на альбом, исполнителя или плейлист. Для плейлиста {@code ownerId} — uid владельца, {@code id} — kind.
 */
public record CatalogRef(String id, String ownerId, String title, String subtitle) {
    public CatalogRef {
        id = id == null ? "" : id;
        ownerId = ownerId == null ? "" : ownerId;
        title = title == null || title.isBlank() ? "Без названия" : title;
        subtitle = subtitle == null ? "" : subtitle;
    }
}
