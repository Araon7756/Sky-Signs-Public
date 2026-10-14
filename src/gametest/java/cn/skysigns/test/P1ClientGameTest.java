package cn.skysigns.test;

import cn.skysigns.SkySigns;
import cn.skysigns.weather.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.level.ServerWorldProperties;
import static cn.skysigns.weather.P1Assertions.check;

@SuppressWarnings("UnstableApiUsage")
public final class P1ClientGameTest {
    private P1ClientGameTest() {}
    public static void run(ClientGameTestContext context) {
        P1Assertions.curves();
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            game.getClientWorld().waitForChunksDownload();
            game.getServer().runOnServer(server -> {
                ServerWorld w = server.getOverworld();
                try { P1Assertions.disk(w, game.getWorldSave().getSaveDirectory()); }
                catch (Exception e) { throw new AssertionError("P1 disk fixtures", e); }
                SaveMigrationAssertions.verify(w, game.getWorldSave().getSaveDirectory());
                verifyQueries(w);
                verifyCommands(w);
            });
            context.waitTicks(40);
            game.getServer().runOnServer(server -> {
                ServerWorld w = server.getOverworld(); var s = WeatherManager.get(w);
                long frozen = s.pauseAt();
                check(s.paused() && s.nextAutomaticChange() - frozen == 300, "Paused command consumed duration");
                var bases = WeatherManager.view(w).bases();
                command(w, "gamerule doWeatherCycle true");
                WeatherManager.beforeWeather(w);
                check(!s.paused() && s.nextAutomaticChange() - w.getTime() == 300, "Resume lost remaining duration");
                check(WeatherManager.view(w).bases().equals(bases), "Resume jumped bases");
                command(w, "time add 1000");
                long t = s.nextAutomaticChange(); command(w, "time set day");
                check(s.nextAutomaticChange() == t, "Daylight command changed plan");
            });
            final long[] frozenClock = new long[1];
            game.getServer().runOnServer(server -> {
                var w = server.getOverworld(); frozenClock[0] = w.getTime(); command(w, "tick freeze");
                command(w, "weather thunder 300t");
                var wind = WindManager.get(w); wind.setAutomatic(true); wind.setTarget(0, .99F);
            });
            context.waitTicks(20);
            game.getServer().runOnServer(server -> {
                var w = server.getOverworld(); var state = WeatherManager.get(w);
                check(w.getTime() == frozenClock[0] && state.nextAutomaticChange() - w.getTime() == 300,
                        "Tick freeze advanced weather/command duration");
                check(WindManager.get(w).directionDegrees() == 359 && WindManager.get(w).strength() == .77F,
                        "Tick freeze continued wind smoothing");
                WindManager.setManual(w, 359, .77F);
                command(w, "tick unfreeze");
            });
            for (var from : SkyWeatherType.values()) for (var to : SkyWeatherType.values()) if (from != to) {
                game.getServer().runOnServer(server -> {
                    ServerWorld w = server.getOverworld(); WeatherManager.setManual(w, from);
                    WeatherManager.enableAutomatic(w);
                    long now = w.getTime();
                    var p = WeatherPlan.normal(from, to, now, now + 40);
                    WeatherManager.get(w).install(p, from, true); P1Probe.expected = p; P1Probe.boundaries = 0;
                    // Natural vanilla timers would toggle before T without the production guard.
                    var properties = (ServerWorldProperties) w.getLevelProperties();
                    properties.setClearWeatherTime(0); properties.setRainTime(1); properties.setThunderTime(1);
                });
                context.waitTicks(65);
                game.getServer().runOnServer(server -> {
                    check(P1Probe.boundaries == 2, "Six-transition runtime T/E observer did not execute");
                    P1Probe.expected = null;
                    var wind = WindManager.get(server.getOverworld());
                    check(!wind.automatic() && wind.directionDegrees() == 359 && wind.strength() == .77F, "Weather overwrote manual wind");
                });
            }
            game.getServer().runOnServer(server -> {
                ServerWorld w = server.getOverworld(); verifyAutoWind(w); WeatherManager.setManual(w, SkyWeatherType.RAIN);
                command(w, "weather rain 1t");
            });
            context.waitTicks(5);
            game.getServer().runOnServer(server -> {
                ServerWorld w = server.getOverworld(); var s = WeatherManager.get(w);
                check(!s.automatic() && s.type() == WeatherManager.readVanillaWeather(w) && s.type() == SkyWeatherType.CLEAR,
                        "Manual suppressed vanilla natural expiration");
                verifyRollbackAndLegacy(w);
            });
            verifySleep(context, game);
            SkySigns.LOGGER.info("P1_ASSERTIONS_PASSED: six live T/E transitions before block entities, curves, disk migration/protection, pure queries, vanilla durations/dimensions, pause/command/resume, sleep, rollback, manual wind");
        }
    }
    private static void command(ServerWorld w, String command) {
        try { w.getServer().getCommandManager().getDispatcher().execute(command, w.getServer().getCommandSource().withWorld(w)); }
        catch (Exception e) { throw new AssertionError(command, e); }
    }
    private static void verifyQueries(ServerWorld w) {
        var manager = w.getPersistentStateManager(); var original = WeatherManager.get(w); var originalWind = WindManager.get(w);
        try {
            var uninitialized = new WeatherState(); var wind = new WindState(); manager.set(WeatherState.TYPE, uninitialized); manager.set(WindState.TYPE, wind);
            w.getRandom().setSeed(178); long expected = Random.create(178).nextLong();
            P1Probe.waterChecks = 0;
            for (int i = 0; i < 20; i++) { WeatherManager.view(w); WeatherManager.describe(w); WindManager.get(w); AtmosphericReadings.pressureHpa(w); AtmosphericReadings.pressureText(w); }
            check(P1Probe.waterChecks == 0, "Pressure scanned water");
            AtmosphericReadings.humidityPercent(w, BlockPos.ORIGIN);
            check(P1Probe.waterChecks == 1, "Humidity did not scan water exactly once");
            AtmosphericReadings.measure(w, BlockPos.ORIGIN);
            check(P1Probe.waterChecks == 2, "Measure duplicated water scan");
            check(!uninitialized.initialized() && !wind.initialized() && w.getRandom().nextLong() == expected, "Queries initialized/consumed RNG");
        } finally { manager.set(WeatherState.TYPE, original); manager.set(WindState.TYPE, originalWind); }
    }
    private static void verifyCommands(ServerWorld w) {
        command(w, "gamerule doWeatherCycle false"); WeatherManager.enableAutomatic(w);
        WindManager.setManual(w, 359, .77F);
        WeatherState s = WeatherManager.get(w);
        long frozen = s.pauseAt();
        for (String arg : new String[]{"300", "300t", "15s", "1d"}) {
            command(w, "weather rain " + arg);
            check(s.nextAutomaticChange() - frozen == (arg.equals("1d") ? 24000 : 300), "Vanilla duration units wrong: " + arg);
        }
        WeatherPlan first = s.plan(); command(w, "weather rain 600t");
        check(s.plan() != first && s.nextAutomaticChange() - frozen == 600, "Same-weather duration command lost");
        command(w, "weather thunder 20t"); command(w, "weather clear 50t");
        check(s.type() == SkyWeatherType.CLEAR && s.nextAutomaticChange() - frozen == 50, "Same-tick independent commands lost");
        command(w, "weather rain");
        long actual = ((ServerWorldProperties) w.getLevelProperties()).getRainTime();
        check(actual > 0 && s.nextAutomaticChange() - frozen == actual, "Default duration used -1 or rerolled");
        var nether = w.getServer().getWorld(World.NETHER);
        command(nether, "weather thunder 77t");
        check(s.type() == SkyWeatherType.STORM && s.nextAutomaticChange() - frozen == 77 && !WeatherManager.get(nether).initialized(), "Command source dimension mistaken for target");
        check(!WeatherManager.view(nether).applicable(), "Nether applicability");
        check(nether.getPersistentStateManager().get(WeatherState.TYPE) == null, "Query installed a nonapplicable weather file");
        command(w, "weather rain 300t");
    }
    private static void verifyAutoWind(ServerWorld w) {
        WeatherManager.setManual(w, SkyWeatherType.CLEAR);
        long now = w.getTime();
        WeatherManager.get(w).install(WeatherPlan.normal(SkyWeatherType.CLEAR, SkyWeatherType.STORM, now - 1000, now + 400),
                SkyWeatherType.CLEAR, true);
        var range = WeatherManager.view(w).bases(); var wind = WindManager.get(w);
        for (int seed = 0; seed < 16; seed++) {
            w.getRandom().setSeed(seed); WindManager.enableAutomatic(w);
            float previous = wind.strength(); WindManager.tick(w);
            var codec = WindState.TYPE.codec().apply(new net.minecraft.world.PersistentState.Context(w));
            var saved = (net.minecraft.nbt.NbtCompound) codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, wind).getOrThrow();
            float target = saved.getFloat("target_strength", -1);
            check(target >= range.windMin() && target <= range.windMax(), "Automatic wind used abrupt type range");
            check(wind.nextAutomaticChange() - now >= 400 && wind.nextAutomaticChange() - now <= 900, "Wind target cadence changed");
            check(Math.abs(wind.strength() - previous) <= .00251, "Actual wind was clamped/jumped to range");
        }
        var codec = WindState.TYPE.codec().apply(new net.minecraft.world.PersistentState.Context(w));
        var before = (net.minecraft.nbt.NbtCompound) codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, wind).getOrThrow();
        // Cross T while its own wind target is far in the future; weather alone must not choose another target.
        wind.scheduleNextChange(now + 10000);
        ((ServerWorldProperties) w.getLevelProperties()).setTime(now + 400); WeatherManager.tick(w);
        var after = (net.minecraft.nbt.NbtCompound) codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, wind).getOrThrow();
        check(before.get("target_direction").equals(after.get("target_direction"))
                && before.get("target_strength").equals(after.get("target_strength")), "T forced a wind target redraw");
        WindManager.setManual(w, 359, .77F);
    }
    private static void verifyRollbackAndLegacy(ServerWorld w) {
        WeatherManager.enableAutomatic(w); WeatherManager.beforeWeather(w);
        var s = WeatherManager.get(w); long before = s.nextAutomaticChange() - w.getTime();
        long now = w.getTime(); ((ServerWorldProperties) w.getLevelProperties()).setTime(now - 10);
        WeatherManager.beforeWeather(w);
        check(s.nextAutomaticChange() - w.getTime() == before, "Runtime rollback changed remaining duration");
        ((ServerWorldProperties) w.getLevelProperties()).setTime(now); // forward jump then fresh explicit plan
        WeatherManager.setManual(w, SkyWeatherType.STORM); WeatherManager.enableAutomatic(w);
        var old = new WeatherPlan(SkyWeatherType.STORM, SkyWeatherType.RAIN, now - 1000, now - 500, now, now,
                WeatherPlan.Curve.LEGACY, now - 1000, now - 1000, WeatherPlan.Bases.of(SkyWeatherType.STORM));
        s.install(old, SkyWeatherType.STORM, true);
        var beforeBases = old.bases(now, true); WeatherManager.tick(w);
        check(s.type() == SkyWeatherType.RAIN && s.phaseStartedAt() == now && s.plan().curve() == WeatherPlan.Curve.REBASE,
                "Expired LEGACY did not complete exactly once");
        check(WeatherManager.view(w).bases().equals(beforeBases), "LEGACY completion jumped humidity/wind");
        var next = s.plan(); WeatherManager.tick(w); check(s.plan() == next, "Same tick recursively drew another cycle");
        for (long length : new long[]{0, 1}) {
            WeatherManager.setManual(w, SkyWeatherType.CLEAR); WeatherManager.enableAutomatic(w);
            long start = w.getTime();
            var zero = WeatherPlan.normal(SkyWeatherType.CLEAR, SkyWeatherType.STORM, start, start + length);
            s.install(zero, SkyWeatherType.CLEAR, true);
            ((ServerWorldProperties) w.getLevelProperties()).setTime(start + length);
            var actualBases = zero.bases(start + length, true); WeatherManager.tick(w);
            check(s.type() == SkyWeatherType.STORM && s.plan().curve() == WeatherPlan.Curve.REBASE
                    && WeatherManager.view(w).bases().equals(actualBases), "Zero/single tick plan jumped bases");
        }
        var once = s.plan();
        long jumped = once.end() + 100000;
        ((ServerWorldProperties) w.getLevelProperties()).setTime(jumped); WeatherManager.tick(w);
        check(s.phaseStartedAt() == jumped, "Large jump did not start at actual completion tick");
        var afterJump = s.plan(); WeatherManager.tick(w);
        check(s.plan() == afterJump, "Large jump generated a missed-cycle chain");
    }
    private static void verifySleep(ClientGameTestContext context, TestSingleplayerContext game) {
        var bed = game.getServer().computeOnServer(server -> {
            var w = server.getOverworld(); command(w, "gamerule doWeatherCycle false"); command(w, "time set midnight");
            WeatherManager.setManual(w, SkyWeatherType.STORM);
            w.setRainGradient(1); w.setThunderGradient(1);
            var player = server.getPlayerManager().getPlayerList().getFirst(); var pos = player.getBlockPos();
            var foot = net.minecraft.block.Blocks.RED_BED.getDefaultState().with(net.minecraft.block.BedBlock.FACING, net.minecraft.util.math.Direction.NORTH)
                    .with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.FOOT);
            w.setBlockState(pos, foot); w.setBlockState(pos.north(), foot.with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.HEAD));
            check(player.trySleep(pos).right().isPresent(), "Disabled-cycle sleep did not start"); return pos;
        });
        context.waitTicks(140);
        game.getServer().runOnServer(server -> {
            var w = server.getOverworld(); check(WeatherManager.get(w).type() == SkyWeatherType.STORM, "Synthesized sleep clear with gamerule disabled");
            command(w, "gamerule doWeatherCycle true"); command(w, "time set midnight");
            check(server.getPlayerManager().getPlayerList().getFirst().trySleep(bed).right().isPresent(), "Manual sleep did not start");
        });
        context.waitTicks(140);
        game.getServer().runOnServer(server -> {
            var w = server.getOverworld(); check(WeatherManager.get(w).type() == SkyWeatherType.CLEAR && !WeatherManager.get(w).automatic(), "Manual sleep mode/reset");
            WeatherManager.setManual(w, SkyWeatherType.STORM); WeatherManager.enableAutomatic(w);
            w.setRainGradient(1); w.setThunderGradient(1); command(w, "time set midnight");
            check(server.getPlayerManager().getPlayerList().getFirst().trySleep(bed).right().isPresent(), "Auto sleep did not start");
        });
        context.waitTicks(140);
        game.getServer().runOnServer(server -> {
            var w = server.getOverworld(); var s = WeatherManager.get(w);
            check(s.type() == SkyWeatherType.CLEAR && s.automatic() && s.nextAutomaticChange() > w.getTime(), "Auto sleep reset plan/mode");
        });
    }
}
