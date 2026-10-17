package dev.yamod.yandexmusic.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Один фоновый поток для запросов к API, чтобы не блокировать игру.
 */
public final class ClientBackground {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "yandexmusic-api");
        thread.setDaemon(true);
        return thread;
    });

    private ClientBackground() {
    }

    public static void run(Runnable task) {
        EXECUTOR.execute(task);
    }

    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
