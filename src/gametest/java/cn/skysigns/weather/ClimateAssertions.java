package cn.skysigns.weather;

import cn.skysigns.SkySigns;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/** GameTest-only checks with climate extremes found in biome datapacks. */
public final class ClimateAssertions {
    private ClimateAssertions() {
    }

    public static void verify() {
        int rainforest = AtmosphericReadings.humidityForClimate(
                SkyWeatherType.RAIN, true, 0.9F, 0.95F, 80, false);
        int aridDesert = AtmosphericReadings.humidityForClimate(
                SkyWeatherType.RAIN, false, 0.0F, 2.0F, 80, false);
        check(rainforest > aridDesert + 40, "Rainforest and dry biome humidity are too similar");

        // Terralith includes both negative downfall and values above one.
        int steppe = AtmosphericReadings.humidityForClimate(
                SkyWeatherType.CLEAR, true, -0.5F, 0.4F, 80, false);
        int zeroDownfall = AtmosphericReadings.humidityForClimate(
                SkyWeatherType.CLEAR, true, 0.0F, 0.4F, 80, false);
        int snowyTaiga = AtmosphericReadings.humidityForClimate(
                SkyWeatherType.STORM, false, 1.2F, 0.13F, 80, false);
        int oneDownfall = AtmosphericReadings.humidityForClimate(
                SkyWeatherType.STORM, false, 1.0F, 0.13F, 80, false);
        check(steppe == zeroDownfall && snowyTaiga == oneDownfall,
                "Datapack downfall outside 0..1 was not clamped");

        int highPeak = AtmosphericReadings.humidityForClimate(
                SkyWeatherType.RAIN, true, 0.9F, 0.95F, 300, false);
        int besideWater = AtmosphericReadings.humidityForClimate(
                SkyWeatherType.RAIN, true, 0.9F, 0.95F, 80, true);
        check(highPeak < rainforest && highPeak >= rainforest - 12,
                "High terrain humidity adjustment exceeded its cap");
        check(besideWater == rainforest + 8, "Nearby water no longer affects humidity");
    }

    public static void verifyTerralithRegistry(ServerWorld world) {
        if (!FabricLoader.getInstance().isModLoaded("terralith")) {
            return;
        }
        Registry<Biome> biomes = world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
        long count = biomes.getIds().stream().filter(id -> id.getNamespace().equals("terralith")).count();
        check(count >= 90, "Terralith loaded but its biome registry is missing: " + count);
        Biome rainforest = biomes.get(Identifier.of("terralith", "amethyst_rainforest"));
        Biome mesa = biomes.get(Identifier.of("terralith", "white_mesa"));
        check(rainforest != null && mesa != null, "Representative Terralith biomes are missing");
        int wet = AtmosphericReadings.humidityForClimate(SkyWeatherType.RAIN,
                rainforest.hasPrecipitation(), rainforest.weather.downfall(), rainforest.getTemperature(), 80, false);
        int dry = AtmosphericReadings.humidityForClimate(SkyWeatherType.RAIN,
                mesa.hasPrecipitation(), mesa.weather.downfall(), mesa.getTemperature(), 80, false);
        check(wet > dry + 40, "Registered Terralith biomes did not produce distinct humidity: " + wet + "/" + dry);
        Biome iceMarsh = biomes.get(Identifier.of("terralith", "ice_marsh"));
        check(iceMarsh != null, "Terralith ice marsh biome is missing");
        BlockPos highPos = new BlockPos(0, world.getSeaLevel() + 100, 0);
        var rainforestPrecipitation = rainforest.getPrecipitation(highPos, world.getSeaLevel());
        var iceMarshPrecipitation = iceMarsh.getPrecipitation(highPos, world.getSeaLevel());
        var mesaPrecipitation = mesa.getPrecipitation(highPos, world.getSeaLevel());
        check(rainforestPrecipitation != Biome.Precipitation.NONE
                        && iceMarshPrecipitation != Biome.Precipitation.NONE
                        && mesaPrecipitation == Biome.Precipitation.NONE,
                "Unexpected high-altitude precipitation: rainforest=" + rainforestPrecipitation
                        + ", ice_marsh=" + iceMarshPrecipitation + ", white_mesa=" + mesaPrecipitation);
        SkySigns.LOGGER.info("Client GameTest high-altitude precipitation: rainforest {}, ice marsh {}, mesa {}",
                rainforestPrecipitation, iceMarshPrecipitation, mesaPrecipitation);
        SkySigns.LOGGER.info("Client GameTest Terralith climate: {} biomes, rainforest humidity {}, mesa humidity {}",
                count, wet, dry);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
