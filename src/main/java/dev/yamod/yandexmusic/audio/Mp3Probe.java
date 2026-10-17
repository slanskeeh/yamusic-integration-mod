package dev.yamod.yandexmusic.audio;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

/**
 * Проверяет, что начало ответа похоже на mp3, и возвращает те же байты декодеру.
 */
public final class Mp3Probe {
    private Mp3Probe() {
    }

    public static InputStream checked(InputStream input) throws IOException {
        PushbackInputStream pushback = new PushbackInputStream(input, 4);
        byte[] magic = pushback.readNBytes(4);
        if (!looksLikeMp3(magic)) {
            pushback.close();
            throw new AudioException(describe(magic));
        }
        pushback.unread(magic);
        return pushback;
    }

    public static boolean looksLikeMp3(byte[] magic) {
        if (magic.length >= 3 && magic[0] == 'I' && magic[1] == 'D' && magic[2] == '3') {
            return true;
        }
        return magic.length >= 2 && (magic[0] & 0xff) == 0xff && (magic[1] & 0xe0) == 0xe0;
    }

    public static String describe(byte[] magic) {
        if (magic.length == 0) {
            return "CDN вернул пустой ответ, это не mp3.";
        }
        if (magic[0] == '<') {
            return "CDN вернул XML или HTML, а не mp3.";
        }
        if (magic[0] == '{') {
            return "CDN вернул JSON, а не mp3.";
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : magic) {
            hex.append(String.format("%02x", value));
        }
        return "CDN вернул не mp3. Начало файла: " + hex;
    }
}
