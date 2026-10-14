package cn.skysigns.registry;

import cn.skysigns.SkySigns;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModItems {
    public static final Item WEATHER_VANE_ROTOR = registerInternal("weather_vane_rotor");
    public static final Item BAROMETER_NEEDLE = registerInternal("barometer_needle");
    public static final Item HYGROMETER_NEEDLE = registerInternal("hygrometer_needle");

    private ModItems() {
    }

    public static void initialize() {
        // Static initialization performs registration.
    }

    private static Item registerInternal(String path) {
        Identifier id = Identifier.of(SkySigns.MOD_ID, path);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        Item item = new Item(new Item.Settings().registryKey(key));
        return Registry.register(Registries.ITEM, key, item);
    }
}
