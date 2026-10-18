package dev.yamod.redio.music.model;

import java.util.Comparator;
import java.util.List;

/**
 * Выбирает только незашифрованный mp3. Если сервис отдал лишь encraw, воспроизведение запрещено.
 */
public final class StreamChooser {
    private StreamChooser() {
    }

    public static StreamDecision choose(List<DownloadOption> options) {
        if (options == null || options.isEmpty()) {
            return new StreamDecision(
                    StreamAccess.UNAVAILABLE,
                    null,
                    "API не вернуло вариантов загрузки. Воспроизведение этого трека не подтверждено."
            );
        }
        DownloadOption best = options.stream()
                .filter(DownloadOption::unencryptedMp3Candidate)
                .max(Comparator.comparingInt(DownloadOption::bitrateKbps))
                .orElse(null);
        if (best != null) {
            return new StreamDecision(
                    StreamAccess.UNENCRYPTED_MP3,
                    best,
                    "Найден незашифрованный mp3, " + best.bitrateKbps() + " кбит/с. Ссылка запрашивается в момент воспроизведения и живёт около минуты."
            );
        }
        boolean encrypted = options.stream().anyMatch(DownloadOption::encrypted);
        if (encrypted) {
            return new StreamDecision(
                    StreamAccess.ENCRYPTED_REFUSED,
                    null,
                    "Сервис отдал зашифрованный поток encraw. Расшифровка не выполняется: это обход технической защиты."
            );
        }
        boolean previewOnly = options.stream().anyMatch(DownloadOption::preview);
        if (previewOnly) {
            return new StreamDecision(
                    StreamAccess.UNAVAILABLE,
                    null,
                    "Сервис отдал только короткое превью, а не полный трек."
            );
        }
        return new StreamDecision(
                StreamAccess.UNAVAILABLE,
                null,
                "Среди вариантов нет незашифрованного mp3. Другие кодеки и зашифрованная доставка в прототипе не воспроизводятся."
        );
    }
}
