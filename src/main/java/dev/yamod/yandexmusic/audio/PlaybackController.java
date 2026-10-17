package dev.yamod.yandexmusic.audio;

/**
 * Держит одну текущую сессию и закрывает предыдущую перед новой.
 */
public final class PlaybackController {
    private AudioSession session;

    public synchronized PlaybackState state() {
        return session == null ? PlaybackState.IDLE : session.state();
    }

    public synchronized String errorMessage() {
        return session == null ? "" : session.errorMessage();
    }

    public void start(AudioSession next) {
        if (next == null) {
            throw new AudioException("Сессия воспроизведения не передана.");
        }
        AudioSession previous;
        synchronized (this) {
            previous = session;
            session = next;
        }
        if (previous != null) {
            previous.close();
        }
        try {
            // play() нельзя держать под замком контроллера: пока он ждёт звук,
            // клиентский поток замирает на state() и может заблокировать загрузку классов.
            next.play();
        } catch (RuntimeException exception) {
            synchronized (this) {
                if (session == next) {
                    session = null;
                }
            }
            next.close();
            throw exception;
        }
    }

    public synchronized void togglePause() {
        if (session == null) {
            return;
        }
        if (session.state() == PlaybackState.PLAYING) {
            session.pause();
        } else if (session.state() == PlaybackState.PAUSED) {
            session.resume();
        }
    }

    public synchronized long positionMillis() {
        return session == null ? 0 : session.positionMillis();
    }

    public synchronized void setVolume(float volume) {
        if (session != null) {
            session.setVolume(volume);
        }
    }

    public synchronized void seek(long millis) {
        if (session != null) {
            session.seek(millis);
        }
    }

    public synchronized boolean naturallyEnded() {
        return session != null && session.naturallyEnded();
    }

    public synchronized void release() {
        if (session != null) {
            session.close();
            session = null;
        }
    }
}
