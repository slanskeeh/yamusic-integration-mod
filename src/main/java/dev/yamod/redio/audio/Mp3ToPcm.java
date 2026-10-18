package dev.yamod.redio.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Отдельная программа декодирования mp3. Её запускает обычная Java, не загрузчик Minecraft.
 */
public final class Mp3ToPcm {
    private Mp3ToPcm() {
    }

    public static void main(String[] args) {
        try {
            int frames = decode(System.in, System.out);
            if (frames == 0) {
                throw new IllegalStateException("во входном mp3 нет кадров");
            }
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            System.err.println(message);
            System.exit(1);
        }
    }

    public static int decode(InputStream mp3, OutputStream pcm) throws Exception {
        Bitstream bitstream = new Bitstream(mp3);
        Decoder decoder = new Decoder();
        boolean wroteHeader = false;
        int frames = 0;
        try {
            while (true) {
                Header header = bitstream.readFrame();
                if (header == null) {
                    break;
                }
                if (!(decoder.decodeFrame(header, bitstream) instanceof SampleBuffer buffer)) {
                    throw new IllegalStateException("декодер вернул неподдерживаемый буфер");
                }
                if (!wroteHeader) {
                    writeInt(pcm, buffer.getSampleFrequency());
                    writeInt(pcm, buffer.getChannelCount());
                    wroteHeader = true;
                }
                writeSamples(pcm, buffer);
                bitstream.closeFrame();
                frames++;
            }
        } finally {
            bitstream.close();
        }
        pcm.flush();
        return frames;
    }

    private static void writeSamples(OutputStream pcm, SampleBuffer buffer) throws Exception {
        short[] samples = buffer.getBuffer();
        int length = buffer.getBufferLength();
        byte[] bytes = new byte[length * 2];
        for (int index = 0; index < length; index++) {
            short sample = samples[index];
            bytes[index * 2] = (byte) (sample & 0xff);
            bytes[index * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }
        pcm.write(bytes);
    }

    private static void writeInt(OutputStream pcm, int value) throws Exception {
        pcm.write(value & 0xff);
        pcm.write((value >> 8) & 0xff);
        pcm.write((value >> 16) & 0xff);
        pcm.write((value >> 24) & 0xff);
    }
}
