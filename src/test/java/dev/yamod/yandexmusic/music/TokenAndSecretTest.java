package dev.yamod.yandexmusic.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenAndSecretTest {
    @TempDir
    Path directory;

    @Test
    void environmentTokenWinsOverFile() throws Exception {
        Path file = directory.resolve("token.txt");
        Files.writeString(file, "from-file");
        assertEquals("from-env", TokenFiles.read(" from-env ", file).orElseThrow());
    }

    @Test
    void fileSkipsCommentsAndBom() throws Exception {
        Path file = directory.resolve("token.txt");
        Files.write(file, ("\uFEFF# comment\n\nfile-token\n").getBytes(StandardCharsets.UTF_8));
        assertEquals("file-token", TokenFiles.read(" ", file).orElseThrow());
    }

    @Test
    void missingTokenIsEmpty() throws Exception {
        assertTrue(TokenFiles.read(null, directory.resolve("absent.txt")).isEmpty());
    }

    @Test
    void secretIsRemovedFromText() {
        assertEquals("Ошибка <redacted> при входе", Secrets.redact("Ошибка secret-token при входе", "secret-token"));
    }

    @Test
    void trackIdUsesPartBeforeAlbum() {
        assertEquals("37699623", TrackIds.apiId("37699623:4532"));
    }

    @Test
    void unexpectedTrackIdIsRejected() {
        assertThrows(MusicException.class, () -> TrackIds.apiId("../etc"));
    }
}
