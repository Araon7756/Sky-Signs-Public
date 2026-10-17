package cn.skysigns.client.render;

import cn.skysigns.SkySigns;
import cn.skysigns.client.ClientWindState;
import cn.skysigns.client.SkySignsClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Method;

/** Client-only offsets for vanilla precipitation quads. No extra particles are spawned. */
public final class WindPrecipitationVisual {
    private static final float MAX_TILT = 2.4F;
    private static boolean irisPresent;
    private static boolean shaderPackActive;
    private static boolean apiFailed;
    private static Object irisApi;
    private static Method irisShaderPackInUse;

    private WindPrecipitationVisual() {}

    public static void initialize() { initializeShaderCompatibility(); }
    public static void tick(MinecraftClient client) { updateShaderCompatibility(); }

    /** Translation key, or null while tilt is available. Preferences and shared wind remain intact. */
    public static String unavailableReason() {
        if (apiFailed) return "screen.skysigns.settings.iris_error";
        return shaderPackActive ? "screen.skysigns.settings.iris_active" : null;
    }

    private static float tilt(int height) {
        var client = MinecraftClient.getInstance();
        var wind = ClientWindState.INSTANCE;
        var config = SkySignsClient.config();
        return config.enabled() && unavailableReason() == null
                && wind.belongsTo(client.world, client.getNetworkHandler())
                ? MAX_TILT * config.strength() * wind.smoothStrength()
                    * MathHelper.clamp(height / 8.0F, 0.0F, 1.0F) : 0.0F;
    }

    public static float topOffsetX(int height) {
        return -(float) Math.sin(Math.toRadians(ClientWindState.INSTANCE.smoothDirection())) * tilt(height);
    }

    public static float topOffsetZ(int height) {
        return (float) Math.cos(Math.toRadians(ClientWindState.INSTANCE.smoothDirection())) * tilt(height);
    }

    private static void initializeShaderCompatibility() {
        irisPresent = FabricLoader.getInstance().isModLoaded("iris");
        if (!irisPresent) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisApi = apiClass.getMethod("getInstance").invoke(null);
            irisShaderPackInUse = apiClass.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            // If the optional API is unavailable, leave vanilla precipitation untouched.
            shaderPackActive = true;
            apiFailed = true;
            SkySigns.LOGGER.warn("Could not query Iris shader state; disabling precipitation tilt for compatibility", exception);
        }
    }

    private static void updateShaderCompatibility() {
        if (!irisPresent || irisShaderPackInUse == null) {
            return;
        }
        try {
            Object result = irisShaderPackInUse.invoke(irisApi);
            if (!(result instanceof Boolean active)) throw new IllegalStateException("Unexpected Iris API response");
            shaderPackActive = active;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            irisShaderPackInUse = null;
            shaderPackActive = true;
            apiFailed = true;
            SkySigns.LOGGER.warn("Could not query Iris shader state; disabling precipitation tilt for compatibility", exception);
        }
    }

}
