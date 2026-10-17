package dev.yamod.yandexmusic.client.ui;

import dev.yamod.yandexmusic.audio.PlaybackState;
import dev.yamod.yandexmusic.client.CoverArt;
import dev.yamod.yandexmusic.client.LibraryView;
import dev.yamod.yandexmusic.client.NowPlaying;
import dev.yamod.yandexmusic.client.PersonalVolume;
import dev.yamod.yandexmusic.music.PlaybackTime;
import dev.yamod.yandexmusic.music.RepeatMode;
import dev.yamod.yandexmusic.music.model.TrackMetadata;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно плеера: боковое меню, сетка и нижняя панель. Закрытие не останавливает звук.
 */
public final class PrototypeScreen extends Screen {
    private enum Section {
        HOME, WAVE, COLLECTION, LIKES, ALBUMS, ARTISTS, SEARCH, PLAYLISTS
    }

    private static Section section = Section.HOME;
    private static boolean settingsOpen;
    private static boolean foldersOpen = true;
    private static boolean likesRequested;

    private final List<Hit> hits = new ArrayList<>();
    private final int[] scrolls = new int[Section.values().length];
    private EditBox search;
    private Frame frame;
    private boolean draggingSeek;
    private boolean draggingVolume;
    private double seekHold = -1;

    public PrototypeScreen() {
        super(Component.literal("Yandex Music"));
    }

