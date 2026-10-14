package cn.skysigns.test;

import cn.skysigns.SkySigns;
import cn.skysigns.block.entity.MeterBlockEntity;
import cn.skysigns.registry.ModBlocks;
import cn.skysigns.storage.SaveDataMigrations;
import cn.skysigns.weather.SkyWeatherType;
import cn.skysigns.weather.WeatherManager;
import cn.skysigns.weather.WeatherState;
import cn.skysigns.weather.WindManager;
import cn.skysigns.weather.WindState;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.nio.file.Files;
import java.nio.file.Path;

/** Fixtures reflect the fields in archived Alpha/Beta sources, not full historical worlds. */
public final class SaveMigrationAssertions {
    private SaveMigrationAssertions() {}

    public static void verify(ServerWorld world, Path temporaryWorldDirectory) {
        try {
            for (boolean automatic : new boolean[]{false, true}) {
                verifyLegacyFiles(world, Files.createTempDirectory(temporaryWorldDirectory,
                        automatic ? "migration-auto-" : "migration-manual-"), automatic);
            }
            verifyDefaults(world);
            verifyUninitializedManual(world);
            verifyMeter(world);
            verifyFutureProtection(world, Files.createTempDirectory(temporaryWorldDirectory, "migration-future-"));
            SkySigns.LOGGER.info("Save migration assertions passed: WEATHER v0 -> v1 -> v2; WIND/METER v0 -> v1, defaults, repeat load, "
                    + "meters, future/invalid versions, read-only commands and unknown-field preservation");
        } catch (Exception exception) {
            throw new AssertionError("Save migration verification failed", exception);
        }
    }

    private static void verifyLegacyFiles(ServerWorld world, Path directory, boolean automatic) throws Exception {
        NbtCompound weather = legacyWeather(automatic);
        NbtCompound wind = legacyWind(automatic);
        writeState(directory, "skysigns_weather", weather);
        writeState(directory, "skysigns_wind", wind);
        try (PersistentStateManager manager = manager(world, directory)) {
            WeatherState loadedWeather = manager.getOrCreate(WeatherState.TYPE);
            WindState loadedWind = manager.getOrCreate(WindState.TYPE);
            check(loadedWeather.isDirty() && loadedWind.isDirty(), "Legacy migration was not scheduled for save");
            check(loadedWeather.type() == SkyWeatherType.STORM && loadedWeather.forecastType() == SkyWeatherType.RAIN
                            && loadedWeather.phaseStartedAt() == 4000L && loadedWeather.nextAutomaticChange() == 18000L
                            && loadedWeather.automatic() == automatic,
                    "Legacy weather/forecast/mode/timestamps changed");
            check(loadedWind.directionDegrees() == 271.25F && loadedWind.strength() == 0.75F
                            && loadedWind.nextAutomaticChange() == 14000L && loadedWind.automatic() == automatic,
                    "Legacy wind/mode/deadline changed");
            manager.save();
        }
        NbtCompound savedWeather = readState(directory, "skysigns_weather");
        NbtCompound savedWind = readState(directory, "skysigns_wind");
        assertOriginalFields(weather, savedWeather);
        assertOriginalFields(wind, savedWind);
        check(savedWeather.getInt(SaveDataMigrations.VERSION_KEY, -1) == 2
                        && savedWind.getInt(SaveDataMigrations.VERSION_KEY, -1) == 1,
                "Current schema version was not written to disk");
        check(!weather.contains(SaveDataMigrations.VERSION_KEY), "Migration mutated legacy input");
        try (PersistentStateManager manager = manager(world, directory)) {
            WeatherState loadedWeather = manager.getOrCreate(WeatherState.TYPE);
            WindState loadedWind = manager.getOrCreate(WindState.TYPE);
            check(!loadedWeather.isDirty() && !loadedWind.isDirty(), "Current data migrated again on repeat load");
            check(encode(weatherCodec(world), loadedWeather).equals(savedWeather), "Weather changed on repeat load");
            check(encode(windCodec(world), loadedWind).equals(savedWind), "Wind changed on repeat load");
        }
    }

