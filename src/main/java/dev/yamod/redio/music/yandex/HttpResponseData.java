package dev.yamod.redio.music.yandex;

public record HttpResponseData(int status, String body) {
    public HttpResponseData {
        body = body == null ? "" : body;
    }
}