    @Override
    protected void init() {
        search = new EditBox(this.font, 0, 0, 120, 14, Component.literal(PlayerStyle.text("Поиск", "Search")));
        search.setBordered(false);
        search.setMaxLength(120);
        search.setValue(LibraryView.query());
        search.setResponder(LibraryView::query);
        search.setSuggestion(PlayerStyle.text("Поиск", "Search"));
        addRenderableWidget(search);
        if (!likesRequested) {
            likesRequested = true;
            NowPlaying.showLikes();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        frame = Frame.of(this.width, this.height);
        search.setX(frame.searchX + 4);
        search.setY(frame.searchY + 3);
        search.setWidth(Math.max(40, frame.searchW - 8));
        search.setTextColor(PlayerStyle.ink());
        search.setSuggestion(search.getValue().isEmpty() ? PlayerStyle.text("Поиск", "Search") : "");
        this.renderBackground(graphics);
        graphics.fill(frame.x + 3, frame.y + 4, frame.x + frame.w + 3, frame.y + frame.h + 4, 0x66000000);
        graphics.fill(frame.x, frame.y, frame.x + frame.w, frame.y + frame.h, PlayerStyle.window());
        graphics.fill(frame.x, frame.y, frame.x + frame.side, frame.y + frame.h - frame.bar, PlayerStyle.sidebar());
        graphics.fill(frame.x + frame.side, frame.y, frame.x + frame.side + 1, frame.y + frame.h - frame.bar, PlayerStyle.line());
        drawBrand(graphics);
        drawNav(graphics, mouseX, mouseY);
        drawHeader(graphics);
        graphics.enableScissor(frame.contentX, frame.gridTop, frame.contentX + frame.contentW, frame.gridBottom);
        drawBody(graphics, mouseX, mouseY);
        graphics.disableScissor();
        drawBar(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (settingsOpen) {
            drawSettings(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || frame == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        Hit settingsHit = find("settings");
        if (settingsHit != null && settingsHit.contains(mouseX, mouseY)) {
            settingsOpen = !settingsOpen;
            return true;
        }
        if (settingsOpen && inside(frame.settingsX, frame.settingsY, frame.settingsW, frame.settingsH, mouseX, mouseY)) {
            handleSettings(mouseX, mouseY);
            return true;
        }
        if (settingsOpen) {
            settingsOpen = false;
        }
        Hit hit = findAt(mouseX, mouseY);
        if (hit == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (hit.id.equals("seek")) {
            draggingSeek = true;
            seekHold = fraction(mouseX, frame.seekX, frame.seekW);
            return true;
        }
        if (hit.id.equals("volume")) {
            draggingVolume = true;
            NowPlaying.setVolume((float) fraction(mouseX, frame.volX, frame.volW));
            return true;
        }
        activate(hit.id);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSeek && frame != null) {
            seekHold = fraction(mouseX, frame.seekX, frame.seekW);
            return true;
        }
        if (draggingVolume && frame != null) {
            NowPlaying.setVolume((float) fraction(mouseX, frame.volX, frame.volW));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSeek) {
            draggingSeek = false;
            if (seekHold >= 0) {
                NowPlaying.seekFraction(seekHold);
            }
            seekHold = -1;
            return true;
        }
        draggingVolume = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (frame != null && mouseX >= frame.contentX && mouseY >= frame.gridTop && mouseY < frame.gridBottom) {
            scrolls[section.ordinal()] = Math.max(0, scrolls[section.ordinal()] - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search != null && search.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            runSearch();
            return true;
        }
        if (search == null || !search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_SPACE) {
                NowPlaying.playOrResume();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                NowPlaying.next();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                NowPlaying.previous();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void activate(String id) {
        switch (id) {
            case "home" -> section = Section.HOME;
            case "wave" -> section = Section.WAVE;
            case "collection" -> section = Section.COLLECTION;
            case "likes" -> {
                section = Section.LIKES;
                NowPlaying.showLikes();
            }
            case "albums" -> section = Section.ALBUMS;
            case "artists" -> section = Section.ARTISTS;
            case "search-nav" -> section = Section.SEARCH;
            case "playlists" -> {
                section = Section.PLAYLISTS;
                NowPlaying.showPlaylists();
            }
            case "folders" -> foldersOpen = !foldersOpen;
            case "play-wave" -> {
                section = Section.WAVE;
                NowPlaying.playWave();
            }
            case "go-likes" -> {
                section = Section.LIKES;
                NowPlaying.showLikes();
            }
            case "go-playlists" -> {
                section = Section.PLAYLISTS;
                NowPlaying.showPlaylists();
            }
            case "go-search" -> section = Section.SEARCH;
            case "pause" -> NowPlaying.playOrResume();
            case "prev" -> NowPlaying.previous();
            case "next" -> NowPlaying.next();
            case "shuffle" -> NowPlaying.toggleShuffle();
            case "repeat" -> NowPlaying.cycleRepeat();
            case "like" -> NowPlaying.toggleLike();
            default -> {
                if (id.startsWith("like:")) {
                    NowPlaying.playAt(NowPlaying.likes(), number(id), false);
                } else if (id.startsWith("wave:")) {
                    NowPlaying.playAt(NowPlaying.waveTracks(), number(id), true);
                } else if (id.startsWith("listed:")) {
                    NowPlaying.playAt(NowPlaying.listedTracks(), number(id), false);
                } else if (id.startsWith("row:")) {
                    NowPlaying.openRow(number(id));
                }
            }
        }
    }

    private void handleSettings(double mouseX, double mouseY) {
        Hit hit = findAt(mouseX, mouseY);
        if (hit == null) {
            return;
        }
        switch (hit.id) {
            case "lang-en" -> PlayerStyle.english(true);
            case "lang-ru" -> PlayerStyle.english(false);
            case "theme-light" -> PlayerStyle.theme(PlayerStyle.Theme.LIGHT);
            case "theme-dark" -> PlayerStyle.theme(PlayerStyle.Theme.DARK);
            default -> {
                if (hit.id.startsWith("accent:")) {
                    int index = number(hit.id);
                    if (index >= 0 && index < PlayerStyle.ACCENTS.length) {
                        PlayerStyle.accent(PlayerStyle.ACCENTS[index]);
                    }
                }
            }
        }
    }

    private void runSearch() {
        LibraryView.query(search.getValue());
        LibraryView.resetScroll();
        section = Section.SEARCH;
        NowPlaying.search();
        search.setFocused(false);
    }

    private void drawBrand(GuiGraphics graphics) {
        int cx = frame.x + 16;
        int cy = frame.y + 16;
        disc(graphics, cx, cy, 6, 0xFFFFCC00);
        graphics.fill(cx - 1, cy - 2, cx + 1, cy + 3, 0xFFE10600);
        graphics.drawString(this.font, "Yandex Music", frame.x + 26, frame.y + 12, PlayerStyle.ink());
    }

    private void drawNav(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = frame.y + 34;
        y = nav(graphics, mouseX, mouseY, y, "home", Section.HOME, PlayerStyle.text("Главная", "Home"), Icon.HOME);
        y = nav(graphics, mouseX, mouseY, y, "wave", Section.WAVE, PlayerStyle.text("Моя волна", "My wave"), Icon.WAVE);
        y = nav(graphics, mouseX, mouseY, y, "collection", Section.COLLECTION, PlayerStyle.text("Моя коллекция", "Collection"), Icon.STACK);
        y += 8;
        graphics.drawString(this.font, PlayerStyle.text("Плейлисты", "Playlists"), frame.x + 12, y, PlayerStyle.muted());
        hit(frame.x + 8, y - 2, frame.side - 16, 12, "folders");
        y += 14;
        if (foldersOpen) {
            y = nav(graphics, mouseX, mouseY, y, "likes", Section.LIKES, PlayerStyle.text("Любимые треки", "Liked"), Icon.HEART);
            y = nav(graphics, mouseX, mouseY, y, "albums", Section.ALBUMS, PlayerStyle.text("Альбомы", "Albums"), Icon.DISC);
            y = nav(graphics, mouseX, mouseY, y, "artists", Section.ARTISTS, PlayerStyle.text("Исполнители", "Artists"), Icon.PERSON);
        }
        y = nav(graphics, mouseX, mouseY, y, "search-nav", Section.SEARCH, PlayerStyle.text("Поиск", "Search"), Icon.SEARCH);
        int bottom = frame.y + frame.h - frame.bar - 18;
        navAt(graphics, mouseX, mouseY, bottom, "settings", false, PlayerStyle.text("Настройки", "Settings"), Icon.GEAR);
    }

    private int nav(GuiGraphics graphics, int mouseX, int mouseY, int y, String id, Section target, String label, Icon icon) {
        navAt(graphics, mouseX, mouseY, y, id, section == target, label, icon);
        return y + 16;
    }

    private void navAt(GuiGraphics graphics, int mouseX, int mouseY, int y, String id, boolean selected, String label, Icon icon) {
        int x = frame.x + 6;
        int w = frame.side - 12;
        boolean hover = inside(x, y - 2, w, 15, mouseX, mouseY);
        if (selected) {
            graphics.fill(x, y - 3, x + w, y + 11, PlayerStyle.accent());
        } else if (hover) {
            graphics.fill(x, y - 3, x + w, y + 11, PlayerStyle.cardHover());
        }
        int color = selected ? 0xFFFFFF : PlayerStyle.ink();
        icon(graphics, icon, x + 3, y - 1, color);
        graphics.drawString(this.font, fit(label, w - 18), x + 14, y, color);
        hit(x, y - 3, w, 14, id);
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().scale(1.4F, 1.4F, 1.0F);
        float inv = 1.0F / 1.4F;
        graphics.drawString(this.font, title(), (int) (frame.contentX * inv), (int) ((frame.contentY + 2) * inv), PlayerStyle.ink());
        graphics.pose().popPose();
        graphics.fill(frame.searchX, frame.searchY, frame.searchX + frame.searchW, frame.searchY + 16, PlayerStyle.field());
        String status = fit(NowPlaying.status(), frame.contentW);
        int color = NowPlaying.playback() == PlaybackState.FAILED ? 0xFFFF6B6B : PlayerStyle.muted();
        graphics.drawString(this.font, status, frame.contentX, frame.contentY + 18, color);
    }

    private void drawBody(GuiGraphics graphics, int mouseX, int mouseY) {
        switch (section) {
            case HOME, LIKES -> drawTracks(graphics, mouseX, mouseY, NowPlaying.likes(), "like:", frame.gridTop);
            case WAVE -> {
                drawHero(graphics, mouseX, mouseY);
                drawTracks(graphics, mouseX, mouseY, NowPlaying.waveTracks(), "wave:", frame.gridTop + 44);
            }
            case COLLECTION -> drawHub(graphics, mouseX, mouseY);
            case PLAYLISTS -> drawRows(graphics, mouseX, mouseY, LibraryView.rows(), frame.gridTop);
            case ALBUMS -> drawRows(graphics, mouseX, mouseY, filter(LibraryView.Kind.ALBUM), frame.gridTop);
            case ARTISTS -> drawRows(graphics, mouseX, mouseY, filter(LibraryView.Kind.ARTIST), frame.gridTop);
            case SEARCH -> {
                int top = frame.gridTop;
                if (!NowPlaying.listedTracks().isEmpty()) {
                    top = drawTracks(graphics, mouseX, mouseY, NowPlaying.listedTracks(), "listed:", frame.gridTop);
                }
                drawRows(graphics, mouseX, mouseY, filterOthers(), top + 8);
            }
        }
    }

    private void drawHero(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = frame.contentX;
        int y = frame.gridTop;
        int w = Math.min(220, frame.contentW);
        boolean hover = inside(x, y, w, 36, mouseX, mouseY);
        graphics.fill(x, y, x + w, y + 36, hover ? PlayerStyle.cardHover() : PlayerStyle.card());
        graphics.fill(x, y, x + 3, y + 36, PlayerStyle.accent());
        graphics.drawString(this.font, PlayerStyle.text("Слушать волну", "Play My Wave"), x + 10, y + 8, PlayerStyle.ink());
        graphics.drawString(this.font, PlayerStyle.text("Персональная подборка", "Personal radio"), x + 10, y + 20, PlayerStyle.muted());
        hit(x, y, w, 36, "play-wave");
    }

    private void drawHub(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = frame.gridTop;
        y = hub(graphics, mouseX, mouseY, y, "go-likes", PlayerStyle.text("Любимые треки", "Liked tracks"), PlayerStyle.text("То, что вы отметили", "Tracks you marked"));
        y = hub(graphics, mouseX, mouseY, y, "go-playlists", PlayerStyle.text("Плейлисты", "Playlists"), PlayerStyle.text("Списки вашего аккаунта", "Lists on your account"));
        hub(graphics, mouseX, mouseY, y, "go-search", PlayerStyle.text("Поиск", "Search"), PlayerStyle.text("Треки, альбомы, исполнители", "Tracks, albums, artists"));
    }

    private int hub(GuiGraphics graphics, int mouseX, int mouseY, int y, String id, String title, String subtitle) {
        int x = frame.contentX;
        int w = Math.min(260, frame.contentW);
        boolean hover = inside(x, y, w, 36, mouseX, mouseY);
        graphics.fill(x, y, x + w, y + 36, hover ? PlayerStyle.cardHover() : PlayerStyle.card());
        graphics.drawString(this.font, title, x + 8, y + 8, PlayerStyle.ink());
        graphics.drawString(this.font, subtitle, x + 8, y + 20, PlayerStyle.muted());
        hit(x, y, w, 36, id);
        return y + 42;
    }

    private int drawTracks(GuiGraphics graphics, int mouseX, int mouseY, List<TrackMetadata> tracks, String prefix, int top) {
        if (tracks.isEmpty()) {
            String message = section == Section.HOME || section == Section.LIKES
                    ? PlayerStyle.text("Пока нет любимых треков.", "No liked tracks yet.")
                    : PlayerStyle.text("Здесь пока пусто.", "Nothing here yet.");
            graphics.drawString(this.font, message, frame.contentX, top, PlayerStyle.muted());
            return top + 16;
        }
        int gap = 8;
        int cols = Math.max(2, frame.contentW / 86);
        int cardW = Math.min(78, (frame.contentW - gap * (cols - 1)) / cols);
        int cardH = cardW + 24;
        int rowCount = (tracks.size() + cols - 1) / cols;
        int visible = Math.max(1, (frame.gridBottom - top) / (cardH + gap));
        int scroll = Math.min(scrolls[section.ordinal()], Math.max(0, rowCount - visible));
        scrolls[section.ordinal()] = scroll;
        int originY = top - scroll * (cardH + gap);
        for (int i = 0; i < tracks.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = frame.contentX + col * (cardW + gap);
            int y = originY + row * (cardH + gap);
            if (y > frame.gridBottom || y + cardH < frame.gridTop) {
                continue;
            }
            TrackMetadata track = tracks.get(i);
            boolean hover = inside(x, y, cardW, cardH, mouseX, mouseY);
            if (hover) {
                graphics.fill(x - 2, y - 2, x + cardW + 2, y + cardH, PlayerStyle.cardHover());
            }
            drawCover(graphics, track.coverUri(), track.title(), x, y, cardW);
            graphics.drawString(this.font, fit(track.title(), cardW), x, y + cardW + 3, PlayerStyle.ink());
            graphics.drawString(this.font, fit(track.artists(), cardW), x, y + cardW + 13, PlayerStyle.muted());
            if (y + cardH > frame.gridTop && y < frame.gridBottom) {
                hit(x, y, cardW, cardH, prefix + i);
            }
        }
        return top + rowCount * (cardH + gap);
    }

    private void drawRows(GuiGraphics graphics, int mouseX, int mouseY, List<LibraryView.Row> rows, int top) {
        if (rows.isEmpty()) {
            graphics.drawString(
                    this.font,
                    PlayerStyle.text("Ничего нет. Найдите через поиск сверху.", "Nothing yet. Use the search above."),
                    frame.contentX,
                    top,
                    PlayerStyle.muted()
            );
            return;
        }
        int y = top - scrolls[section.ordinal()] * 22;
        int index = 0;
        List<LibraryView.Row> all = LibraryView.rows();
        for (LibraryView.Row row : rows) {
            int real = all.indexOf(row);
            if (real < 0) {
                real = index;
            }
            if (y + 20 >= frame.gridTop && y < frame.gridBottom) {
                boolean hover = inside(frame.contentX, y, frame.contentW - 4, 20, mouseX, mouseY);
                graphics.fill(frame.contentX, y, frame.contentX + frame.contentW - 4, y + 20, hover ? PlayerStyle.cardHover() : PlayerStyle.card());
                graphics.drawString(this.font, fit(row.label(), frame.contentW - 16), frame.contentX + 6, y + 6, PlayerStyle.ink());
                hit(frame.contentX, y, frame.contentW - 4, 20, "row:" + real);
            }
            y += 22;
            index++;
        }
    }

    private void drawCover(GuiGraphics graphics, String coverUri, String seed, int x, int y, int size) {
        var location = CoverArt.location(coverUri);
        if (location != null) {
            graphics.blit(
                    location,
                    x,
                    y,
                    size,
                    size,
                    0,
                    0,
                    CoverArt.width(coverUri),
                    CoverArt.height(coverUri),
                    CoverArt.width(coverUri),
                    CoverArt.height(coverUri)
            );
            return;
        }
        int color = 0xFF303030 | (Math.floorMod(seed == null ? 0 : seed.hashCode(), 0xFFFFFF) & 0x3F3F3F);
        graphics.fill(x, y, x + size, y + size, 0xFF2A2A2A);
        graphics.fill(x + 4, y + 4, x + size - 4, y + size - 4, color | 0xFF000000);
        CoverArt.request(coverUri);
    }

    private void drawBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = frame.y + frame.h - frame.bar;
        graphics.fill(frame.x, y, frame.x + frame.w, frame.y + frame.h, PlayerStyle.bar());
        graphics.fill(frame.x, y, frame.x + frame.w, y + 1, PlayerStyle.line());
        TrackMetadata track = NowPlaying.track();
        int cover = 32;
        int cx = frame.x + 8;
        int cy = y + 8;
        if (track != null) {
            drawCover(graphics, track.coverUri(), track.title(), cx, cy, cover);
            graphics.drawString(this.font, fit(track.title(), 90), cx + 38, cy + 4, PlayerStyle.ink());
            graphics.drawString(this.font, fit(track.artists(), 90), cx + 38, cy + 16, PlayerStyle.muted());
        } else {
            graphics.fill(cx, cy, cx + cover, cy + cover, PlayerStyle.field());
            graphics.drawString(this.font, PlayerStyle.text("Ничего не играет", "Nothing playing"), cx + 38, cy + 12, PlayerStyle.muted());
        }
        int starX = frame.x + 148;
        icon(graphics, Icon.STAR, starX, y + 18, NowPlaying.currentLiked() ? PlayerStyle.accent() : PlayerStyle.muted());
        hit(starX - 3, y + 14, 14, 14, "like");

        int center = frame.x + frame.w / 2;
        boolean shuffleOn = NowPlaying.shuffled();
        RepeatMode repeat = NowPlaying.repeat();
        control(graphics, mouseX, mouseY, center - 52, y + 10, "shuffle", Icon.SHUFFLE, shuffleOn ? PlayerStyle.accent() : PlayerStyle.ink());
        control(graphics, mouseX, mouseY, center - 34, y + 10, "prev", Icon.PREV, PlayerStyle.ink());
        drawPlay(graphics, center - 8, y + 8);
        hit(center - 10, y + 6, 20, 20, "pause");
        control(graphics, mouseX, mouseY, center + 16, y + 10, "next", Icon.NEXT, PlayerStyle.ink());
        int repeatColor = repeat == RepeatMode.OFF ? PlayerStyle.ink() : PlayerStyle.accent();
        control(graphics, mouseX, mouseY, center + 34, y + 10, "repeat", Icon.REPEAT, repeatColor);

        long duration = NowPlaying.durationMillis();
        double shown = seekHold >= 0 ? seekHold : NowPlaying.fraction();
        long position = duration <= 0 ? 0 : (long) (shown * duration);
        String left = PlaybackTime.clock(position);
        String right = PlaybackTime.clock(duration);
        int timeY = y + frame.bar - 14;
        frame.seekW = Math.min(220, Math.max(80, frame.w / 4));
        frame.seekX = center - frame.seekW / 2;
        frame.seekY = timeY + 3;
        graphics.drawString(this.font, left, frame.seekX - 28, timeY, PlayerStyle.muted());
        graphics.drawString(this.font, right, frame.seekX + frame.seekW + 6, timeY, PlayerStyle.muted());
        drawSlider(graphics, frame.seekX, frame.seekY, frame.seekW, shown, 0xFFFFFFFF);
        hit(frame.seekX, frame.seekY - 3, frame.seekW, 8, "seek");

        frame.volX = frame.x + frame.w - 78;
        frame.volY = y + 20;
        frame.volW = 62;
        icon(graphics, Icon.VOLUME, frame.volX - 14, frame.volY - 2, PlayerStyle.muted());
        drawSlider(graphics, frame.volX, frame.volY, frame.volW, PersonalVolume.get(), PlayerStyle.accent());
        hit(frame.volX, frame.volY - 3, frame.volW, 8, "volume");
    }

    private void drawPlay(GuiGraphics graphics, int x, int y) {
        disc(graphics, x + 8, y + 8, 8, PlayerStyle.ink());
        disc(graphics, x + 8, y + 8, 6, PlayerStyle.bar());
        if (NowPlaying.playback() == PlaybackState.PLAYING) {
            graphics.fill(x + 6, y + 5, x + 8, y + 12, PlayerStyle.ink());
            graphics.fill(x + 10, y + 5, x + 12, y + 12, PlayerStyle.ink());
        } else {
            graphics.fill(x + 7, y + 5, x + 8, y + 6, PlayerStyle.ink());
            graphics.fill(x + 7, y + 6, x + 9, y + 7, PlayerStyle.ink());
            graphics.fill(x + 7, y + 7, x + 11, y + 8, PlayerStyle.ink());
            graphics.fill(x + 7, y + 8, x + 11, y + 9, PlayerStyle.ink());
            graphics.fill(x + 7, y + 9, x + 9, y + 10, PlayerStyle.ink());
            graphics.fill(x + 7, y + 10, x + 8, y + 11, PlayerStyle.ink());
        }
    }

    private void control(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String id, Icon icon, int color) {
        if (inside(x - 2, y - 2, 14, 14, mouseX, mouseY)) {
            graphics.fill(x - 2, y - 2, x + 12, y + 12, PlayerStyle.cardHover());
        }
        icon(graphics, icon, x, y, color);
        hit(x - 2, y - 2, 14, 14, id);
    }

    private void drawSlider(GuiGraphics graphics, int x, int y, int width, double fraction, int fill) {
        double shown = Math.max(0, Math.min(1, fraction));
        int done = (int) (width * shown);
        graphics.fill(x, y, x + width, y + 2, PlayerStyle.track());
        graphics.fill(x, y, x + done, y + 2, fill);
        graphics.fill(x + done - 2, y - 2, x + done + 2, y + 4, fill);
    }

    private void drawSettings(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = frame.settingsX;
        int y = frame.settingsY;
        int w = frame.settingsW;
        int h = frame.settingsH;
        graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
        graphics.fill(x, y, x + w, y + h, 0xFFC6C6C6);
        graphics.drawCenteredString(this.font, PlayerStyle.text("Внешний вид", "Appearance"), x + w / 2, y + 6, 0xFF202020);
        graphics.drawString(this.font, PlayerStyle.text("Язык", "Language"), x + 8, y + 22, 0xFF202020);
        choice(graphics, mouseX, mouseY, x + 8, y + 34, 70, "English", "lang-en", PlayerStyle.english());
        choice(graphics, mouseX, mouseY, x + 82, y + 34, 70, "Русский", "lang-ru", !PlayerStyle.english());
        graphics.drawString(this.font, PlayerStyle.text("Тема", "Theme"), x + 8, y + 58, 0xFF202020);
        choice(graphics, mouseX, mouseY, x + 8, y + 70, 70, "Light", "theme-light", PlayerStyle.theme() == PlayerStyle.Theme.LIGHT);
        choice(graphics, mouseX, mouseY, x + 82, y + 70, 70, "Dark", "theme-dark", PlayerStyle.theme() == PlayerStyle.Theme.DARK);
        for (int i = 0; i < PlayerStyle.ACCENTS.length; i++) {
            int sx = x + 8 + i * 24;
            int sy = y + 96;
            graphics.fill(sx - 1, sy - 1, sx + 15, sy + 15, PlayerStyle.accent() == PlayerStyle.ACCENTS[i] ? 0xFFFFFFFF : 0xFF202020);
            graphics.fill(sx, sy, sx + 14, sy + 14, PlayerStyle.ACCENTS[i]);
            hit(sx, sy, 14, 14, "accent:" + i);
        }
    }

    private void choice(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, String label, String id, boolean selected) {
        int bg = selected ? 0xFF2E2E2E : 0xFF8E8E8E;
        if (!selected && inside(x, y, w, 16, mouseX, mouseY)) {
            bg = 0xFF6E6E6E;
        }
        graphics.fill(x, y, x + w, y + 16, bg);
        graphics.drawCenteredString(this.font, label, x + w / 2, y + 4, 0xFFFFFFFF);
        hit(x, y, w, 16, id);
    }

    private String title() {
        return switch (section) {
            case HOME -> PlayerStyle.text("Главная", "Home");
            case WAVE -> PlayerStyle.text("Моя волна", "My wave");
            case COLLECTION -> PlayerStyle.text("Моя коллекция", "My collection");
            case LIKES -> PlayerStyle.text("Любимые треки", "Liked tracks");
            case ALBUMS -> PlayerStyle.text("Альбомы", "Albums");
            case ARTISTS -> PlayerStyle.text("Исполнители", "Artists");
            case SEARCH -> PlayerStyle.text("Поиск", "Search");
            case PLAYLISTS -> PlayerStyle.text("Плейлисты", "Playlists");
        };
    }

    private List<LibraryView.Row> filter(LibraryView.Kind kind) {
        List<LibraryView.Row> rows = new ArrayList<>();
        for (LibraryView.Row row : LibraryView.rows()) {
            if (row.kind() == kind) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<LibraryView.Row> filterOthers() {
        List<LibraryView.Row> rows = new ArrayList<>();
        for (LibraryView.Row row : LibraryView.rows()) {
            if (row.kind() != LibraryView.Kind.TRACK) {
                rows.add(row);
            }
        }
        return rows;
    }

    private void icon(GuiGraphics graphics, Icon kind, int x, int y, int color) {
        switch (kind) {
            case HOME -> {
                graphics.fill(x + 3, y, x + 5, y + 1, color);
                graphics.fill(x + 1, y + 2, x + 7, y + 3, color);
                graphics.fill(x + 2, y + 3, x + 6, y + 7, color);
            }
            case WAVE -> {
                graphics.fill(x + 1, y + 3, x + 2, y + 5, color);
                graphics.fill(x + 3, y + 2, x + 4, y + 6, color);
                graphics.fill(x + 5, y + 1, x + 6, y + 7, color);
            }
            case STACK -> {
                graphics.fill(x, y + 1, x + 7, y + 2, color);
                graphics.fill(x, y + 3, x + 7, y + 4, color);
                graphics.fill(x, y + 5, x + 7, y + 6, color);
            }
            case HEART -> {
                graphics.fill(x + 1, y + 1, x + 3, y + 3, color);
                graphics.fill(x + 4, y + 1, x + 6, y + 3, color);
                graphics.fill(x + 1, y + 3, x + 6, y + 5, color);
                graphics.fill(x + 2, y + 5, x + 5, y + 6, color);
                graphics.fill(x + 3, y + 6, x + 4, y + 7, color);
            }
            case DISC -> disc(graphics, x + 3, y + 3, 3, color);
            case PERSON -> {
                disc(graphics, x + 3, y + 2, 2, color);
                graphics.fill(x + 1, y + 5, x + 6, y + 7, color);
            }
            case SEARCH -> {
                disc(graphics, x + 3, y + 3, 2, color);
                graphics.fill(x + 5, y + 5, x + 7, y + 7, color);
            }
            case GEAR -> disc(graphics, x + 3, y + 3, 2, color);
            case STAR -> {
                graphics.fill(x + 3, y, x + 5, y + 3, color);
                graphics.fill(x, y + 3, x + 8, y + 5, color);
                graphics.fill(x + 1, y + 5, x + 3, y + 8, color);
                graphics.fill(x + 5, y + 5, x + 7, y + 8, color);
            }
            case SHUFFLE -> {
                graphics.fill(x, y + 2, x + 5, y + 3, color);
                graphics.fill(x, y + 5, x + 5, y + 6, color);
                graphics.fill(x + 5, y + 1, x + 7, y + 3, color);
                graphics.fill(x + 5, y + 5, x + 7, y + 7, color);
            }
            case PREV -> {
                graphics.fill(x, y + 1, x + 2, y + 7, color);
                graphics.fill(x + 3, y + 3, x + 7, y + 5, color);
            }
            case NEXT -> {
                graphics.fill(x + 1, y + 3, x + 5, y + 5, color);
                graphics.fill(x + 6, y + 1, x + 8, y + 7, color);
            }
            case REPEAT -> {
                graphics.fill(x + 2, y + 1, x + 7, y + 2, color);
                graphics.fill(x + 1, y + 5, x + 6, y + 6, color);
                graphics.fill(x + 6, y + 1, x + 7, y + 4, color);
                graphics.fill(x + 1, y + 3, x + 2, y + 6, color);
            }
            case VOLUME -> {
                graphics.fill(x, y + 2, x + 2, y + 6, color);
                graphics.fill(x + 2, y + 1, x + 4, y + 7, color);
                graphics.fill(x + 5, y + 2, x + 6, y + 3, color);
                graphics.fill(x + 5, y + 5, x + 6, y + 6, color);
            }
            case PLAY -> {
            }
        }
    }

    private void disc(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.round(Math.sqrt(radius * radius - dy * dy));
            graphics.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }

    private void hit(int x, int y, int w, int h, String id) {
        hits.add(new Hit(x, y, w, h, id));
    }

    private Hit find(String id) {
        for (Hit hit : hits) {
            if (hit.id.equals(id)) {
                return hit;
            }
        }
        return null;
    }

    private Hit findAt(double mouseX, double mouseY) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(mouseX, mouseY)) {
                return hits.get(i);
            }
        }
        return null;
    }

    private static boolean inside(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    private static double fraction(double mouseX, int x, int width) {
        if (width <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, (mouseX - x) / width));
    }

    private static int number(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) {
            return -1;
        }
        try {
            return Integer.parseInt(id.substring(colon + 1));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String fit(String text, int width) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (this.font.width(text) <= width) {
            return text;
        }
        String value = text;
        while (value.length() > 1 && this.font.width(value + "...") > width) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
    }

    private enum Icon {
        HOME, WAVE, STACK, HEART, DISC, PERSON, SEARCH, GEAR, STAR, SHUFFLE, PREV, NEXT, REPEAT, VOLUME, PLAY
    }

    private record Hit(int x, int y, int w, int h, String id) {
        private boolean contains(double mouseX, double mouseY) {
            return inside(x, y, w, h, mouseX, mouseY);
        }
    }

    private static final class Frame {
        private int x;
        private int y;
        private int w;
        private int h;
        private int side;
        private int bar;
        private int contentX;
        private int contentY;
        private int contentW;
        private int gridTop;
        private int gridBottom;
        private int searchX;
        private int searchY;
        private int searchW;
        private int seekX;
        private int seekY;
        private int seekW;
        private int volX;
        private int volY;
        private int volW;
        private int settingsX;
        private int settingsY;
        private int settingsW;
        private int settingsH;

        private static Frame of(int screenW, int screenH) {
            Frame frame = new Frame();
            int marginX = Math.max(8, screenW / 18);
            int marginY = Math.max(6, screenH / 14);
            frame.x = marginX;
            frame.y = marginY;
            frame.w = screenW - marginX * 2;
            frame.h = screenH - marginY * 2;
            frame.side = Math.max(108, Math.min(150, frame.w / 4));
            frame.bar = 52;
            frame.contentX = frame.x + frame.side + 10;
            frame.contentY = frame.y + 8;
            frame.contentW = frame.w - frame.side - 20;
            frame.gridTop = frame.contentY + 34;
            frame.gridBottom = frame.y + frame.h - frame.bar - 4;
            frame.searchW = Math.min(150, Math.max(70, frame.contentW / 3));
            frame.searchX = frame.contentX + frame.contentW - frame.searchW;
            frame.searchY = frame.contentY;
            frame.settingsW = 168;
            frame.settingsH = 118;
            frame.settingsX = frame.x + frame.w - frame.settingsW - 10;
            frame.settingsY = frame.y + 28;
            return frame;
        }
    }
}