    private static void verifyDefaults(ServerWorld world) {
        NbtCompound minimalWind = new NbtCompound();
        minimalWind.putFloat("direction", 45.0F);
        minimalWind.putFloat("strength", 0.4F);
        WindState wind = windCodec(world).parse(NbtOps.INSTANCE, minimalWind).getOrThrow();
        NbtCompound saved = encode(windCodec(world), wind);
        check(saved.getFloat("target_direction", -1) == 45.0F && saved.getFloat("target_strength", -1) == 0.4F
                && wind.automatic(), "Missing legacy targets/mode did not use historical defaults");
        WeatherState weather = weatherCodec(world).parse(NbtOps.INSTANCE, new NbtCompound()).getOrThrow();
        check(weather.type() == SkyWeatherType.CLEAR && weather.forecastType() == SkyWeatherType.RAIN
                && weather.automatic(), "Empty legacy weather defaults changed");
        NbtCompound malformedWind = new NbtCompound();
        malformedWind.putFloat("direction", Float.NaN);
        malformedWind.putFloat("strength", Float.POSITIVE_INFINITY);
        malformedWind.putFloat("target_direction", Float.NEGATIVE_INFINITY);
        malformedWind.putFloat("target_strength", Float.NaN);
        WindState repaired = windCodec(world).parse(NbtOps.INSTANCE, malformedWind).getOrThrow();
        repaired.advanceOneTick();
        check(Float.isFinite(repaired.directionDegrees()) && Float.isFinite(repaired.strength()),
                "Non-finite legacy wind reached simulation");
        for (SaveDataMigrations.Kind kind : SaveDataMigrations.Kind.values()) {
            var first = SaveDataMigrations.upgrade(new NbtCompound(), kind);
            var second = SaveDataMigrations.upgrade(first.data(), kind);
            check(first.migrated() && !second.migrated() && first.data().equals(second.data()),
                    "Migration is not idempotent for " + kind);
        }
    }

    private static void verifyUninitializedManual(ServerWorld world) {
        var manager = world.getPersistentStateManager();
        var originalWeather = WeatherManager.get(world); var originalWind = WindManager.get(world);
        try {
            var weather = new WeatherState(); weather.setAutomatic(false);
            var wind = new WindState(); wind.set(359, .77F); wind.setAutomatic(false);
            manager.set(WeatherState.TYPE, weather); manager.set(WindState.TYPE, wind);
            world.getRandom().setSeed(1234);
            long expected = net.minecraft.util.math.random.Random.create(1234).nextLong();
            WeatherManager.beforeWeather(world);
            check(weather.initialized() && !weather.automatic() && weather.type() == WeatherManager.readVanillaWeather(world)
                    && weather.forecastType() == weather.type(), "Initialization replaced saved manual mode with auto");
            check(world.getRandom().nextLong() == expected, "Manual initialization generated an automatic forecast");
            check(!wind.automatic() && wind.directionDegrees() == 359 && wind.strength() == .77F,
                    "Manual initialization changed manual wind");
            SkySigns.LOGGER.info("Uninitialized manual weather keeps its mode and consumes no forecast RNG");
        } finally {
            manager.set(WeatherState.TYPE, originalWeather); manager.set(WindState.TYPE, originalWind);
        }
    }

