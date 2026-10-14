package cn.skysigns.weather;

import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;

public final class AtmosphericReadings {
    private AtmosphericReadings() {
    }

    public static Snapshot measure(ServerWorld world, BlockPos pos) {
        WeatherManager.View weather = WeatherManager.view(world);
        float pressure = pressureHpa(world, weather);
        int humidity = humidity(world, pos, weather);
        Text trend = pressureTrend(weather);
        Text forecast = WeatherManager.forecast(weather);
        return new Snapshot(pressure, humidity, trend, forecast, localPrecipitation(world, pos));
    }

    /** Pressure-only meter sampling does not need a biome or 245 fluid lookups. */
    public static float pressureHpa(ServerWorld world) {
        return pressureHpa(world, WeatherManager.view(world));
    }

    /** A barometer interaction only needs pressure and its trend, not humidity or nearby water. */
    public static Text pressureText(ServerWorld world) {
        WeatherManager.View weather = WeatherManager.view(world);
        Text trend = pressureTrend(weather);
        return formatPressure(pressureHpa(world, weather), trend);
    }

    public static int humidityPercent(ServerWorld world, BlockPos pos) {
        return humidity(world, pos, WeatherManager.view(world));
    }

    public static Text humidityText(int humidityPercent) {
        return Text.translatable("reading.skysigns.humidity", humidityPercent,
                Snapshot.humidityLevel(humidityPercent));
    }

    private static Text localPrecipitation(ServerWorld world, BlockPos pos) {
        if (!world.isRaining()) {
            return Text.translatable("reading.skysigns.local.none");
        }

        // Observe above the instrument so the instrument block does not shelter itself.
        BlockPos samplePos = pos.up();
        return switch (world.getPrecipitation(samplePos)) {
            case RAIN -> Text.translatable("reading.skysigns.local.rain");
            case SNOW -> Text.translatable("reading.skysigns.local.snow");
            case NONE -> world.getBiome(samplePos).value().hasPrecipitation()
                    ? Text.translatable("reading.skysigns.local.sheltered")
                    : Text.translatable("reading.skysigns.local.biome_none");
        };
    }

    private static float pressureHpa(ServerWorld world, WeatherManager.View weather) {
        float wobble = (float) Math.sin(((double) world.getTime() + dimensionOffset(world)) / 360.0) * 1.2F;
        return weather.bases().pressure() + wobble;
    }

    private static int humidity(ServerWorld world, BlockPos pos, WeatherManager.View weather) {
        Biome biome = world.getBiome(pos).value();
        float base = biome.hasPrecipitation() ? weather.bases().wetHumidity() : weather.bases().dryHumidity();
        return humidityForClimate(base, biome.hasPrecipitation(), biome.weather.downfall(),
                biome.getTemperature(), pos.getY(), hasNearbyWater(world, pos));
    }

    /** Uses biome data, not biome IDs, so worldgen mods and datapacks work without name lists. */
    static int humidityForClimate(SkyWeatherType weather, boolean hasPrecipitation,
                                  float downfall, float temperature, int altitude, boolean nearbyWater) {
        var base = WeatherPlan.Bases.of(weather);
        return humidityForClimate(hasPrecipitation ? base.wetHumidity() : base.dryHumidity(),
                hasPrecipitation, downfall, temperature, altitude, nearbyWater);
    }

    static int humidityForClimate(float value, boolean hasPrecipitation,
                                  float downfall, float temperature, int altitude, boolean nearbyWater) {
        // Datapacks can use values outside 0..1; keep them within the climate model's range.
        value += Math.round((MathHelper.clamp(downfall, 0.0F, 1.0F) - 0.5F) * 20.0F);
        if (!hasPrecipitation) {
            value -= 4;
        }
        if (temperature > 1.2F) {
            value -= 8;
        } else if (temperature < 0.2F) {
            value -= 3;
        }
        if (altitude > 120) {
            value -= Math.min(12, (altitude - 120) / 12);
        }
        if (nearbyWater) {
            value += 8;
        }

        return MathHelper.clamp(Math.round(value), 5, 100);
    }

    private static boolean hasNearbyWater(ServerWorld world, BlockPos origin) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (world.getFluidState(cursor).isIn(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Text pressureTrend(WeatherManager.View view) {
        double delta = view.pressureDelta();
        if (Math.abs(delta) < 0.000001) return Text.translatable("reading.skysigns.pressure.stable");
        return Text.translatable(delta < 0 ? "reading.skysigns.pressure.falling" : "reading.skysigns.pressure.rising");
    }

    private static int dimensionOffset(ServerWorld world) {
        return world.getRegistryKey().getValue().hashCode() & 2047;
    }

    public record Snapshot(float pressureHpa, int humidityPercent, Text pressureTrend,
                           Text forecast, Text localPrecipitation) {
        public Text pressureText() {
            return formatPressure(pressureHpa, pressureTrend);
        }

        public Text humidityText() {
            return AtmosphericReadings.humidityText(humidityPercent);
        }

        private static Text humidityLevel(int humidity) {
            if (humidity < 30) return Text.translatable("reading.skysigns.humidity.dry");
            if (humidity < 60) return Text.translatable("reading.skysigns.humidity.comfortable");
            if (humidity < 80) return Text.translatable("reading.skysigns.humidity.humid");
            return Text.translatable("reading.skysigns.humidity.very_humid");
        }
    }

    private static Text formatPressure(float pressure, Text trend) {
        return Text.translatable("reading.skysigns.pressure",
                String.format(java.util.Locale.ROOT, "%.1f", pressure), trend);
    }
}
