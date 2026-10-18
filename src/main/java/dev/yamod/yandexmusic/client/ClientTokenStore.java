package dev.yamod.yandexmusic.client;

import dev.yamod.yandexmusic.music.TokenFiles;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ClientTokenStore {
    private ClientTokenStore() {
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("yandexmusic").resolve("token.txt");
    }

    public static Optional<String> readToken() throws IOException {
        return TokenFiles.read(System.getenv("YANDEX_MUSIC_TOKEN"), file());
    }

    public static boolean environmentSuppliesToken() {
        String value = System.getenv("YANDEX_MUSIC_TOKEN");
        return value != null && !value.isBlank();
    }

    public static void save(String token) throws IOException {
        String clean = TokenFiles.extract(token);
        if (clean == null) {
            throw new IOException("token rejected");
        }
        Path path = file();
        Files.createDirectories(path.getParent());
        Files.writeString(path, clean + "\n", StandardCharsets.UTF_8);
    }

    public static boolean clearFile() throws IOException {
        return Files.deleteIfExists(file());
    }
}
