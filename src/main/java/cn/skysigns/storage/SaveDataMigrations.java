package cn.skysigns.storage;

import cn.skysigns.SkySigns;
import cn.skysigns.weather.WeatherPlan;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;

/** Sky Signs schema versions are independent of Minecraft's outer DataVersion. */
public final class SaveDataMigrations {
    public static final String VERSION_KEY = "skysigns_data_version";
    public static final String METER_DATA_KEY = "skysigns_data";

    public enum Kind {
        WEATHER(2), WIND(1), METER(1);

        private final int currentVersion;

        Kind(int currentVersion) {
            this.currentVersion = currentVersion;
        }

        public int currentVersion() {
            return currentVersion;
        }
    }

    public record Result(NbtCompound data, int sourceVersion, boolean migrated, boolean readOnly) {}

    private SaveDataMigrations() {}

    public static Result upgrade(NbtCompound source, Kind kind) {
        // Never mutate the input: callers may still need it for backups or diagnostics.
        NbtCompound data = source.copy();
        int version = !source.contains(VERSION_KEY) ? 0
                : source.get(VERSION_KEY) instanceof NbtInt ? source.getInt(VERSION_KEY, -1) : -1;
        if (version < 0 || version > kind.currentVersion()) {
            SkySigns.LOGGER.warn("Sky Signs {} save schema {} is unsupported (supported: 0..{}). "
                            + "Keeping original data read-only; use a compatible mod version or restore a backup.",
                    kind, version, kind.currentVersion());
            return new Result(data, version, false, true);
        }
        int originalVersion = version;
        while (version < kind.currentVersion()) {
            try {
                switch (version) {
                    case 0 -> migrateLegacy(data, kind);
                    case 1 -> {
                        if (kind != Kind.WEATHER) throw new IllegalArgumentException("Missing migration");
                        migrateWeatherPlan(data);
                    }
                    default -> {
                        // PersistentStateManager can replace failed decodes with defaults.
                        // A missing migration must therefore return a protected original,
                        // not throw and risk resetting the save through that fallback.
                        SkySigns.LOGGER.error("Missing Sky Signs {} migration from schema {}; preserving source read-only",
                                kind, version);
                        return new Result(source.copy(), originalVersion, false, true);
                    }
                }
                data.putInt(VERSION_KEY, ++version);
            } catch (IllegalArgumentException | ArithmeticException invalid) {
                SkySigns.LOGGER.warn("Invalid Sky Signs {} schema: {}; preserving original read-only", kind, invalid.getMessage());
                return new Result(source.copy(), originalVersion, false, true);
            }
        }
        return new Result(data, originalVersion, originalVersion != version, false);
    }

    private static void migrateLegacy(NbtCompound data, Kind kind) {
        // v0 is the unversioned Alpha/Beta schema. Preserve existing values exactly.
        switch (kind) {
            case WEATHER -> {
                putDefault(data, "type", "CLEAR");
                putDefault(data, "forecast", "RAIN");
                if (!data.contains("phase_started_at")) data.putLong("phase_started_at", 0L);
                if (!data.contains("next_change")) data.putLong("next_change", 0L);
                if (!data.contains("automatic")) data.putBoolean("automatic", true);
                if (!data.contains("initialized")) data.putBoolean("initialized", true);
            }
            case WIND -> {
                if (!data.contains("direction")) data.putFloat("direction", 0.0F);
                if (!data.contains("strength")) data.putFloat("strength", 0.0F);
                if (!data.contains("target_direction")) {
                    data.putFloat("target_direction", data.getFloat("direction", 0.0F));
                }
                if (!data.contains("target_strength")) {
                    data.putFloat("target_strength", data.getFloat("strength", 0.0F));
                }
                if (!data.contains("next_change")) data.putLong("next_change", 0L);
                if (!data.contains("automatic")) data.putBoolean("automatic", true);
                if (!data.contains("initialized")) data.putBoolean("initialized", true);
            }
            case METER -> {
                if (!data.contains("reading_percent")) data.putInt("reading_percent", 50);
            }
        }
    }

    private static void migrateWeatherPlan(NbtCompound data) {
        var type = WeatherPlan.type(data, "type");
        var target = WeatherPlan.type(data, "forecast");
        long s = WeatherPlan.time(data, "phase_started_at"), t = WeatherPlan.time(data, "next_change");
        boolean automatic = WeatherPlan.flag(data, "automatic");
        WeatherPlan.flag(data, "initialized");
        long length = Math.subtractExact(t, s);
        if (length < 0) throw new IllegalArgumentException("Reversed legacy deadline");
        WeatherPlan plan = new WeatherPlan(type, target, s, t - Math.min(1200, length / 2), t, t,
                WeatherPlan.Curve.LEGACY, s, s, WeatherPlan.Bases.of(type));
        plan.validate(automatic);
        plan.write(data);
        data.putBoolean("paused", false);
        data.putLong("pause_at", 0);
    }

    private static void putDefault(NbtCompound data, String key, String value) {
        if (!data.contains(key)) data.putString(key, value);
    }

    public static void requireWritable(boolean readOnly, Kind kind) {
        if (readOnly) throw new IllegalStateException("Unsupported Sky Signs " + kind + " save schema is read-only");
    }
}
