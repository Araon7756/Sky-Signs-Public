package cn.skysigns.client;

import cn.skysigns.client.render.WeatherVaneBlockEntityRenderer;
import cn.skysigns.client.render.MeterBlockEntityRenderer;
import cn.skysigns.client.render.WindPrecipitationVisual;
import cn.skysigns.client.screen.WeatherStationScreen;
import cn.skysigns.network.StationReportPayload;
import cn.skysigns.network.WindVisualPayload;
import cn.skysigns.registry.ModBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SkySignsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WindPrecipitationVisual.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(WindPrecipitationVisual::tick);
        BlockEntityRendererFactories.register(
                ModBlockEntities.WEATHER_VANE,
                WeatherVaneBlockEntityRenderer::new
        );
        BlockEntityRendererFactories.register(ModBlockEntities.METER, MeterBlockEntityRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(StationReportPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().currentScreen instanceof WeatherStationScreen screen
                            && screen.stationPos() == payload.stationPos()) {
                        screen.updateReport(payload);
                    } else if (payload.opensScreen()) {
                        context.client().setScreen(new WeatherStationScreen(payload));
                    }
                })
        );
        ClientPlayNetworking.registerGlobalReceiver(WindVisualPayload.ID, (payload, context) ->
                context.client().execute(() -> WindPrecipitationVisual.accept(
                        context.client().world, payload.directionDegrees(), payload.strength()))
        );
    }
}
