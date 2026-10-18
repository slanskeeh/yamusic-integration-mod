package dev.yamod.redio.audio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mp3ProbeTest {
    @Test
    void id3HeaderIsReturnedToTheDecoder() throws IOException {
        byte[] source = new byte[] {'I', 'D', '3', 3, 1, 2};
        InputStream checked = Mp3Probe.checked(new ByteArrayInputStream(source));
        assertEquals(source.length, checked.readAllBytes().length);
    }

    @Test
    void htmlIsRejected() {
        AudioException error = assertThrows(AudioException.class, () -> Mp3Probe.checked(new ByteArrayInputStream("<html>".getBytes())));
        assertTrue(error.getMessage().contains("HTML"));
    }

    @Test
    void emptyBodyIsRejected() {
        AudioException error = assertThrows(AudioException.class, () -> Mp3Probe.checked(InputStream.nullInputStream()));
        assertTrue(error.getMessage().contains("пустой"));
    }
}
