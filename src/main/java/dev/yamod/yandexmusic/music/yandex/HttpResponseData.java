package dev.yamod.yandexmusic.music.yandex;

public record HttpResponseData(int status, String body) {
    public HttpResponseData {
        body = body == null ? "" : body;
    }
}
