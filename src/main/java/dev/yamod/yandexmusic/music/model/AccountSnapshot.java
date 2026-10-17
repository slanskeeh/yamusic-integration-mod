package dev.yamod.yandexmusic.music.model;

/**
 * Короткий снимок аккаунта для прототипа. Токен сюда не входит.
 */
public record AccountSnapshot(long uid, String login, String displayName) {
    public AccountSnapshot {
        login = login == null ? "" : login;
        displayName = displayName == null || displayName.isBlank() ? login : displayName;
    }

    public String describe() {
        return "Аккаунт подтверждён: " + displayName + " (uid " + uid + ").";
    }
}
