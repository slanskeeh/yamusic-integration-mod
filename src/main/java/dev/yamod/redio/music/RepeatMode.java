package dev.yamod.redio.music;

/**
 * Локальный режим повтора. Не зависит от того, что умеет сервис.
 */
public enum RepeatMode {
    OFF,
    TRACK,
    QUEUE;

    public RepeatMode next() {
        return switch (this) {
            case OFF -> TRACK;
            case TRACK -> QUEUE;
            case QUEUE -> OFF;
        };
    }

    public String label() {
        return switch (this) {
            case OFF -> "выкл";
            case TRACK -> "трек";
            case QUEUE -> "очередь";
        };
    }
}
