package dev.yamod.yandexmusic.music.yandex;

import dev.yamod.yandexmusic.YandexMusicMod;
import dev.yamod.yandexmusic.music.MusicException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Транспорт на стандартном HttpClient. Один повтор при обрыве сети, без повтора ответов 4xx и 5xx.
 */
public final class JavaHttpTransport implements HttpTransport {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Override
    public HttpResponseData exchange(HttpRequestData request) {
        HttpResponse<String> response = send(toRequest(request), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new HttpResponseData(response.statusCode(), response.body());
    }

    @Override
    public byte[] getBytes(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "yandexmusic-minecraft/0.1.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new MusicException("Не удалось получить XML download-info, код " + response.statusCode() + ".");
        }
        return response.body();
    }

    @Override
    public InputStream openStream(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://music.yandex.ru/")
                .header("Origin", "https://music.yandex.ru")
                .header("Accept", "*/*")
                .GET()
                .build();
        HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new MusicException("Аудиопоток не открылся, код " + response.statusCode() + ".");
        }
        byte[] body = response.body() == null ? new byte[0] : response.body();
        String type = response.headers().firstValue("content-type").orElse("unknown");
        long declared = response.headers().firstValueAsLong("content-length").orElse(-1L);
        YandexMusicMod.LOGGER.info("Yandex audio bytes: type={}, declared={}, actual={}", type, declared, body.length);
        if (body.length < 128) {
            throw new MusicException("CDN прислал " + body.length + " байт вместо файла mp3.");
        }
        if (declared > body.length) {
            throw new MusicException("CDN оборвал файл: пришло " + body.length + " байт из " + declared + ".");
        }
        return new ByteArrayInputStream(body);
    }

    private HttpRequest toRequest(HttpRequestData request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url()))
                .timeout(Duration.ofSeconds(15));
        request.headers().forEach(builder::header);
        if ("POST".equals(request.method())) {
            builder.header("Content-Type", "application/x-www-form-urlencoded");
            builder.POST(HttpRequest.BodyPublishers.ofString(request.body()));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return sendOnce(request, handler);
        } catch (MusicException first) {
            if (!first.getMessage().startsWith("Нет соединения")) {
                throw first;
            }
            return sendOnce(request, handler);
        }
    }

    private <T> HttpResponse<T> sendOnce(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return client.send(request, handler);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MusicException("Запрос к API прерван.");
        } catch (Exception exception) {
            throw new MusicException("Нет соединения с сервисом Яндекс Музыки.");
        }
    }
}
