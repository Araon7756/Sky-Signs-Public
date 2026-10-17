package cn.skysigns.client;

import cn.skysigns.client.render.WeatherVaneBlockEntityRenderer;
import cn.skysigns.client.render.MeterBlockEntityRenderer;
import cn.skysigns.client.render.WindPrecipitationVisual;
import cn.skysigns.client.screen.WeatherStationScreen;
import cn.skysigns.client.screen.ClientSettingsScreen;
import cn.skysigns.client.config.ClientConfig;
import cn.skysigns.SkySigns;
import cn.skysigns.network.StationReportPayload;
import cn.skysigns.network.WindVisualPayload;
import cn.skysigns.registry.ModBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

public final class SkySignsClient implements ClientModInitializer {
    private static ClientConfig config;

    public static ClientConfig config() { return config; }

    @Override
    public void onInitializeClient() {
        config = new ClientConfig(FabricLoader.getInstance().getConfigDir().resolve("skysigns-client.properties"),
                message -> SkySigns.LOGGER.warn("{}", message));
        config.save();
        WindPrecipitationVisual.initialize();
        KeyBinding settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skysigns.settings", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(),
                KeyBinding.Category.create(Identifier.of("skysigns", "settings"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientWindState.INSTANCE.tick(client.world, client.getNetworkHandler(), client.isPaused());
            WindPrecipitationVisual.tick(client);
            while (settingsKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ClientSettingsScreen(null));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientWindState.INSTANCE.reset());
        BlockEntityRendererFactories.register(
                ModBlockEntities.WEATHER_VANE,
                WeatherVaneBlockEntityRenderer::new
        );
        BlockEntityRendererFactories.register(ModBlockEntities.METER, MeterBlockEntityRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(StationReportPayload.ID, (payload, context) -> {
            var session = ClientSession.capture(context.client());
            context.client().execute(() -> acceptStationReport(context.client(), session, payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(WindVisualPayload.ID, (payload, context) -> {
            var session = ClientSession.capture(context.client());
            context.client().execute(() -> {
                if (session.matches(context.client())) {
                    ClientWindState.INSTANCE.accept(session.world(), session.connection(),
                            payload.directionDegrees(), payload.strength());
                }
            });
        });
    }

    public static void acceptStationReport(MinecraftClient client, ClientSession session, StationReportPayload payload) {
        if (!session.matches(client)) return;
        if (client.currentScreen instanceof ClientSettingsScreen settings) {
            settings.acceptStationReport(payload);
        } else if (client.currentScreen instanceof WeatherStationScreen screen
                && screen.stationPos() == payload.stationPos() && screen.isValidParent(client)) {
            screen.updateReport(payload);
        } else if (payload.opensScreen()) {
            client.setScreen(new WeatherStationScreen(payload));
        }
    }
}
