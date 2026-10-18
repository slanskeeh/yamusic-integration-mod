package dev.yamod.yandexmusic.client;

import dev.yamod.yandexmusic.YandexMusicMod;
import dev.yamod.yandexmusic.client.ui.PlayerStyle;
import dev.yamod.yandexmusic.client.ui.PrototypeScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientModEvents {
    private ClientModEvents() {
    }

    @Mod.EventBusSubscriber(modid = YandexMusicMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(PrototypeKeyMappings.OPEN_PROTOTYPE);
            event.register(PrototypeKeyMappings.PLAY_PAUSE);
            event.register(PrototypeKeyMappings.PREVIOUS);
            event.register(PrototypeKeyMappings.NEXT);
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(PlayerStyle::load);
        }
    }

    @Mod.EventBusSubscriber(modid = YandexMusicMod.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            GameMusic.tick();
            NowPlaying.tick();
            while (PrototypeKeyMappings.OPEN_PROTOTYPE.consumeClick()) {
                if (minecraft.screen == null) {
                    minecraft.setScreen(new PrototypeScreen());
                }
            }
            while (PrototypeKeyMappings.PLAY_PAUSE.consumeClick()) {
                NowPlaying.playOrResume();
            }
            while (PrototypeKeyMappings.PREVIOUS.consumeClick()) {
                NowPlaying.previous();
            }
            while (PrototypeKeyMappings.NEXT.consumeClick()) {
                NowPlaying.next();
            }
        }

        @SubscribeEvent
        public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            PrototypeAudio.release();
        }

        @SubscribeEvent
        public static void onShutdown(GameShuttingDownEvent event) {
            PrototypeAudio.release();
            ClientBackground.shutdown();
        }
    }
}
