package dev.yamod.yandexmusic.music.model;

import java.util.List;

/**
 * Первая страница поиска: треки можно играть сразу, остальное открывается отдельным запросом.
 */
public record SearchPage(
        List<TrackMetadata> tracks,
        List<CatalogRef> albums,
        List<CatalogRef> artists,
        List<CatalogRef> playlists
) {
    public SearchPage {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        albums = albums == null ? List.of() : List.copyOf(albums);
        artists = artists == null ? List.of() : List.copyOf(artists);
        playlists = playlists == null ? List.of() : List.copyOf(playlists);
    }

    public boolean isEmpty() {
        return tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty();
    }
}
