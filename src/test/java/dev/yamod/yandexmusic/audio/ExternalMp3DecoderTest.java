package dev.yamod.yandexmusic.audio;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalMp3DecoderTest {
    @Test
    void childProcessDecodesOneMp3Frame() throws Exception {
        byte[] mp3 = frame();
        ExternalMp3Decoder decoder = ExternalMp3Decoder.start(mp3);
        try {
            AudioFormat format = decoder.readFormat();
            assertEquals(44100, (int) format.getSampleRate());
            assertEquals(2, format.getChannels());
            byte[] pcm = new byte[8];
            assertEquals(8, decoder.read(pcm));
        } finally {
            decoder.close();
        }
    }

    @Test
    void directDecodeWritesFormatAndSamples() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int frames = Mp3ToPcm.decode(new ByteArrayInputStream(frame()), output);
        assertEquals(1, frames);
        assertTrue(output.size() > 8);
    }

    private static byte[] frame() {
        byte[] bytes = new byte[626];
        bytes[0] = (byte) 0xff;
        bytes[1] = (byte) 0xfb;
        bytes[2] = (byte) 0xb0;
        bytes[3] = 0x44;
        return bytes;
    }
}
