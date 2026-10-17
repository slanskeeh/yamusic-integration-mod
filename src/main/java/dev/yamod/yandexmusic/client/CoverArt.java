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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш обложек в памяти. Токен на адрес картинки не отправляется.
 */
public final class CoverArt {
    private static final int MAX_BYTES = 300_000;
    private static final int MAX_ENTRIES = 24;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Cover> COVERS = new ConcurrentHashMap<>();
    private static final Deque<String> ORDER = new ArrayDeque<>();

    private CoverArt() {
    }

    public static void show(TrackMetadata track) {
        request(track == null ? "" : track.coverUri());
    }

    public static ResourceLocation location(String coverUri) {
        String url = CoverUrls.fromCoverUri(coverUri).orElse("");
        if (url.isEmpty()) {
            return null;
        }
        Cover cover = COVERS.get(url);
        return cover != null && cover.ready ? cover.location : null;
    }

    public static int width(String coverUri) {
        Cover cover = cover(coverUri);
        return cover == null ? 1 : cover.width;
    }

    public static int height(String coverUri) {
        Cover cover = cover(coverUri);
        return cover == null ? 1 : cover.height;
    }

    public static void request(String coverUri) {
        String url = CoverUrls.fromCoverUri(coverUri).orElse("");
        if (url.isEmpty()) {
            return;
        }
        Cover existing = COVERS.get(url);
        if (existing != null) {
            return;
        }
        if (COVERS.putIfAbsent(url, Cover.pending(url)) != null) {
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
                COVERS.remove(url);
                YandexMusicMod.LOGGER.info("Yandex cover skipped");
            }
        });
    }

    private static Cover cover(String coverUri) {
        String url = CoverUrls.fromCoverUri(coverUri).orElse("");
        return url.isEmpty() ? null : COVERS.get(url);
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
        Minecraft minecraft = Minecraft.getInstance();
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            NativeImage image = NativeImage.read(input);
            Cover cover = COVERS.computeIfAbsent(url, Cover::pending);
            cover.width = Math.max(1, image.getWidth());
            cover.height = Math.max(1, image.getHeight());
            minecraft.getTextureManager().register(cover.location, new DynamicTexture(image));
            cover.ready = true;
            synchronized (ORDER) {
                ORDER.remove(url);
                ORDER.addLast(url);
                while (ORDER.size() > MAX_ENTRIES) {
                    String old = ORDER.removeFirst();
                    Cover removed = COVERS.remove(old);
                    if (removed != null && removed.ready) {
                        minecraft.getTextureManager().release(removed.location);
                    }
                }
            }
        } catch (Exception exception) {
            COVERS.remove(url);
            YandexMusicMod.LOGGER.info("Yandex cover skipped");
        }
    }

    private static final class Cover {
        private final ResourceLocation location;
        private volatile boolean ready;
        private volatile int width = 1;
        private volatile int height = 1;

        private Cover(ResourceLocation location) {
            this.location = location;
        }

        private static Cover pending(String url) {
            String path = "dynamic/c" + Integer.toUnsignedString(url.hashCode(), 16);
            return new Cover(new ResourceLocation(YandexMusicMod.MOD_ID, path));
        }
    }
}
