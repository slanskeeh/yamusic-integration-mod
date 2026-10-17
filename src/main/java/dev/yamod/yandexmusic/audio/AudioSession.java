package dev.yamod.yandexmusic.audio;

public interface AudioSession extends AutoCloseable {
    void play();

    void pause();

    void resume();

    PlaybackState state();

    String errorMessage();

    @Override
    void close();
}
