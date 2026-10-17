package dev.yamod.yandexmusic.music.yandex;

import java.io.InputStream;

public interface HttpTransport {
    HttpResponseData exchange(HttpRequestData request);

    byte[] getBytes(String url);

    InputStream openStream(String url);
}
