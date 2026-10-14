package cn.skysigns;

import cn.skysigns.command.SkySignsCommands;
import cn.skysigns.network.StationReportPayload;
import cn.skysigns.network.StationRefreshRequestPayload;
import cn.skysigns.network.WindVisualPayload;
import cn.skysigns.block.ObservationInstrumentBlock;
import cn.skysigns.block.entity.MeterMigration;
import cn.skysigns.registry.ModBlockEntities;
import cn.skysigns.registry.ModBlocks;
import cn.skysigns.registry.ModItems;
import cn.skysigns.weather.WindManager;
import cn.skysigns.weather.WindState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.WeakHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SkySigns implements ModInitializer {
    public static final String MOD_ID = "skysigns";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Map<ServerPlayerEntity, Long> LAST_STATION_REFRESH = new WeakHashMap<>();

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();
        MeterMigration.initialize();
        PayloadTypeRegistry.playS2C().register(StationReportPayload.ID, StationReportPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WindVisualPayload.ID, WindVisualPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StationRefreshRequestPayload.ID, StationRefreshRequestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StationRefreshRequestPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayerEntity player = context.player();
                    ServerWorld world = player.getEntityWorld();
                    BlockPos pos = BlockPos.fromLong(payload.stationPos());
                    if (player.squaredDistanceTo(Vec3d.ofCenter(pos)) > 64.0D
                            || !world.getBlockState(pos).isOf(ModBlocks.WEATHER_STATION)) {
                        return;
                    }
                    long now = world.getTime();
                    Long previous = LAST_STATION_REFRESH.get(player);
                    if (previous != null && now >= previous && now - previous < 10L) {
                        return;
                    }
                    LAST_STATION_REFRESH.put(player, now);
                    ObservationInstrumentBlock.sendStationReport(world, player, pos, false);
                })
        );
        SkySignsCommands.initialize();
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            // A frozen game clock must not advance wind or repeatedly send at the same tick % 20 == 0.
            if (!world.getTickManager().shouldTick()) return;
            WindManager.tick(world);
            if (world.getTime() % 20L == 0L && !world.getPlayers().isEmpty()) {
                WindState wind = WindManager.get(world);
                WindVisualPayload snapshot = new WindVisualPayload(wind.directionDegrees(), wind.strength());
                for (ServerPlayerEntity player : world.getPlayers()) {
                    if (ServerPlayNetworking.canSend(player, WindVisualPayload.ID)) {
                        ServerPlayNetworking.send(player, snapshot);
                    }
                }
            }
        });
        LOGGER.info("Sky Signs {} initialized", net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(MOD_ID).orElseThrow().getMetadata().getVersion().getFriendlyString());
    }
}
