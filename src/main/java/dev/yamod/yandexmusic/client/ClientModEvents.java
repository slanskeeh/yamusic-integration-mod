package dev.yamod.yandexmusic.client;

import dev.yamod.yandexmusic.YandexMusicMod;
import dev.yamod.yandexmusic.client.ui.PrototypeScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
            while (PrototypeKeyMappings.OPEN_PROTOTYPE.consumeClick()) {
                if (minecraft.screen == null) {
                    minecraft.setScreen(new PrototypeScreen());
                }
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
