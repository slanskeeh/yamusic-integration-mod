package dev.yamod.yandexmusic.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaybackControllerTest {
    @Test
    void newSessionClosesThePreviousOne() {
        PlaybackController controller = new PlaybackController();
        FakeSession first = new FakeSession();
        FakeSession second = new FakeSession();
        controller.start(first);
        controller.start(second);
        assertEquals(1, first.closes);
        assertEquals(PlaybackState.PLAYING, controller.state());
        controller.togglePause();
        assertEquals(1, second.pauses);
        assertEquals(PlaybackState.PAUSED, controller.state());
        controller.togglePause();
        assertEquals(1, second.resumes);
        controller.release();
        assertEquals(1, second.closes);
        assertEquals(PlaybackState.IDLE, controller.state());
    }

    @Test
    void failedStartReleasesTheSession() {
        PlaybackController controller = new PlaybackController();
        FakeSession broken = new FakeSession();
        broken.failOnPlay = true;
        assertThrows(AudioException.class, () -> controller.start(broken));
        assertEquals(1, broken.closes);
        assertEquals(PlaybackState.IDLE, controller.state());
    }

    private static final class FakeSession implements AudioSession {
        private int pauses;
        private int resumes;
        private int closes;
        private boolean failOnPlay;
        private PlaybackState state = PlaybackState.IDLE;

        @Override
        public void play() {
            if (failOnPlay) {
                throw new AudioException("boom");
            }
            state = PlaybackState.PLAYING;
        }

        @Override
        public void pause() {
            pauses++;
            state = PlaybackState.PAUSED;
        }

        @Override
        public void resume() {
            resumes++;
            state = PlaybackState.PLAYING;
        }

        @Override
        public PlaybackState state() {
            return state;
        }

        @Override
        public String errorMessage() {
            return "";
        }

        @Override
        public void close() {
            closes++;
            state = PlaybackState.STOPPED;
        }
    }
}
