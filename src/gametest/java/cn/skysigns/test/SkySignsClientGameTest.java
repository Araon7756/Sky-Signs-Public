package cn.skysigns.test;

import cn.skysigns.block.ObservationInstrumentBlock;
import cn.skysigns.block.entity.MeterBlockEntity;
import cn.skysigns.client.screen.WeatherStationScreen;
import cn.skysigns.network.StationReportPayload;
import cn.skysigns.registry.ModBlocks;
import cn.skysigns.weather.AtmosphericReadings;
import cn.skysigns.weather.SkyWeatherType;
import cn.skysigns.weather.WeatherManager;
import cn.skysigns.weather.WeatherState;
import cn.skysigns.weather.ClimateAssertions;
import cn.skysigns.SkySigns;
import cn.skysigns.storage.SaveDataMigrations;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.block.Blocks;
import net.minecraft.block.BedBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

@SuppressWarnings("UnstableApiUsage")
public final class SkySignsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if ("1".equals(System.getenv("SKYSIGNS_INF01_SELFTEST"))) {
            ChunkReadiness.prepare(context);
            Inf01ReadinessAssertions.run(context);
            return;
        }
        if ("1".equals(System.getenv("SKYSIGNS_NETWORK_SELFTEST"))) {
            NetworkSynchronizerAssertions.run();
            return;
        }
        if (System.getenv("SKYSIGNS_ISSUE9_MODE") != null) {
            Issue9PerformanceTest.run(context);
            return;
        }
        if ("1".equals(System.getenv("SKYSIGNS_PERFORMANCE_TEST"))) {
            HygrometerPerformanceTest.run(context);
            return;
        }
        if ("1".equals(System.getenv("SKYSIGNS_P1_ONLY"))) {
            P1ClientGameTest.run(context);
            return;
        }
        cn.skysigns.weather.P1Assertions.curves();
        ChunkReadiness.prepare(context);
        verifyModIcon();
        ModelUiAssertions.verifyResources();
        ClimateAssertions.verify();
        // Flooding fixtures use their own disposable world; fluid propagation must
        // not leak into the existing weather/UI regression scenes.
        try (TestSingleplayerContext waterWorld = context.worldBuilder().create()) {
            ChunkReadiness.await(context, waterWorld, "water-created", playerTargets(waterWorld));
            WaterObservationAssertions.verify(context, waterWorld);
            ChunkReadiness.capture(context, waterWorld, "water-complete", playerTargets(waterWorld));
        }
        ChunkReadiness.closed(context, "water");
        if ("1".equals(System.getenv("SKYSIGNS_WATER_ONLY"))) return;
        TestWorldSave save;
        long[] savedAutomaticPhase;
        BlockPos legacyMeterPos;
        BlockPos stationPos;
        // The builder creates a disposable singleplayer world in the test run directory.
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            save = singleplayer.getWorldSave();
            ChunkReadiness.await(context, singleplayer, "station-created", playerTargets(singleplayer));
            stationPos = singleplayer.getServer().computeOnServer(server -> {
                ServerWorld world = server.getOverworld();
                SaveMigrationAssertions.verify(world, save.getSaveDirectory());
                ClimateAssertions.verifyTerralithRegistry(world);
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().getFirst();
                WeatherManager.setManual(world, SkyWeatherType.CLEAR);
                BlockPos pos = player.getBlockPos().east(2);
                world.setBlockState(pos, ModBlocks.WEATHER_STATION.getDefaultState());
                verifyLocalPrecipitationSnapshots(world, pos);
                return pos;
            });
            legacyMeterPos = stationPos.south(3);
            singleplayer.getServer().runOnServer(server -> server.getOverworld().setBlockState(legacyMeterPos,
                    ModBlocks.HYGROMETER.getDefaultState().with(ObservationInstrumentBlock.FACING, Direction.EAST)));
            context.waitFor(client -> client.world != null
                    && client.world.getBlockState(stationPos).isOf(ModBlocks.WEATHER_STATION));
            ChunkReadiness.captureTarget(context, singleplayer, "station-blocks", stationTargets(stationPos, legacyMeterPos), stationPos);
            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().getFirst();
                ObservationInstrumentBlock.sendStationReport(world, player, stationPos, true);
            });
            context.waitForScreen(WeatherStationScreen.class);
            StationReportPayload before = currentReport(context);
            check(hasWeather(before, SkyWeatherType.CLEAR) && !before.automatic(),
                    "Station did not show initial manual clear weather: " + before.weather());

            // Fabric test worlds default doWeatherCycle=false. This scenario deliberately advances auto weather.
            singleplayer.getServer().runCommand("gamerule doWeatherCycle true");
            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                WeatherManager.enableAutomatic(world);
                WeatherState state = WeatherManager.get(world);
                long now = world.getTime();
                state.set(SkyWeatherType.CLEAR, SkyWeatherType.RAIN, now, now + 60L);
            });
            context.waitTicks(100);
            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                check(WeatherManager.get(world).type() == SkyWeatherType.RAIN,
                        "Automatic weather did not advance to rain");
                check(world.isRaining(), "Vanilla weather did not start raining");
            });
            // A rain description can arrive before vanilla's visual rain threshold. Compare
            // a subsequent periodic report with the settled state, not that earlier snapshot.
            Text expectedLocalWeather = singleplayer.getServer().computeOnServer(server -> {
                ServerWorld world = server.getOverworld();
                BlockPos samplePos = stationPos.up();
                return switch (world.getPrecipitation(samplePos)) {
                    case RAIN -> Text.translatable("reading.skysigns.local.rain");
                    case SNOW -> Text.translatable("reading.skysigns.local.snow");
                    case NONE -> world.getBiome(samplePos).value().hasPrecipitation()
                            ? Text.translatable("reading.skysigns.local.sheltered")
                            : Text.translatable("reading.skysigns.local.biome_none");
                };
            });
            context.waitFor(client -> client.currentScreen instanceof WeatherStationScreen screen
                    && hasWeather(readReport(screen), SkyWeatherType.RAIN)
                    && readReport(screen).localWeather().equals(expectedLocalWeather));
            StationReportPayload after = currentReport(context);
            check(after.automatic() && hasWeather(after, SkyWeatherType.RAIN)
                            && after.secondsRemaining() > 0 && after.progressPercent() < 100,
                    "Station did not refresh after automatic weather change: " + after);
            check(after.localWeather().equals(expectedLocalWeather),
                    "Station local precipitation differs from world at instrument: " + after.localWeather());
            String originalLanguage = context.computeOnClient(client -> client.options.language);
            try {
                verifyLanguage(context, "en_us", "Rain", "beta7-station-en-us");
                verifyCompactScreen(context, "beta7-station-en-us-compact");
                verifyLanguage(context, "zh_cn", "降雨", "beta7-station-zh-cn");
                verifyCompactScreen(context, "beta7-station-zh-cn-compact");
            } finally {
                changeLanguage(context, originalLanguage);
            }

            context.runOnClient(client -> client.setScreen(new InstrumentPreviewScreen()));
            context.waitTicks(3);
            context.takeScreenshot("beta7-inventory-models");
            verifyInstrumentViews(context, singleplayer, stationPos);
            context.runOnClient(client -> client.setScreen(new WeatherStationScreen(after)));

            for (String weather : new String[]{"clear", "rain", "thunder"}) {
                singleplayer.getServer().runCommand("weather " + weather + " 300");
                context.waitTicks(25);
                singleplayer.getServer().runOnServer(server -> {
                    ServerWorld world = server.getOverworld();
                    SkyWeatherType expected = weather.equals("clear") ? SkyWeatherType.CLEAR
                            : weather.equals("rain") ? SkyWeatherType.RAIN : SkyWeatherType.STORM;
                    WeatherState state = WeatherManager.get(world);
                    check(state.type() == expected && state.automatic(),
                            "Vanilla command mismatch: " + weather + ", actual=" + state.type());
                    check(state.nextAutomaticChange() > world.getTime(), "Vanilla command left expired countdown");
                });
            }
            singleplayer.getServer().runOnServer(server ->
                    WeatherManager.setManual(server.getOverworld(), SkyWeatherType.CLEAR));
            for (String weather : new String[]{"clear", "rain", "thunder"}) {
                singleplayer.getServer().runCommand("weather " + weather + " 300");
                context.waitTicks(25);
                singleplayer.getServer().runOnServer(server -> {
                    WeatherState state = WeatherManager.get(server.getOverworld());
                    SkyWeatherType expected = weather.equals("clear") ? SkyWeatherType.CLEAR
                            : weather.equals("rain") ? SkyWeatherType.RAIN : SkyWeatherType.STORM;
                    check(state.type() == expected && state.forecastType() == expected && !state.automatic(),
                            "Vanilla command lost manual mode or forecast: " + weather);
                });
                StationReportPayload manual = currentReport(context);
                check(!manual.automatic() && manual.secondsRemaining() == -1 && hasWeather(manual,
                        weather.equals("clear") ? SkyWeatherType.CLEAR
                                : weather.equals("rain") ? SkyWeatherType.RAIN : SkyWeatherType.STORM),
                        "Station manual report/countdown did not match vanilla command");
            }

            context.computeOnClient(client -> {
                client.setScreen(null);
                return true;
            });
            // These exact-value sampling/probe assertions require a stable base. Manual now has a 400-tick rebase,
            // and the preceding vanilla 300-tick command is allowed to expire naturally.
            singleplayer.getServer().runOnServer(server -> WeatherManager.setManual(server.getOverworld(), SkyWeatherType.STORM));
            context.waitTicks(420);
            PerformanceProbe.Phase baseline = PerformanceProbe.start();
            context.waitTicks(120);
            PerformanceProbe.stop();

            BlockPos stressStart = stationPos.east(5);
            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 8; z++) {
                        world.setBlockState(stressStart.add(x, 0, z), ModBlocks.HYGROMETER.getDefaultState());
                    }
                }
            });
            context.waitTicks(40);
            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 8; z++) {
                        BlockPos pos = stressStart.add(x, 0, z);
                        check(world.getBlockEntity(pos) instanceof MeterBlockEntity meter
                                        && meter.readingPercent() == AtmosphericReadings.humidityPercent(world, pos),
                                "Stress-test hygrometer did not sample at " + pos);
                    }
                }
            });
            PerformanceProbe.Phase loaded = PerformanceProbe.start();
            context.waitTicks(120);
            PerformanceProbe.stop();
            check(baseline.tickSummary().count() >= 100 && loaded.tickSummary().count() >= 100,
                    "Not enough integrated-server tick samples");
            check(baseline.frameSummary().count() >= 100 && loaded.frameSummary().count() >= 100,
                    "Not enough rendered frame samples");
            SkySigns.LOGGER.info("Client GameTest perf baseline: world tick {}, frame {}",
                    baseline.tickSummary(), baseline.frameSummary());
            SkySigns.LOGGER.info("Client GameTest perf +128 hygrometers: world tick {}, frame {}",
                    loaded.tickSummary(), loaded.frameSummary());

            BlockPos meterStart = stationPos.north(3);
            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                for (int i = 0; i < 20; i++) {
                    world.setBlockState(meterStart.north(i), ModBlocks.HYGROMETER.getDefaultState());
                }
            });
            context.waitTicks(50);
            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                for (int i = 0; i < 20; i++) {
                    BlockPos pos = meterStart.north(i);
                    check(world.getBlockEntity(pos) instanceof MeterBlockEntity meter
                                    && meter.readingPercent() == AtmosphericReadings.humidityPercent(world, pos),
                            "Hygrometer did not update after staggered sampling at " + pos);
                }
            });
            singleplayer.getServer().runCommand("gamerule doWeatherCycle true");
            singleplayer.getServer().runCommand("time set midnight");
            BlockPos sleepPos = singleplayer.getServer().computeOnServer(server -> {
                ServerWorld world = server.getOverworld();
                WeatherManager.setManual(world, SkyWeatherType.STORM);
                ServerPlayerEntity player = server.getPlayerManager().getPlayerList().getFirst();
                BlockPos bed = player.getBlockPos();
                var foot = Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH)
                        .with(BedBlock.PART, BedPart.FOOT);
                world.setBlockState(bed, foot);
                world.setBlockState(bed.north(), foot.with(BedBlock.PART, BedPart.HEAD));
                check(player.trySleep(bed).right().isPresent(), "Could not start actual bed sleep");
                return bed;
            });
            context.waitTicks(140);
            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                check(!world.getLevelProperties().isRaining(), "Sleeping did not clear vanilla rain");
                WeatherState state = WeatherManager.get(world);
                check(state.type() == SkyWeatherType.CLEAR && !state.automatic(),
                        "Sleep did not reconcile clear weather while preserving manual mode");
                WeatherManager.setManual(world, SkyWeatherType.STORM);
                WeatherManager.enableAutomatic(world);
            });
            singleplayer.getServer().runCommand("time set midnight");
            singleplayer.getServer().runOnServer(server -> check(
                    server.getPlayerManager().getPlayerList().getFirst().trySleep(sleepPos).right().isPresent(),
                    "Could not start automatic-mode sleep"));
            context.waitTicks(140);
            singleplayer.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                WeatherState state = WeatherManager.get(world);
                check(!world.getLevelProperties().isRaining() && state.type() == SkyWeatherType.CLEAR
                                && state.automatic() && state.nextAutomaticChange() > world.getTime(),
                        "Automatic-mode sleep did not retain mode and renew countdown");
                WeatherManager.setManual(world, SkyWeatherType.STORM);
            });
            singleplayer.getServer().runOnServer(server -> prepareDimensionLanding(server.getWorld(World.NETHER)));
            singleplayer.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 80 0");
            context.waitFor(client -> client.world != null && client.world.getRegistryKey().equals(World.NETHER));
            ChunkReadiness.capture(context, singleplayer, "nether", playerTargets(singleplayer)
                    .block(new BlockPos(0, 79, 0), Blocks.GLASS));
            singleplayer.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                check(WeatherManager.get(player.getEntityWorld()).type() == SkyWeatherType.CLEAR,
                        "Nether inherited overworld storm readings");
                check(WeatherManager.get(server.getOverworld()).type() == SkyWeatherType.STORM,
                        "Dimension transition overwrote overworld weather");
            });
            singleplayer.getServer().runOnServer(server -> prepareDimensionLanding(server.getOverworld()));
            singleplayer.getServer().runCommand("execute in minecraft:overworld run tp @a 0 80 0");
            context.waitFor(client -> client.world != null && client.world.getRegistryKey().equals(World.OVERWORLD));
            ChunkReadiness.capture(context, singleplayer, "overworld-return", stationTargets(stationPos, legacyMeterPos)
                    .block(new BlockPos(0, 79, 0), Blocks.GLASS));
            singleplayer.getServer().runOnServer(server -> {
                // Simulate a pre-block-entity instrument: keep the block, remove its BE before saving.
                server.getOverworld().removeBlockEntity(legacyMeterPos);
            });
        }
        ChunkReadiness.closed(context, "station-save");
        try (TestSingleplayerContext reopened = save.open()) {
            savedAutomaticPhase = reopened.getServer().computeOnServer(server -> {
                var world = server.getOverworld();
                check(world.getBlockState(legacyMeterPos).isOf(ModBlocks.HYGROMETER)
                                && world.getBlockState(legacyMeterPos).get(ObservationInstrumentBlock.FACING) == Direction.EAST,
                        "Legacy meter block/facing changed on chunk reload");
                check(world.getBlockEntity(legacyMeterPos) instanceof MeterBlockEntity,
                        "Legacy meter without a block entity was not restored on reload");
                var restored = (MeterBlockEntity) world.getBlockEntity(legacyMeterPos);
                check(restored.createNbt(world.getRegistryManager())
                                .getCompoundOrEmpty(SaveDataMigrations.METER_DATA_KEY)
                                .getInt(SaveDataMigrations.VERSION_KEY, -1) == 1,
                        "Restored legacy meter did not use current schema");
                WeatherState state = WeatherManager.get(server.getOverworld());
                check(state.type() == SkyWeatherType.STORM && !state.automatic(),
                        "Manual storm/mode did not survive save and reopen");
                check(server.getOverworld().isRaining() && server.getOverworld().isThundering(),
                        "Vanilla storm did not survive save and reopen");
                WeatherManager.enableAutomatic(server.getOverworld());
                state = WeatherManager.get(server.getOverworld());
                return new long[]{state.phaseStartedAt(), state.nextAutomaticChange(), state.forecastType().ordinal()};
            });
            ChunkReadiness.captureTarget(context, reopened, "manual-reopen", stationTargets(stationPos, legacyMeterPos), legacyMeterPos);
        }
        ChunkReadiness.closed(context, "manual-reopen-save");
        try (TestSingleplayerContext reopened = save.open()) {
            reopened.getServer().runOnServer(server -> {
                ServerWorld world = server.getOverworld();
                WeatherState state = WeatherManager.get(world);
                check(state.type() == SkyWeatherType.STORM && state.automatic()
                                && state.nextAutomaticChange() > world.getTime(),
                        "Automatic storm/countdown did not survive save and reopen");
                check(state.phaseStartedAt() == savedAutomaticPhase[0]
                                && state.nextAutomaticChange() == savedAutomaticPhase[1]
                                && state.forecastType().ordinal() == savedAutomaticPhase[2],
                        "Reopen rerolled automatic forecast or phase timestamps");
            });
            ChunkReadiness.captureTarget(context, reopened, "automatic-reopen", stationTargets(stationPos, legacyMeterPos), legacyMeterPos);
        }
        ChunkReadiness.closed(context, "automatic-reopen-save");
    }

    private static ChunkReadiness.Targets playerTargets(TestSingleplayerContext game) {
        return game.getServer().computeOnServer(server -> {
            BlockPos pos = server.getPlayerManager().getPlayerList().getFirst().getBlockPos();
            // Includes the water fixture, its seven-block fluid spread, and neighbor queries across chunk boundaries.
            return ChunkReadiness.Targets.area(pos.add(-12, -2, -12), pos.add(12, 10, 12));
        });
    }

    private static ChunkReadiness.Targets stationTargets(BlockPos station, BlockPos legacy) {
        return ChunkReadiness.Targets.area(station.add(-8, -1, -23), station.add(21, 9, 8))
                .block(station, ModBlocks.WEATHER_STATION)
                .meter(legacy, ModBlocks.HYGROMETER, Direction.EAST);
    }

    private static boolean hasWeather(StationReportPayload report, SkyWeatherType expected) {
        if (!(report.weather().getContent() instanceof TranslatableTextContent description)) return false;
        return description.getArgs().length > 0 && expected.displayName().equals(description.getArgs()[0]);
    }

    private static void changeLanguage(ClientGameTestContext context, String language) {
        var reload = context.computeOnClient(client -> {
            client.getLanguageManager().setLanguage(language);
            client.options.language = language;
            return client.reloadResources();
        });
        context.waitFor(client -> reload.isDone());
        reload.join();
        context.waitFor(client -> client.getOverlay() == null);
        context.waitTicks(2);
    }

    private static void verifyLanguage(ClientGameTestContext context, String language,
                                       String rainName, String screenshot) {
        changeLanguage(context, language);
        context.runOnClient(client -> {
            StationReportPayload report = readReport((WeatherStationScreen) client.currentScreen);
            check(report.weather().getString().contains(rainName), "Weather translation failed: " + language);
            for (Text text : new Text[]{report.weather(), report.localWeather(), report.pressure(),
                    report.humidity(), report.wind(), report.forecast()}) {
                String rendered = text.getString();
                check(!rendered.contains("skysigns.") && !rendered.contains("%s"),
                        "Unresolved report translation in " + language + ": " + rendered);
            }
        });
        context.takeScreenshot(screenshot);
    }

    private static StationReportPayload currentReport(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            check(client.currentScreen instanceof WeatherStationScreen, "Station screen closed unexpectedly");
            return readReport((WeatherStationScreen) client.currentScreen);
        });
    }

    private static void verifyCompactScreen(ClientGameTestContext context, String screenshot) {
        context.runOnClient(client -> client.currentScreen.init(client, 320, 180));
        try {
            context.waitTicks(2);
            context.takeScreenshot(screenshot);
            check(currentReport(context) != null, "Compact station lost its live report");
        } finally {
            context.runOnClient(client -> client.currentScreen.init(client,
                    client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight()));
        }
    }

    private static void verifyInstrumentViews(ClientGameTestContext context,
                                              TestSingleplayerContext singleplayer, BlockPos stationPos) {
        double[] original = singleplayer.getServer().computeOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            return new double[]{player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()};
        });
        context.runOnClient(client -> client.setScreen(null));
        BlockPos center = stationPos.up(6);
        try {
            for (Direction facing : new Direction[]{Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST}) {
                singleplayer.getServer().runOnServer(server -> {
                    var world = server.getOverworld();
                    for (int x = -7; x <= 7; x++) {
                        for (int z = -7; z <= 7; z++) {
                            world.setBlockState(center.add(x, -1, z), Blocks.SMOOTH_STONE.getDefaultState());
                            world.setBlockState(center.add(x, 0, z), Blocks.AIR.getDefaultState());
                        }
                    }
                    var blocks = new net.minecraft.block.Block[]{ModBlocks.BAROMETER,
                            ModBlocks.HYGROMETER, ModBlocks.WEATHER_STATION};
                    for (int i = 0; i < blocks.length; i++) {
                        BlockPos pos = facing.getAxis() == Direction.Axis.Z
                                ? center.add((i - 1) * 2, 0, 0) : center.add(0, 0, (i - 1) * 2);
                        var state = blocks[i].getDefaultState().with(ObservationInstrumentBlock.FACING, facing);
                        world.setBlockState(pos, state);
                        check(!state.getCollisionShape(world, pos).isEmpty(), "Missing instrument collision: " + facing);
                        check(world.getBlockState(pos).get(ObservationInstrumentBlock.FACING) == facing,
                                "Instrument facing not retained");
                    }
                });
                int yaw = switch (facing) { case SOUTH -> 180; case WEST -> 270; case NORTH -> 0; default -> 90; };
                String command = String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %d 12",
                        center.getX() + 0.5 + facing.getOffsetX() * 5,
                        center.getY() + 1.5,
                        center.getZ() + 0.5 + facing.getOffsetZ() * 5, yaw);
                singleplayer.getServer().runCommand(command);
                context.waitTicks(10);
                var modelTargets = ChunkReadiness.Targets.area(center.add(-7, -1, -7), center.add(7, 1, 7));
                var modelBlocks = new net.minecraft.block.Block[]{ModBlocks.BAROMETER, ModBlocks.HYGROMETER, ModBlocks.WEATHER_STATION};
                for (int i = 0; i < modelBlocks.length; i++) {
                    BlockPos pos = facing.getAxis() == Direction.Axis.Z
                            ? center.add((i - 1) * 2, 0, 0) : center.add(0, 0, (i - 1) * 2);
                    modelTargets = modelTargets.facing(pos, modelBlocks[i], facing);
                }
                ChunkReadiness.await(context, singleplayer, "models-" + facing.name().toLowerCase(java.util.Locale.ROOT), modelTargets);
                context.takeScreenshot("beta7-models-" + facing.name().toLowerCase(java.util.Locale.ROOT));
            }
        } finally {
            singleplayer.getServer().runCommand(String.format(java.util.Locale.ROOT,
                    "tp @a %.3f %.3f %.3f %.3f %.3f", original[0], original[1], original[2], original[3], original[4]));
            singleplayer.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
                    world.setBlockState(center.add(x, -1, z), Blocks.AIR.getDefaultState());
                    world.setBlockState(center.add(x, 0, z), Blocks.AIR.getDefaultState());
                }
            });
            context.waitTicks(3);
        }
    }

    private static void verifyLocalPrecipitationSnapshots(ServerWorld world, BlockPos pos) {
        WeatherManager.setManual(world, SkyWeatherType.RAIN);
        world.setRainGradient(0.0F);
        check(world.getLevelProperties().isRaining() && !world.isRaining(),
                "Rain fixture must separate server flags from the visual gradient");
        Text fadingReport = AtmosphericReadings.measure(world, pos).localPrecipitation();
        check(fadingReport.equals(Text.translatable("reading.skysigns.local.none")),
                "Local precipitation must remain absent before the visual rain threshold");
        check(WeatherManager.get(world).type() == SkyWeatherType.RAIN,
                "Visual rain fade must not revert the logical rain phase");
        world.setRainGradient(1.0F);
        Text settledReport = AtmosphericReadings.measure(world, pos).localPrecipitation();
        check(world.isRaining() && !settledReport.equals(fadingReport),
                "Local precipitation must update after the rain gradient settles");
        SkySigns.LOGGER.info("Station snapshot regression: fading={}, settled={}, same rain phase={}",
                fadingReport, settledReport, WeatherManager.get(world).type());
        WeatherManager.setManual(world, SkyWeatherType.CLEAR);
        world.setRainGradient(0.0F);
    }

    private static void verifyModIcon() {
        String path = FabricLoader.getInstance().getModContainer(SkySigns.MOD_ID).orElseThrow()
                .getMetadata().getIconPath(128).orElseThrow();
        try (InputStream input = SkySigns.class.getClassLoader().getResourceAsStream(path)) {
            check(input != null, "Declared mod icon is not on the classpath: " + path);
            var image = ImageIO.read(input);
            check(image != null && image.getWidth() == image.getHeight() && image.getColorModel().hasAlpha(),
                    "Mod icon is not a square transparent PNG");
        } catch (IOException exception) {
            throw new AssertionError("Could not read declared mod icon", exception);
        }
    }

    private static StationReportPayload readReport(WeatherStationScreen screen) {
        try {
            Field field = WeatherStationScreen.class.getDeclaredField("report");
            field.setAccessible(true);
            return (StationReportPayload) field.get(screen);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not read station report in client test", exception);
        }
    }

    private static void prepareDimensionLanding(ServerWorld world) {
        // The weather fixture's old y=80 teleport can land inside Nether terrain or fall
        // to the superflat floor. Full readiness takes real ticks; keep the survival player
        // in a small safe test enclosure instead of losing its native chunk tracking on death.
        // Only the disposable fixture changes; no tickets, filter edits or synthetic packets.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 79; y <= 83; y++) {
                    boolean shell = y == 79 || y == 83 || Math.abs(x) == 2 || Math.abs(z) == 2;
                    world.setBlockState(new BlockPos(x, y, z), (shell ? Blocks.GLASS : Blocks.AIR).getDefaultState());
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
