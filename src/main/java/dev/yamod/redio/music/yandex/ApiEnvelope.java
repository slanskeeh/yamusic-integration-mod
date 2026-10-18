package dev.yamod.redio.music.yandex;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yamod.redio.music.MusicException;

/**
 * Достаёт поле {@code result} из конверта неофициального API.
 */
public final class ApiEnvelope {
    private ApiEnvelope() {
    }

    public static JsonElement result(String body) {
        if (body == null || body.isBlank()) {
            throw new MusicException("API вернуло пустой ответ.");
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (RuntimeException exception) {
            throw new MusicException("API вернуло не JSON.");
        }
        if (!root.isJsonObject()) {
            throw new MusicException("Ответ API не является объектом JSON.");
        }
        JsonObject object = root.getAsJsonObject();
        JsonElement result = object.get("result");
        if (result != null && !result.isJsonNull()) {
            return result;
        }
        throw new MusicException(errorText(object.get("error")));
    }

    private static String errorText(JsonElement error) {
        if (error == null || error.isJsonNull()) {
            return "В ответе API нет поля result.";
        }
        if (error.isJsonObject()) {
            JsonObject details = error.getAsJsonObject();
            String name = text(details.get("name"));
            String message = text(details.get("message"));
            if (!name.isEmpty() && !message.isEmpty()) {
                return "Ошибка API: " + name + ". " + message;
            }
            if (!name.isEmpty()) {
                return "Ошибка API: " + name + ".";
            }
            if (!message.isEmpty()) {
                return "Ошибка API: " + message;
            }
        }
        if (error.isJsonPrimitive()) {
            return "Ошибка API: " + error.getAsString();
        }
        return "API вернуло ошибку без описания.";
    }

    private static String text(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return "";
        }
        return element.getAsString();
    }
}
