package dev.yamod.redio.music;

/**
 * Текст позиции воспроизведения. Не знает про Minecraft.
 */
public final class PlaybackTime {
    private PlaybackTime() {
    }

    public static String clock(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        return totalSeconds / 60 + ":" + String.format("%02d", totalSeconds % 60);
    }

    public static String line(long positionMillis, long durationMillis) {
        if (durationMillis <= 0) {
            return clock(positionMillis);
        }
        return clock(positionMillis) + " / " + clock(durationMillis);
    }
}
