package cn.skysigns.registry;

import cn.skysigns.SkySigns;
import cn.skysigns.block.entity.WeatherVaneBlockEntity;
import cn.skysigns.block.entity.MeterBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {
    public static final BlockEntityType<WeatherVaneBlockEntity> WEATHER_VANE = registerWeatherVane();
    public static final BlockEntityType<MeterBlockEntity> METER = registerMeter();

    private ModBlockEntities() {
    }

    public static void initialize() {
        // Static initialization performs registration.
    }

    private static BlockEntityType<WeatherVaneBlockEntity> registerWeatherVane() {
        Identifier id = Identifier.of(SkySigns.MOD_ID, "weather_vane");
        RegistryKey<BlockEntityType<?>> key = RegistryKey.of(RegistryKeys.BLOCK_ENTITY_TYPE, id);
        BlockEntityType<WeatherVaneBlockEntity> type = FabricBlockEntityTypeBuilder
                .create(WeatherVaneBlockEntity::new, ModBlocks.WEATHER_VANE)
                .build();
        return Registry.register(Registries.BLOCK_ENTITY_TYPE, key, type);
    }

    private static BlockEntityType<MeterBlockEntity> registerMeter() {
        Identifier id = Identifier.of(SkySigns.MOD_ID, "meter");
        RegistryKey<BlockEntityType<?>> key = RegistryKey.of(RegistryKeys.BLOCK_ENTITY_TYPE, id);
        BlockEntityType<MeterBlockEntity> type = FabricBlockEntityTypeBuilder
                .create(MeterBlockEntity::new, ModBlocks.BAROMETER, ModBlocks.HYGROMETER)
                .build();
        return Registry.register(Registries.BLOCK_ENTITY_TYPE, key, type);
    }
}
