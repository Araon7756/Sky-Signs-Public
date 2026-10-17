package cn.skysigns.client;

import net.minecraft.client.MinecraftClient;

/** Captured before queueing a callback. The unchanged payload cannot identify packets already late at capture. */
public record ClientSession(Object world, Object connection) {
    public static ClientSession capture(MinecraftClient client) {
        return new ClientSession(client.world, client.getNetworkHandler());
    }

    public boolean matches(MinecraftClient client) {
        return matches(client.world, client.getNetworkHandler());
    }

    public boolean matches(Object world, Object connection) {
        return this.world != null && this.connection != null && this.world == world && this.connection == connection;
    }
}
