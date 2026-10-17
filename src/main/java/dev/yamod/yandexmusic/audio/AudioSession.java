package dev.yamod.yandexmusic.audio;

public interface AudioSession extends AutoCloseable {
    void play();

    void pause();

    void resume();

    PlaybackState state();

    String errorMessage();

    default long positionMillis() {
        return 0;
    }

    default void seek(long millis) {
    }

    default boolean naturallyEnded() {
        return false;
    }

    default void setVolume(float volume) {
    }

    @Override
    void close();
}
