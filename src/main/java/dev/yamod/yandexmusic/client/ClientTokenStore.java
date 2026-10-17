package dev.yamod.yandexmusic.client;

import dev.yamod.yandexmusic.music.TokenFiles;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
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
}