    private static void verifyMeter(ServerWorld world) {
        for (int value : new int[]{0, 50, 88, 100}) {
            NbtCompound legacy = new NbtCompound();
            legacy.putInt("reading_percent", value);
            MeterBlockEntity meter = meter(world, legacy);
            check(meter.readingPercent() == value, "Legacy meter reading changed: " + value);
            NbtCompound saved = meter.createNbt(world.getRegistryManager());
            NbtCompound data = saved.getCompoundOrEmpty(SaveDataMigrations.METER_DATA_KEY);
            check(data.getInt(SaveDataMigrations.VERSION_KEY, -1) == 1
                    && data.getInt("reading_percent", -1) == value, "Meter schema/reading not saved");
            check(meter(world, saved).createNbt(world.getRegistryManager()).equals(saved),
                    "Meter changed on repeat load");
        }
        check(meter(world, new NbtCompound()).readingPercent() == 50, "Missing meter reading default changed");
        for (int invalid : new int[]{-10, 150}) {
            NbtCompound legacy = new NbtCompound();
            legacy.putInt("reading_percent", invalid);
            check(meter(world, legacy).readingPercent() == (invalid < 0 ? 0 : 100), "Meter clamp changed");
        }
        NbtCompound data = new NbtCompound();
        data.putInt(SaveDataMigrations.VERSION_KEY, 1);
        data.putInt("reading_percent", 88);
        data.putString("unknown_extension", "keep me");
        NbtCompound root = new NbtCompound();
        root.put(SaveDataMigrations.METER_DATA_KEY, data);
        assertOriginalFields(data, meter(world, root).createNbt(world.getRegistryManager())
                .getCompoundOrEmpty(SaveDataMigrations.METER_DATA_KEY));
        data.putInt(SaveDataMigrations.VERSION_KEY, 99);
        MeterBlockEntity future = meter(world, root);
        check(future.isReadOnly(), "Future meter was not protected");
        future.sample(world);
        check(future.createNbt(world.getRegistryManager()).getCompoundOrEmpty(SaveDataMigrations.METER_DATA_KEY)
                .equals(data), "Future meter payload was overwritten");
    }

    private static void verifyFutureProtection(ServerWorld world, Path directory) throws Exception {
        NbtCompound futureWeather = legacyWeather(true);
        NbtCompound futureWind = legacyWind(true);
        futureWeather.putInt(SaveDataMigrations.VERSION_KEY, 99);
        futureWind.putInt(SaveDataMigrations.VERSION_KEY, 99);
        writeState(directory, "skysigns_weather", futureWeather);
        writeState(directory, "skysigns_wind", futureWind);
        WeatherState protectedWeather;
        WindState protectedWind;
        try (PersistentStateManager manager = manager(world, directory)) {
            protectedWeather = manager.getOrCreate(WeatherState.TYPE);
            protectedWind = manager.getOrCreate(WindState.TYPE);
            check(protectedWeather.isReadOnly() && protectedWind.isReadOnly(), "Future data became default writable state");
            expectReadOnly(() -> protectedWeather.setAutomatic(false));
            expectReadOnly(() -> protectedWind.set(10, 0.5F));
            protectedWeather.markDirty();
            protectedWind.markDirty();
            manager.save();
        }
        check(readState(directory, "skysigns_weather").equals(futureWeather), "Future weather changed on forced save");
        check(readState(directory, "skysigns_wind").equals(futureWind), "Future wind changed on forced save");
        var liveManager = world.getPersistentStateManager();
        WeatherState originalWeather = WeatherManager.get(world);
        WindState originalWind = WindManager.get(world);
        try {
            liveManager.set(WeatherState.TYPE, protectedWeather);
            liveManager.set(WindState.TYPE, protectedWind);
            boolean raining = world.getLevelProperties().isRaining();
            WeatherManager.tick(world);
            WindManager.tick(world);
            var dispatcher = world.getServer().getCommandManager().getDispatcher();
            for (String command : new String[]{"skysigns weather set rain", "skysigns weather auto",
                    "skysigns wind set 90 50", "skysigns wind auto"}) {
                check(dispatcher.execute(command, world.getServer().getCommandSource()) == 0,
                        "Command modified future data: " + command);
            }
            check(world.getLevelProperties().isRaining() == raining, "Read-only command changed vanilla weather");
            check(encode(weatherCodec(world), protectedWeather).equals(futureWeather)
                    && encode(windCodec(world), protectedWind).equals(futureWind), "Runtime rewrote future payload");
        } finally {
            liveManager.set(WeatherState.TYPE, originalWeather);
            liveManager.set(WindState.TYPE, originalWind);
        }
        for (SaveDataMigrations.Kind kind : SaveDataMigrations.Kind.values()) {
            NbtCompound invalidVersion = new NbtCompound();
            invalidVersion.putString(SaveDataMigrations.VERSION_KEY, "broken");
            check(SaveDataMigrations.upgrade(invalidVersion, kind).readOnly(), "Invalid version treated as legacy");
            invalidVersion.putInt(SaveDataMigrations.VERSION_KEY, -1);
            check(SaveDataMigrations.upgrade(invalidVersion, kind).readOnly(), "Negative version treated as legacy");
            invalidVersion.putFloat(SaveDataMigrations.VERSION_KEY, 1.5F);
            check(SaveDataMigrations.upgrade(invalidVersion, kind).readOnly(), "Fractional version truncated");
            invalidVersion.putLong(SaveDataMigrations.VERSION_KEY, 4294967297L);
            check(SaveDataMigrations.upgrade(invalidVersion, kind).readOnly(), "Oversized version truncated");
        }
    }

