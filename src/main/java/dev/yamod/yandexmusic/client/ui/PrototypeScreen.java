package dev.yamod.yandexmusic.client.ui;

import dev.yamod.yandexmusic.audio.PlaybackState;
import dev.yamod.yandexmusic.client.ClientBackground;
import dev.yamod.yandexmusic.client.ClientTokenStore;
import dev.yamod.yandexmusic.client.PrototypeAudio;
import dev.yamod.yandexmusic.music.MusicException;
import dev.yamod.yandexmusic.music.model.StreamDecision;
import dev.yamod.yandexmusic.music.model.TrackMetadata;
import dev.yamod.yandexmusic.music.yandex.YandexMusicProvider;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Экран проверки фазы 1. Это не интерфейс плеера из следующих фаз.
 * Закрытие экрана не останавливает звук.
 */
public final class PrototypeScreen extends Screen {
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile String status = "Прототип фазы 1. Токен читается из переменной YANDEX_MUSIC_TOKEN или из config/yandexmusic/token.txt. В исходники его класть не нужно.";
    private volatile String trackId = "";

    public PrototypeScreen() {
        super(Component.literal("Яндекс Музыка — прототип"));
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 110;
        int y = 36;
        addRenderableWidget(button(x, y, "Проверить аккаунт", this::checkAccount));
        addRenderableWidget(button(x, y + 24, "Метаданные любимого трека", this::loadTrack));
        addRenderableWidget(button(x, y + 48, "Проверить аудиопоток", this::checkStream));
        addRenderableWidget(button(x, y + 72, "Играть незашифрованный mp3", this::playTrack));
        addRenderableWidget(button(x, y + 96, "Тестовый тон", this::playTone));
        addRenderableWidget(button(x, y + 120, "Пауза / продолжить", () -> PrototypeAudio.controller().togglePause()));
        addRenderableWidget(button(x, y + 144, "Стоп", this::stop));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(playbackLine()), this.width / 2, 176, 0xA0D0A0);
        int lineY = 192;
        for (var line : this.font.split(Component.literal(status), this.width - 40)) {
            graphics.drawString(this.font, line, 20, lineY, 0xE0E0E0);
            lineY += 10;
            if (lineY > this.height - 20) {
                break;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Button button(int x, int y, String label, Runnable action) {
        return Button.builder(Component.literal(label), button -> action.run())
                .bounds(x, y, 220, 20)
                .build();
    }

    private void checkAccount() {
        runTask("Проверяю аккаунт", () -> {
            YandexMusicProvider provider = provider();
            return provider.account().describe();
        });
    }

    private void loadTrack() {
        runTask("Загружаю метаданные", () -> {
            TrackMetadata track = provider().firstLikedTrack();
            trackId = track.id();
            return track.describe();
        });
    }

    private void checkStream() {
        runTask("Проверяю варианты потока", () -> {
            String id = requireTrackId();
            StreamDecision decision = provider().availability(id);
            return decision.explanation();
        });
    }

    private void playTrack() {
        runTask("Открываю поток", () -> {
            InputStream stream = provider().openUnencryptedMp3(requireTrackId());
            try {
                PrototypeAudio.controller().start(PrototypeAudio.backend().openMp3(stream));
            } catch (RuntimeException exception) {
                stream.close();
                throw exception;
            }
            return "Воспроизведение незашифрованного mp3 запущено.";
        });
    }

    private void playTone() {
        try {
            PrototypeAudio.controller().start(PrototypeAudio.backend().openTone());
            status = "Играет локальный тестовый тон. Это не трек Яндекс Музыки. Пауза и стоп проверяют освобождение сессии.";
        } catch (RuntimeException exception) {
            status = messageOf(exception);
        }
    }

    private void stop() {
        PrototypeAudio.release();
        status = "Воспроизведение остановлено, аудиоресурсы освобождены.";
    }

    private void runTask(String label, Task task) {
        if (!busy.compareAndSet(false, true)) {
            status = "Предыдущий запрос ещё выполняется.";
            return;
        }
        status = label + "...";
        ClientBackground.run(() -> {
            try {
                status = task.run();
            } catch (Exception exception) {
                status = messageOf(exception);
            } finally {
                busy.set(false);
            }
        });
    }

    private YandexMusicProvider provider() throws Exception {
        return ClientTokenStore.readToken()
                .map(YandexMusicProvider::create)
                .orElseThrow(() -> new MusicException(
                        "Токен не найден. Создайте файл " + ClientTokenStore.file()
                                + " и запишите в него одну строку с токеном, либо задайте переменную YANDEX_MUSIC_TOKEN. Пароль не нужен, токен в исходный код вставлять нельзя."
                ));
    }

    private String requireTrackId() {
        if (trackId == null || trackId.isBlank()) {
            throw new MusicException("Сначала получите метаданные любимого трека.");
        }
        return trackId;
    }

    private String playbackLine() {
        PlaybackState state = PrototypeAudio.controller().state();
        String error = PrototypeAudio.controller().errorMessage();
        if (error != null && !error.isBlank()) {
            return "Сессия: " + state + ". " + error;
        }
        return "Сессия: " + state + ". Закрытие экрана звук не останавливает.";
    }

    private static String messageOf(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Ошибка: " + exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    @FunctionalInterface
    private interface Task {
        String run() throws Exception;
    }
}
