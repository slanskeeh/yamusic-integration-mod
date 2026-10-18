package dev.yamod.redio.music.yandex;

import java.util.Map;

public record HttpRequestData(String method, String url, Map<String, String> headers, String body) {
    public HttpRequestData {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? "" : body;
    }
}
