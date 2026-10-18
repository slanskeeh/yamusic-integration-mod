package dev.yamod.yandexmusic.music.model;

import java.util.List;

/**
 * Партия «Моей волны». Пустой sessionId значит, что идентификатор сессии не пришёл в этом ответе.
 */
public record WavePage(String sessionId, String batchId, List<TrackMetadata> tracks, boolean terminated) {
    public WavePage {
        sessionId = sessionId == null ? "" : sessionId;
        batchId = batchId == null ? "" : batchId;
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }

    public WavePage withSession(String fallbackSessionId) {
        if (!sessionId.isEmpty() || fallbackSessionId == null || fallbackSessionId.isBlank()) {
            return this;
        }
        return new WavePage(fallbackSessionId, batchId, tracks, terminated);
    }
}
