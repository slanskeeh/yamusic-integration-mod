package dev.yamod.yandexmusic.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.yamod.yandexmusic.YandexMusicMod;
import dev.yamod.yandexmusic.music.CoverUrls;
import dev.yamod.yandexmusic.music.model.TrackMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Небольшой кэш обложек в памяти. Токен на адрес картинки не отправляется.
 */
public final class CoverArt {
    public static final ResourceLocation LOCATION = new ResourceLocation(YandexMusicMod.MOD_ID, "dynamic/cover");
    private static final int MAX_BYTES = 300_000;
    private static final int MAX_ENTRIES = 24;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();
    private static volatile String wanted = "";
    private static volatile boolean ready;
    private static volatile int imageWidth = 48;
    private static volatile int imageHeight = 48;

    private CoverArt() {
    }

    public static boolean ready() {
        return ready;
    }

    public static int imageWidth() {
        return imageWidth;
    }

    public static int imageHeight() {
        return imageHeight;
    }

    public static void show(TrackMetadata track) {
        String url = track == null ? "" : CoverUrls.fromCoverUri(track.coverUri()).orElse("");
        wanted = url;
        ready = false;
        if (url.isEmpty()) {
            ready = false;
            return;
        }
        byte[] cached = CACHE.get(url);
        if (cached != null) {
            Minecraft.getInstance().execute(() -> upload(url, cached));
            return;
        }
        ClientBackground.run(() -> {
            try {
                byte[] bytes = download(url);
                remember(url, bytes);
                Minecraft.getInstance().execute(() -> upload(url, bytes));
            } catch (Exception exception) {
                YandexMusicMod.LOGGER.info("Yandex cover skipped");
            }
        });
    }

    private static byte[] download(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "yandexmusic-minecraft/0.1.0")
                .GET()
                .build();
        HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IllegalStateException("cover status");
        }
        try (InputStream input = response.body()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) {
                throw new IllegalStateException("cover size");
            }
            return bytes;
        }
    }

    private static void remember(String url, byte[] bytes) {
        if (CACHE.size() >= MAX_ENTRIES) {
            String oldest = CACHE.keySet().stream().findFirst().orElse(null);
            if (oldest != null) {
                CACHE.remove(oldest);
            }
        }
        CACHE.put(url, bytes);
    }

    private static void upload(String url, byte[] bytes) {
        if (!url.equals(wanted)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            NativeImage image = NativeImage.read(input);
            imageWidth = Math.max(1, image.getWidth());
            imageHeight = Math.max(1, image.getHeight());
            minecraft.getTextureManager().register(LOCATION, new DynamicTexture(image));
            ready = true;
        } catch (Exception exception) {
            ready = false;
            YandexMusicMod.LOGGER.info("Yandex cover skipped");
        }
    }
}