    private static PersistentStateManager manager(ServerWorld world, Path directory) {
        return new PersistentStateManager(new PersistentState.Context(world), directory,
                world.getServer().getDataFixer(), world.getRegistryManager());
    }

    private static Codec<WeatherState> weatherCodec(ServerWorld world) {
        return WeatherState.TYPE.codec().apply(new PersistentState.Context(world));
    }

    private static Codec<WindState> windCodec(ServerWorld world) {
        return WindState.TYPE.codec().apply(new PersistentState.Context(world));
    }

    private static <T> NbtCompound encode(Codec<T> codec, T state) {
        return (NbtCompound) codec.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
    }

    private static MeterBlockEntity meter(ServerWorld world, NbtCompound data) {
        MeterBlockEntity meter = new MeterBlockEntity(BlockPos.ORIGIN, ModBlocks.HYGROMETER.getDefaultState());
        meter.read(NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), data));
        return meter;
    }

    private static NbtCompound legacyWeather(boolean automatic) {
        NbtCompound data = new NbtCompound();
        data.putString("type", "STORM");
        data.putString("forecast", "RAIN");
        data.putLong("phase_started_at", 4000L);
        data.putLong("next_change", 18000L);
        data.putBoolean("automatic", automatic);
        data.putString("unknown_extension", "keep me");
        return data;
    }

    private static NbtCompound legacyWind(boolean automatic) {
        NbtCompound data = new NbtCompound();
        data.putFloat("direction", 271.25F);
        data.putFloat("strength", 0.75F);
        data.putFloat("target_direction", 15.5F);
        data.putFloat("target_strength", 0.9F);
        data.putLong("next_change", 14000L);
        data.putBoolean("automatic", automatic);
        data.putString("unknown_extension", "keep me");
        return data;
    }

    private static void writeState(Path directory, String id, NbtCompound data) throws Exception {
        NbtCompound root = new NbtCompound();
        root.put("data", data.copy());
        NbtHelper.putDataVersion(root);
        NbtIo.writeCompressed(root, directory.resolve(id + ".dat"));
    }

    private static NbtCompound readState(Path directory, String id) throws Exception {
        return NbtIo.readCompressed(directory.resolve(id + ".dat"), NbtSizeTracker.ofUnlimitedBytes())
                .getCompoundOrEmpty("data");
    }

    private static void assertOriginalFields(NbtCompound original, NbtCompound saved) {
        for (String key : original.getKeys()) {
            check(original.get(key).equals(saved.get(key)), "Migration changed original field: " + key);
        }
    }

    private static void expectReadOnly(Runnable mutation) {
        try {
            mutation.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Future schema mutation was allowed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
