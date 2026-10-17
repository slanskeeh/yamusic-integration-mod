package dev.yamod.yandexmusic.client.ui;

import dev.yamod.yandexmusic.audio.PlaybackState;
import dev.yamod.yandexmusic.client.CoverArt;
import dev.yamod.yandexmusic.client.LibraryView;
import dev.yamod.yandexmusic.client.NowPlaying;
import dev.yamod.yandexmusic.client.PersonalVolume;
import dev.yamod.yandexmusic.music.model.TrackMetadata;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Личный плеер и коллекция. Закрытие экрана не останавливает звук.
 */
public final class PrototypeScreen extends Screen {
    private static final int VISIBLE_ROWS = 6;

    private Button playButton;
    private Button repeatButton;
    private Button shuffleButton;
    private Button likeButton;
    private PositionSlider slider;
    private EditBox queryBox;
    private int builtVersion = -1;

    public PrototypeScreen() {
        super(Component.literal("Яндекс Музыка"));
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        addTab(center, 18, LibraryView.Tab.PLAYER, "Плеер");
        addTab(center, 18, LibraryView.Tab.LIKES, "Любимые");
        addTab(center, 18, LibraryView.Tab.SEARCH, "Поиск");
        addTab(center, 18, LibraryView.Tab.PLAYLISTS, "Плейлисты");
        if (LibraryView.tab() == LibraryView.Tab.PLAYER) {
            initPlayer(center);
        } else {
            initCollection(center);
        }
        builtVersion = LibraryView.version();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (LibraryView.tab() != LibraryView.Tab.PLAYER && builtVersion != LibraryView.version()) {
            if (queryBox != null) {
                LibraryView.query(queryBox.getValue());
            }
            this.rebuildWidgets();
        }
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
        if (LibraryView.tab() == LibraryView.Tab.PLAYER) {
            renderPlayer(graphics);
        } else {
            graphics.drawCenteredString(this.font, Component.literal(LibraryView.heading()), this.width / 2, 42, 0xC8C8C8);
        }
        int lineY = LibraryView.tab() == LibraryView.Tab.PLAYER ? this.height - 46 : this.height - 36;
        for (var line : this.font.split(Component.literal(NowPlaying.status()), this.width - 24)) {
            graphics.drawString(this.font, line, 12, lineY, 0xE0E0E0);
            lineY += 10;
            if (lineY > this.height - 8) {
                break;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (LibraryView.tab() == LibraryView.Tab.PLAYER || delta == 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        LibraryView.scrollBy(delta > 0 ? -1 : 1);
        this.rebuildWidgets();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void initPlayer(int center) {
        int y = 112;
        addRenderableWidget(button(center - 155, y, 100, "Предыдущий", NowPlaying::previous));
        playButton = button(center - 50, y, 100, "Играть", NowPlaying::playOrResume);
        addRenderableWidget(playButton);
        addRenderableWidget(button(center + 55, y, 100, "Следующий", NowPlaying::next));
        addRenderableWidget(button(center - 155, y + 24, 100, "Тише", () -> NowPlaying.changeVolume(-0.1F)));
        addRenderableWidget(button(center + 55, y + 24, 100, "Громче", () -> NowPlaying.changeVolume(0.1F)));
        addRenderableWidget(button(center - 50, y + 24, 100, "Стоп", NowPlaying::stop));
        shuffleButton = button(center - 155, y + 48, 150, NowPlaying.shuffleLabel(), NowPlaying::toggleShuffle);
        addRenderableWidget(shuffleButton);
        repeatButton = button(center + 5, y + 48, 150, NowPlaying.repeatLabel(), NowPlaying::cycleRepeat);
        addRenderableWidget(repeatButton);
        likeButton = button(center - 155, y + 72, 150, NowPlaying.likeLabel(), NowPlaying::toggleLike);
        addRenderableWidget(likeButton);
        addRenderableWidget(button(center + 5, y + 72, 150, "Моя волна", NowPlaying::playWave));
        slider = new PositionSlider(center - 110, 90, 220, NowPlaying.fraction());
        addRenderableWidget(slider);
    }

    private void initCollection(int center) {
        int y = 56;
        if (LibraryView.tab() == LibraryView.Tab.SEARCH) {
            queryBox = new EditBox(this.font, center - 155, y, 230, 20, Component.literal("Запрос"));
            queryBox.setMaxLength(120);
            queryBox.setValue(LibraryView.query());
            queryBox.setResponder(LibraryView::query);
            addRenderableWidget(queryBox);
            addRenderableWidget(button(center + 80, y, 75, "Найти", () -> {
                LibraryView.query(queryBox.getValue());
                LibraryView.resetScroll();
                NowPlaying.search();
            }));
            y += 26;
        }
        List<LibraryView.Row> rows = LibraryView.rows();
        int maxScroll = Math.max(0, rows.size() - VISIBLE_ROWS);
        int scroll = Math.min(LibraryView.scroll(), maxScroll);
        int shown = Math.min(VISIBLE_ROWS, rows.size() - scroll);
        for (int i = 0; i < shown; i++) {
            int index = scroll + i;
            LibraryView.Row row = rows.get(index);
            addRenderableWidget(button(center - 155, y, 310, row.label(), () -> NowPlaying.openRow(index)));
            y += 22;
        }
        if (rows.size() > VISIBLE_ROWS) {
            addRenderableWidget(button(center - 155, y, 150, "Выше", () -> {
                LibraryView.scrollBy(-1);
                this.rebuildWidgets();
            }));
            addRenderableWidget(button(center + 5, y, 150, "Ниже", () -> {
                LibraryView.scrollBy(1);
                this.rebuildWidgets();
            }));
        }
    }

    private void renderPlayer(GuiGraphics graphics) {
        TrackMetadata track = NowPlaying.track();
        int textX = this.width / 2;
        if (CoverArt.ready()) {
            graphics.blit(
                    CoverArt.LOCATION,
                    this.width / 2 - 150,
                    44,
                    48,
                    48,
                    0,
                    0,
                    CoverArt.imageWidth(),
                    CoverArt.imageHeight(),
                    CoverArt.imageWidth(),
                    CoverArt.imageHeight()
            );
        }
        String artists = track == null ? "Нет трека" : track.artists();
        String title = track == null ? "Нажмите «Играть»" : track.title();
        graphics.drawCenteredString(this.font, Component.literal(artists), textX, 46, 0xC8C8C8);
        graphics.drawCenteredString(this.font, Component.literal(title), textX, 60, 0xFFFFFF);
        String place = NowPlaying.place();
        String time = NowPlaying.timeLine() + (place.isEmpty() ? "" : "   " + place);
        graphics.drawCenteredString(this.font, Component.literal(time), textX, 74, 0xA0D0A0);
        if (slider != null) {
            slider.sync(NowPlaying.fraction());
        }
        if (playButton != null) {
            playButton.setMessage(Component.literal(playLabel(NowPlaying.playback())));
        }
        if (repeatButton != null) {
            repeatButton.setMessage(Component.literal(NowPlaying.repeatLabel()));
        }
        if (shuffleButton != null) {
            shuffleButton.setMessage(Component.literal(NowPlaying.shuffleLabel()));
        }
        if (likeButton != null) {
            likeButton.setMessage(Component.literal(NowPlaying.likeLabel()));
        }
        graphics.drawCenteredString(
                this.font,
                Component.literal(stateLabel(NowPlaying.playback()) + "   громкость " + PersonalVolume.label()),
                textX,
                108,
                0xE0E0E0
        );
    }

    private void addTab(int center, int y, LibraryView.Tab tab, String label) {
        int order = tab.ordinal();
        int x = center - 160 + order * 80;
        String title = LibraryView.tab() == tab ? "[" + label + "]" : label;
        addRenderableWidget(button(x, y, 78, title, () -> {
            if (queryBox != null) {
                LibraryView.query(queryBox.getValue());
            }
            LibraryView.tab(tab);
            LibraryView.resetScroll();
            if (tab == LibraryView.Tab.LIKES) {
                NowPlaying.showLikes();
            } else if (tab == LibraryView.Tab.PLAYLISTS) {
                NowPlaying.showPlaylists();
            }
            this.rebuildWidgets();
        }));
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

    private static final class PositionSlider extends AbstractSliderButton {
        private boolean adjusting;

        private PositionSlider(int x, int y, int width, double value) {
            super(x, y, width, 20, Component.empty(), value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(NowPlaying.timeLine()));
        }

        @Override
        protected void applyValue() {
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            adjusting = true;
            super.onClick(mouseX, mouseY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean wasAdjusting = adjusting;
            double fraction = this.value;
            adjusting = false;
            boolean handled = super.mouseReleased(mouseX, mouseY, button);
            if (wasAdjusting) {
                NowPlaying.seekFraction(fraction);
            }
            return handled;
        }

        private void sync(double fraction) {
            if (adjusting) {
                return;
            }
            this.value = Mth.clamp(fraction, 0.0, 1.0);
            updateMessage();
        }
    }
}
