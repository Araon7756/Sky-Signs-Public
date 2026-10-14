package cn.skysigns.weather;

import cn.skysigns.storage.SaveDataMigrations;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import java.nio.file.*;
import java.util.function.Consumer;

/** Numerical and real compressed-disk fixtures; no world clock/RNG in migration. */
public final class P1Assertions {
    private P1Assertions() {}
    public static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void near(float a, float b, String message) { check(Math.abs(a - b) < .0002, message + ": " + a + " vs " + b); }
    public static void curves() {
        for (var from : SkyWeatherType.values()) for (var to : SkyWeatherType.values()) if (from != to) {
            var p = WeatherPlan.normal(from, to, 100, 4100); p.validate(true);
            check(p.precursor() == 2900 && p.end() == 4500, "Normal clipping");
            check(p.bases(p.precursor(), true).equals(WeatherPlan.Bases.of(from)), "P source");
            check(p.bases(p.end(), true).equals(WeatherPlan.Bases.of(to)), "E target");
            for (long t : new long[]{p.precursor(), p.change(), p.end()}) {
                var previous = p.bases(t - 1, true); var here = p.bases(t, true); var after = p.bases(t + 1, true);
                check(Math.abs(previous.pressure() - here.pressure()) < .001, "Boundary pressure continuity");
                check(Math.abs(after.pressure() - here.pressure()) < .001, "Boundary pressure continuity");
                check(Math.abs(previous.wetHumidity() - here.wetHumidity()) < .001, "Boundary humidity continuity");
                check(Math.abs(previous.windMin() - here.windMin()) < .00001, "Boundary wind continuity");
            }
            check(p.weight(p.change()) == .5, "T shared midpoint");
            check(p.pressureDelta(p.precursor() - 1, true) == 0 && p.pressureDelta(p.end(), true) == 0, "Stable trend");
            var anchor = new WeatherPlan.Bases(1011, 66, 41, .22F, .7F);
            var r = WeatherPlan.rebase(from, to, 100, 4100, anchor);
            check(r.bases(100, true).equals(anchor) && r.bases(500, true).equals(WeatherPlan.Bases.of(from)), "Rebase anchors");
            check(r.bases(r.precursor(), true).equals(WeatherPlan.Bases.of(from)), "Rebase meets precursor");
            var manual = WeatherPlan.manual(to, 100, anchor); manual.validate(false);
            check(manual.bases(100, false).equals(anchor) && manual.bases(500, false).equals(WeatherPlan.Bases.of(to)), "Manual rebase");
            for (long length : new long[]{0, 1, 2, 3, 4, 20}) {
                var shortPlan = WeatherPlan.normal(from, to, 100, 100 + length); shortPlan.validate(true);
                check(shortPlan.change() == 100 + length, "Short deadline was postponed");
                for (long t = 99; t <= shortPlan.end() + 1; t++) check(Float.isFinite(shortPlan.bases(t, true).pressure()), "Nonfinite short curve");
                if (length == 0) check(shortPlan.bases(100, true).equals(WeatherPlan.Bases.of(from)), "Zero plan jumped bases");
                if (length == 2) check(shortPlan.weight(102) == 1, "Precursor-only full weight");
            }
            var transitionOnly = new WeatherPlan(from, to, 0, 100, 100, 500, WeatherPlan.Curve.NORMAL, 0, 0, WeatherPlan.Bases.of(from));
            check(transitionOnly.weight(300) == .5, "Transition-only full weight");
        }
        var s = new WeatherState(); s.install(WeatherPlan.normal(SkyWeatherType.RAIN, SkyWeatherType.CLEAR, 16000, 20000), SkyWeatherType.CLEAR, true);
        s.pause(20500); var frozen = s.plan().bases(s.effectiveTime(23000), true); s.resume(23000);
        check(s.plan().start() == 18500 && s.plan().change() == 22500 && s.plan().end() == 22900, "Pause whole timeline shift");
        check(s.plan().bases(23000, true).equals(frozen), "Resume curve continuity");
        for (long tick : new long[]{100, 2900, 4100, 4300, 4500}) {
            var pausedState = new WeatherState();
            pausedState.install(WeatherPlan.normal(SkyWeatherType.CLEAR, SkyWeatherType.RAIN, 100, 4100),
                    tick >= 4100 ? SkyWeatherType.RAIN : SkyWeatherType.CLEAR, true);
            var value = pausedState.plan().bases(tick, true);
            pausedState.pause(tick);
            check(pausedState.plan().bases(pausedState.effectiveTime(tick + 9999), true).equals(value), "Frozen boundary moved");
            pausedState.resume(tick + 1000);
            check(pausedState.plan().bases(tick + 1000, true).equals(value), "Boundary resume jumped");
            long t = pausedState.nextAutomaticChange();
            pausedState.pause(tick + 1000); pausedState.resume(tick + 1500);
            check(pausedState.nextAutomaticChange() == t + 500, "Repeated pause double-shifted");
            pausedState.install(WeatherPlan.manual(SkyWeatherType.STORM, tick + 1500, value), SkyWeatherType.STORM, false);
            check(!pausedState.paused() && pausedState.effectiveTime(tick + 1800) == tick + 1800, "Manual kept frozen axis");
        }
        boolean overflow = false;
        try { WeatherPlan.normal(SkyWeatherType.CLEAR, SkyWeatherType.RAIN, Long.MAX_VALUE - 5, Long.MAX_VALUE); }
        catch (ArithmeticException expected) { overflow = true; }
        check(overflow, "Timeline overflow unchecked");
        near(AtmosphericReadings.humidityForClimate(60.5F, true, .5F, .8F, 80, false), 61, "Final rounding");
    }
    private static Codec<WeatherState> codec(ServerWorld w) { return WeatherState.TYPE.codec().apply(new PersistentState.Context(w)); }
    public static NbtCompound encode(ServerWorld w, WeatherState s) { return (NbtCompound) codec(w).encodeStart(NbtOps.INSTANCE, s).getOrThrow(); }
    private static WeatherState decode(ServerWorld w, NbtCompound n) { return codec(w).parse(NbtOps.INSTANCE, n).getOrThrow(); }
    public static void disk(ServerWorld w, Path parent) throws Exception {
        for (boolean auto : new boolean[]{false, true}) for (int version : new int[]{0, 1}) {
            NbtCompound old = new NbtCompound();
            if (version == 1) old.putInt(SaveDataMigrations.VERSION_KEY, 1);
            old.putString("type", "STORM"); old.putString("forecast", "RAIN"); old.putLong("phase_started_at", 4000);
            old.putLong("next_change", 18000); old.putBoolean("automatic", auto); old.putBoolean("initialized", true);
            old.putString("extension", "keep");
            WeatherState s = roundTrip(w, parent, old, false);
            check(s.plan().curve() == WeatherPlan.Curve.LEGACY && s.nextAutomaticChange() == 18000 && s.plan().end() == 18000, "Legacy promise changed");
            check(s.plan().precursor() == 16800 && s.automatic() == auto, "Legacy mode/clipping");
            for (long t : new long[]{0, 4000, 4500, 9999, 14000, 17999, 18000, 50000}) {
                float x = Math.max(0, Math.min(1, (t - 4000) / 14000F));
                float expected = auto ? 989 + (1005 - 989) * x * x * (3 - 2 * x) : 989;
                near(s.plan().bases(t, auto).pressure(), expected, "Legacy pressure changed at " + t);
                near(s.plan().bases(t, auto).wetHumidity(), 93, "Legacy humidity changed");
            }
            check(encode(w, s).getString("extension", "").equals("keep"), "Unknown key lost");
        }
        WeatherState current = new WeatherState(); current.install(WeatherPlan.rebase(SkyWeatherType.RAIN, SkyWeatherType.CLEAR, 100, 5000,
                WeatherPlan.Bases.of(SkyWeatherType.STORM)), SkyWeatherType.RAIN, true); current.pause(700);
        NbtCompound valid = encode(w, current); valid.putString("extension", "keep"); WeatherState reloaded = roundTrip(w, parent, valid, false);
        check(reloaded.paused() && reloaded.pauseAt() == 700, "Saved pause lost");
        reloaded.resume(1700);
        check(reloaded.phaseStartedAt() == 1100 && reloaded.nextAutomaticChange() == 6000
                && reloaded.plan().anchorAt() == 1100 && reloaded.plan().anchorEnd() == 1500, "Loaded pause shifted anchors incorrectly");
        WeatherState manual = new WeatherState(); manual.install(WeatherPlan.manual(SkyWeatherType.CLEAR, 100, WeatherPlan.Bases.of(SkyWeatherType.STORM)), SkyWeatherType.CLEAR, false);
        roundTrip(w, parent, encode(w, manual), false);
        for (Consumer<NbtCompound> corrupt : java.util.List.<Consumer<NbtCompound>>of(
                n -> n.putString("type", "ALIEN"), n -> n.putString("forecast", "ALIEN"),
                n -> n.putString("curve_mode", "FUTURE"), n -> n.putInt("next_change", 5000),
                n -> n.putFloat("pressure_anchor", Float.NaN), n -> n.putFloat("wind_max_anchor", .1F),
                n -> n.putLong("precursor_at", 6000), n -> n.remove("from_type"),
                n -> n.putLong("phase_started_at", Long.MIN_VALUE), n -> n.putString("automatic", "yes"),
                n -> n.putLong("pause_at", -1),
                n -> n.putInt(SaveDataMigrations.VERSION_KEY, 99), n -> n.putInt(SaveDataMigrations.VERSION_KEY, -1),
                n -> n.putLong(SaveDataMigrations.VERSION_KEY, 2))) {
            var damaged = valid.copy(); corrupt.accept(damaged); roundTrip(w, parent, damaged, true);
        }
        var same = new NbtCompound(); same.putString("type", "RAIN"); same.putString("forecast", "RAIN");
        same.putLong("phase_started_at", 0); same.putLong("next_change", 1);
        check(!decode(w, same).isReadOnly() && decode(w, same).forecastType() == SkyWeatherType.RAIN, "Same-weather legacy promise rejected");
    }
    private static WeatherState roundTrip(ServerWorld w, Path parent, NbtCompound data, boolean protectedData) throws Exception {
        Path dir = Files.createTempDirectory(parent, "p1-disk-");
        NbtCompound root = new NbtCompound(); root.put("data", data); NbtHelper.putDataVersion(root);
        NbtIo.writeCompressed(root, dir.resolve("skysigns_weather.dat"));
        try (var m = new PersistentStateManager(new PersistentState.Context(w), dir, w.getServer().getDataFixer(), w.getRegistryManager())) {
            WeatherState s = m.getOrCreate(WeatherState.TYPE);
            check(s.isReadOnly() == protectedData, "Disk fixture protection mismatch: " + data);
            s.markDirty(); m.save();
        }
        NbtCompound saved = NbtIo.readCompressed(dir.resolve("skysigns_weather.dat"), NbtSizeTracker.ofUnlimitedBytes()).getCompoundOrEmpty("data");
        if (protectedData) check(saved.equals(data), "Protected disk bytes/fields overwritten");
        try (var m = new PersistentStateManager(new PersistentState.Context(w), dir, w.getServer().getDataFixer(), w.getRegistryManager())) {
            WeatherState s = m.getOrCreate(WeatherState.TYPE);
            check(!s.isDirty() && encode(w, s).equals(saved), "Repeat disk load changed plan");
            return s;
        }
    }
}
