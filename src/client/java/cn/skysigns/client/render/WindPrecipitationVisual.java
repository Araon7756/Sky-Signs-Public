package cn.skysigns.client.render;

import cn.skysigns.SkySigns;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/** Client-only offsets for vanilla precipitation quads. No extra particles are spawned. */
public final class WindPrecipitationVisual {
    // A full-strength wind should read clearly on an eight-block rain column
    // without moving its top so far that nearby weather columns look detached.
    private static final float MAX_TILT = 2.4F;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("skysigns-client.properties");

    private static boolean enabled = true;
    private static float visualStrength = 1.0F;
    private static ClientWorld trackedWorld;
    private static float targetX;
    private static float targetZ;
    private static float currentX;
    private static float currentZ;
    private static boolean irisPresent;
    private static boolean shaderPackActive;
    private static Object irisApi;
    private static Method irisShaderPackInUse;

    private WindPrecipitationVisual() {
    }

    public static void initialize() {
        initializeShaderCompatibility();
        Properties properties = new Properties();
        if (Files.notExists(CONFIG_PATH)) {
            try {
                Files.createDirectories(CONFIG_PATH.getParent());
                Files.writeString(CONFIG_PATH,
                        "# Restart Minecraft after changing these values.\n"
                                + "wind_precipitation_enabled=true\n"
                                + "wind_precipitation_strength=1.0\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException exception) {
                SkySigns.LOGGER.warn("Could not create client weather config {}", CONFIG_PATH, exception);
            }
        }
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
                properties.load(input);
            } catch (IOException exception) {
                SkySigns.LOGGER.warn("Could not read client weather config {}", CONFIG_PATH, exception);
            }
        }
        enabled = Boolean.parseBoolean(properties.getProperty("wind_precipitation_enabled", "true"));
        try {
            visualStrength = MathHelper.clamp(
                    Float.parseFloat(properties.getProperty("wind_precipitation_strength", "1.0")),
                    0.0F, 1.0F);
            if (!Float.isFinite(visualStrength)) {
                visualStrength = 1.0F;
            }
        } catch (NumberFormatException exception) {
            SkySigns.LOGGER.warn("Invalid wind_precipitation_strength; using 1.0");
            visualStrength = 1.0F;
        }
    }

    public static void accept(ClientWorld world, float directionDegrees, float strength) {
        if (world == null || !Float.isFinite(directionDegrees) || !Float.isFinite(strength)) {
            return;
        }
        if (trackedWorld != world) {
            reset();
            trackedWorld = world;
        }
        float radians = (float) Math.toRadians(directionDegrees);
        float tilt = MAX_TILT * visualStrength * MathHelper.clamp(strength, 0.0F, 1.0F);
        // The top is upwind of the bottom: a northward wind leans the top south.
        targetX = -(float) Math.sin(radians) * tilt;
        targetZ = (float) Math.cos(radians) * tilt;
    }

    public static void tick(MinecraftClient client) {
        updateShaderCompatibility();
        if (client.world == null) {
            reset();
            return;
        }
        if (trackedWorld != client.world) {
            reset();
            trackedWorld = client.world;
        }
        currentX += (targetX - currentX) * 0.2F;
        currentZ += (targetZ - currentZ) * 0.2F;
    }

    public static float topOffsetX(int height) {
        return enabled && !shaderPackActive
                ? currentX * MathHelper.clamp(height / 8.0F, 0.0F, 1.0F) : 0.0F;
    }

    public static float topOffsetZ(int height) {
        return enabled && !shaderPackActive
                ? currentZ * MathHelper.clamp(height / 8.0F, 0.0F, 1.0F) : 0.0F;
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
        } catch (ReflectiveOperationException | LinkageError exception) {
            // If the optional API is unavailable, leave vanilla precipitation untouched.
            shaderPackActive = true;
            SkySigns.LOGGER.warn("Could not query Iris shader state; disabling precipitation tilt for compatibility", exception);
        }
    }

    private static void updateShaderCompatibility() {
        if (!irisPresent || irisShaderPackInUse == null) {
            return;
        }
        try {
            shaderPackActive = Boolean.TRUE.equals(irisShaderPackInUse.invoke(irisApi));
        } catch (ReflectiveOperationException | LinkageError exception) {
            irisShaderPackInUse = null;
            shaderPackActive = true;
            SkySigns.LOGGER.warn("Could not query Iris shader state; disabling precipitation tilt for compatibility", exception);
        }
    }

    public static void reset() {
        trackedWorld = null;
        targetX = 0.0F;
        targetZ = 0.0F;
        currentX = 0.0F;
        currentZ = 0.0F;
    }
}
