package cn.skysigns.test;

import cn.skysigns.SkySigns;
import cn.skysigns.block.ObservationInstrumentBlock;
import cn.skysigns.block.entity.MeterBlockEntity;
import cn.skysigns.registry.ModBlocks;
import cn.skysigns.weather.AtmosphericReadings;
import cn.skysigns.weather.SkyWeatherType;
import cn.skysigns.weather.WeatherManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.InactivityFpsLimit;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Opt-in, disposable singleplayer benchmark. No production instrumentation or performance pass threshold. */
@SuppressWarnings("UnstableApiUsage")
final class HygrometerPerformanceTest {
    private static final int WARMUP_TICKS = 80;
    private static final int SAMPLE_TICKS = 240;
    private static volatile int checksum;
    private static final String HEADER = "round,scenario,ticks,tick_median_ms,tick_p95_ms,frames,frame_median_ms,frame_p95_ms";

    private record Scenario(String name, Block block, boolean water, boolean visible) {}

    static void run(ClientGameTestContext context) {
        List<String> rows = new ArrayList<>();
        rows.add(HEADER);
        InactivityFpsLimit originalInactivity = context.computeOnClient(client -> client.options.getInactivityFpsLimit().getValue());
        boolean originalVsync = context.computeOnClient(client -> client.options.getEnableVsync().getValue());
        context.runOnClient(client -> {
            client.options.getInactivityFpsLimit().setValue(InactivityFpsLimit.MINIMIZED);
            client.options.getEnableVsync().setValue(false);
        });
        context.runOnClient(client -> SkySigns.LOGGER.info(
                "SKYSIGNS_PERF_OPTIONS fps={} vsync={} view={} simulation={} fov={} window={}x{}",
                client.options.getMaxFps().getValue(), client.options.getEnableVsync().getValue(),
                client.options.getViewDistance().getValue(), client.options.getSimulationDistance().getValue(),
                client.options.getFov().getValue(), client.getWindow().getWidth(), client.getWindow().getHeight()));
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksDownload();
            BlockPos origin = singleplayer.getServer().computeOnServer(server -> {
                ServerWorld world = server.getOverworld();
                WeatherManager.setManual(world, SkyWeatherType.CLEAR);
                BlockPos start = server.getPlayerManager().getPlayerList().getFirst().getBlockPos();
                return new BlockPos(start.getX(), 200, start.getZ());
            });
            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                // Opaque floor hides the changing water layer from the renderer. All scanned chunks are loaded.
                for (int x = -4; x <= 20; x++) {
                    for (int z = -16; z <= 12; z++) {
                        world.setBlockState(origin.add(x, -1, z), Blocks.STONE.getDefaultState());
                        world.setBlockState(origin.add(x, -3, z), Blocks.STONE.getDefaultState());
                        if (x == -4 || x == 19 || z == -4 || z == 11) {
                            world.setBlockState(origin.add(x, -2, z), Blocks.STONE.getDefaultState());
                        }
                    }
                }
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.requestTeleport(origin.getX() + 8, origin.getY(), origin.getZ() - 10);
            });
            context.waitTicks(80);
            List<Scenario> scenarios = List.of(
                    new Scenario("empty_visible", Blocks.AIR, false, true),
                    new Scenario("barometer_dry_visible", ModBlocks.BAROMETER, false, true),
                    new Scenario("hygrometer_dry_visible", ModBlocks.HYGROMETER, false, true),
                    new Scenario("hygrometer_dry_away", ModBlocks.HYGROMETER, false, false),
                    new Scenario("hygrometer_water_visible", ModBlocks.HYGROMETER, true, true));
            for (int round = 1; round <= 3; round++) {
                List<Scenario> order = new ArrayList<>(scenarios);
                if (round == 2) Collections.reverse(order);
                for (Scenario scenario : order) {
                    singleplayer.getServer().runOnServer(server -> configure(server.getOverworld(), origin, scenario));
                    context.runOnClient(client -> {
                        client.player.setYaw(scenario.visible ? 0 : 180);
                        client.player.setPitch(8);
                    });
                    context.waitTicks(WARMUP_TICKS);
                    verifyFrameLimiter(context);
                    singleplayer.getServer().runOnServer(server -> verify(server.getOverworld(), origin, scenario));
                    PerformanceProbe.Phase phase = PerformanceProbe.start();
                    try {
                        context.waitTicks(SAMPLE_TICKS);
                    } finally {
                        PerformanceProbe.stop();
                    }
                    verifyFrameLimiter(context);
                    var ticks = phase.tickSummary();
                    var frames = phase.frameSummary();
                    require(ticks.count() >= 200 && frames.count() >= 100, "Insufficient performance samples");
                    String row = String.format(Locale.ROOT, "%d,%s,%d,%.6f,%.6f,%d,%.6f,%.6f",
                            round, scenario.name, ticks.count(), ticks.medianMs(), ticks.p95Ms(),
                            frames.count(), frames.medianMs(), frames.p95Ms());
                    rows.add(row);
                    SkySigns.LOGGER.info("SKYSIGNS_PERF {}", row);
                    context.takeScreenshot("perf-round" + round + "-" + scenario.name);
                }
            }
            // Separate synchronous algorithm batches from frame/world-tick sampling.
            for (boolean water : new boolean[]{false, true}) {
                singleplayer.getServer().runOnServer(server -> {
                    configure(server.getOverworld(), origin,
                            new Scenario("algorithm", ModBlocks.HYGROMETER, water, true));
                    benchmarkAlgorithms(server.getOverworld(), origin, water);
                });
            }
            Path result = Path.of("performance-results", "hygrometer-" + System.currentTimeMillis() + ".csv");
            Files.createDirectories(result.getParent());
            Files.write(result, rows);
            SkySigns.LOGGER.info("SKYSIGNS_PERF_CSV {}", result.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Hygrometer performance benchmark failed", e);
        } finally {
            PerformanceProbe.stop();
            context.runOnClient(client -> {
                client.options.getInactivityFpsLimit().setValue(originalInactivity);
                client.options.getEnableVsync().setValue(originalVsync);
            });
        }
    }

    private static void verifyFrameLimiter(ClientGameTestContext context) {
        context.runOnClient(client -> require(
                client.getInactivityFpsLimiter().getLimitReason().toString().equals("NONE"),
                "Inactivity frame limiter active during performance measurement"));
    }

    private static void configure(ServerWorld world, BlockPos origin, Scenario scenario) {
        // Set the entire 7x5x7 query volume: dry queries have no water; wet queries hit at their first position.
        for (int x = -3; x <= 18; x++) {
            for (int z = -3; z <= 10; z++) {
                for (int y = -2; y <= 2; y++) {
                    if (y == -1) continue;
                    world.setBlockState(origin.add(x, y, z),
                            (y == -2 && scenario.water ? Blocks.WATER : Blocks.AIR).getDefaultState());
                }
            }
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 8; z++) {
                var state = scenario.block.getDefaultState();
                if (scenario.block != Blocks.AIR) state = state.with(ObservationInstrumentBlock.FACING, Direction.NORTH);
                world.setBlockState(origin.add(x, 0, z), state);
            }
        }
    }

    private static void verify(ServerWorld world, BlockPos origin, Scenario scenario) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 8; z++) {
                BlockPos pos = origin.add(x, 0, z);
                require(world.getBlockState(pos).isOf(scenario.block), "Scene block mismatch at " + pos);
                if (scenario.block == ModBlocks.HYGROMETER) {
                    require(world.getBlockEntity(pos) instanceof MeterBlockEntity meter
                            && meter.readingPercent() == AtmosphericReadings.humidityPercent(world, pos),
                            "Hygrometer reading mismatch at " + pos);
                }
            }
        }
    }

    private static void benchmarkAlgorithms(ServerWorld world, BlockPos origin, boolean water) {
        try {
            MethodHandle scan = MethodHandles.privateLookupIn(AtmosphericReadings.class, MethodHandles.lookup())
                    .findStatic(AtmosphericReadings.class, "hasNearbyWater",
                            MethodType.methodType(boolean.class, ServerWorld.class, BlockPos.class));
            List<BlockPos> positions = new ArrayList<>();
            for (int x = 0; x < 16; x++) for (int z = 0; z < 8; z++) positions.add(origin.add(x, 0, z));
            for (BlockPos pos : positions) require((boolean) scan.invokeExact(world, pos) == water, "Water fixture mismatch");
            for (String operation : List.of("water_scan", "humidity", "pressure")) {
                List<Long> times = new ArrayList<>();
                // 8 warmup batches, 24 measured batches; each batch has 128 calls.
                for (int batch = 0; batch < 32; batch++) {
                    int sum = 0;
                    long start = System.nanoTime();
                    for (BlockPos pos : positions) {
                        sum += switch (operation) {
                            case "water_scan" -> (boolean) scan.invokeExact(world, pos) ? 1 : 0;
                            case "humidity" -> AtmosphericReadings.humidityPercent(world, pos);
                            default -> Math.round(AtmosphericReadings.pressureHpa(world));
                        };
                    }
                    long elapsed = System.nanoTime() - start;
                    checksum = sum;
                    if (batch >= 8) times.add(elapsed);
                }
                times.sort(Long::compare);
                SkySigns.LOGGER.info("SKYSIGNS_ALGORITHM water={} operation={} calls_per_batch=128 batches=24 median_ms={} p95_ms={}",
                        water, operation, times.get(12) / 1_000_000.0, times.get(22) / 1_000_000.0);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Algorithm benchmark failed", e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
