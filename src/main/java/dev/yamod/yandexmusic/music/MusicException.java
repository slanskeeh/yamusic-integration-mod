package dev.yamod.yandexmusic.music;

/**
 * Ошибка провайдера, которую можно показать игроку. Текст не должен содержать токен.
 */
public class MusicException extends RuntimeException {
    public MusicException(String message) {
        super(message);
    }

    public MusicException(String message, Throwable cause) {
        super(message, cause);
    }
}
