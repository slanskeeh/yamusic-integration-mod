package dev.yamod.yandexmusic.music.model;

/**
 * Один вариант получения файла. Поле ключа расшифровки намеренно отсутствует:
 * прототип не читает и не хранит его.
 */
public record DownloadOption(
        String codec,
        int bitrateKbps,
        boolean preview,
        String transport,
        String downloadInfoUrl
) {
    public boolean encrypted() {
        return transport != null && transport.equalsIgnoreCase("encraw");
    }

    public boolean unencryptedMp3Candidate() {
        return !preview
                && !encrypted()
                && codec != null
                && codec.equalsIgnoreCase("mp3")
                && (transport == null || transport.isBlank() || transport.equalsIgnoreCase("raw"))
                && downloadInfoUrl != null
                && !downloadInfoUrl.isBlank();
    }
}
