package cn.skysigns.registry;

import cn.skysigns.SkySigns;
import cn.skysigns.block.WeatherVaneBlock;
import cn.skysigns.block.WeatherStationBlock;
import cn.skysigns.block.ObservationInstrumentBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public final class ModBlocks {
    public static final Block WEATHER_VANE = registerWithItem(
            "weather_vane",
            WeatherVaneBlock::new,
            AbstractBlock.Settings.create().strength(1.5F).nonOpaque()
    );
    public static final Block BAROMETER = registerWithItem(
            "barometer",
            settings -> new ObservationInstrumentBlock(settings, ObservationInstrumentBlock.Kind.BAROMETER),
            AbstractBlock.Settings.create().strength(1.0F).nonOpaque()
    );
    public static final Block HYGROMETER = registerWithItem(
            "hygrometer",
            settings -> new ObservationInstrumentBlock(settings, ObservationInstrumentBlock.Kind.HYGROMETER),
            AbstractBlock.Settings.create().strength(1.0F).nonOpaque()
    );
    public static final Block WEATHER_STATION = registerWithItem(
            "weather_station",
            WeatherStationBlock::new,
            AbstractBlock.Settings.create().strength(2.0F).nonOpaque()
    );

    private ModBlocks() {
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(WEATHER_VANE);
            entries.add(BAROMETER);
            entries.add(HYGROMETER);
            entries.add(WEATHER_STATION);
        });
    }

    private static Block registerWithItem(
            String path,
            Function<AbstractBlock.Settings, Block> factory,
            AbstractBlock.Settings settings
    ) {
        Identifier id = Identifier.of(SkySigns.MOD_ID, path);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);

        Block block = factory.apply(settings.registryKey(blockKey));
        Registry.register(Registries.BLOCK, blockKey, block);
        Registry.register(Registries.ITEM, itemKey, new BlockItem(
                block,
                new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey()
        ));
        return block;
    }
}
