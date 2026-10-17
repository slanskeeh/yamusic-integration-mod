package dev.yamod.yandexmusic.client.ui;

import dev.yamod.yandexmusic.audio.PlaybackState;
import dev.yamod.yandexmusic.client.NowPlaying;
import dev.yamod.yandexmusic.client.PersonalVolume;
import dev.yamod.yandexmusic.music.model.TrackMetadata;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Личный плеер. Закрытие экрана не останавливает звук.
 */
public final class PrototypeScreen extends Screen {
    private Button playButton;

    public PrototypeScreen() {
        super(Component.literal("Яндекс Музыка"));
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int y = 88;
        addRenderableWidget(button(center - 155, y, 100, "Предыдущий", NowPlaying::previous));
        playButton = button(center - 50, y, 100, "Играть", NowPlaying::playOrResume);
        addRenderableWidget(playButton);
        addRenderableWidget(button(center + 55, y, 100, "Следующий", NowPlaying::next));
        addRenderableWidget(button(center - 155, y + 24, 100, "Тише", () -> NowPlaying.changeVolume(-0.1F)));
        addRenderableWidget(button(center + 55, y + 24, 100, "Громче", () -> NowPlaying.changeVolume(0.1F)));
        addRenderableWidget(button(center - 50, y + 48, 100, "Стоп", NowPlaying::stop));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        TrackMetadata track = NowPlaying.track();
        String artists = track == null ? "Нет трека" : track.artists();
        String title = track == null ? "Нажмите «Играть»" : track.title();
        graphics.drawCenteredString(this.font, Component.literal(artists), this.width / 2, 36, 0xC8C8C8);
        graphics.drawCenteredString(this.font, Component.literal(title), this.width / 2, 50, 0xFFFFFF);
        String place = NowPlaying.place();
        String time = NowPlaying.timeLine() + (place.isEmpty() ? "" : "   " + place);
        graphics.drawCenteredString(this.font, Component.literal(time), this.width / 2, 66, 0xA0D0A0);
        graphics.drawCenteredString(
                this.font,
                Component.literal(stateLabel(NowPlaying.playback()) + "   громкость " + PersonalVolume.label()),
                this.width / 2,
                78,
                0xE0E0E0
        );
        if (playButton != null) {
            playButton.setMessage(Component.literal(playLabel(NowPlaying.playback())));
        }
        int lineY = 160;
        for (var line : this.font.split(Component.literal(NowPlaying.status()), this.width - 40)) {
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

    private Button button(int x, int y, int width, String label, Runnable action) {
        return Button.builder(Component.literal(label), button -> action.run())
                .bounds(x, y, width, 20)
                .build();
    }

    private static String playLabel(PlaybackState state) {
        if (state == PlaybackState.PLAYING) {
            return "Пауза";
        }
        if (state == PlaybackState.PAUSED) {
            return "Дальше";
        }
        return "Играть";
    }

    private static String stateLabel(PlaybackState state) {
        return switch (state) {
            case PLAYING -> "играет";
            case PAUSED -> "пауза";
            case STOPPED -> "остановлено";
            case FAILED -> "ошибка";
            case IDLE -> "ожидание";
        };
    }
}
