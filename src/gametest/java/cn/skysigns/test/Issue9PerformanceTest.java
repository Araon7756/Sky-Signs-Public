package cn.skysigns.test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.InactivityFpsLimit;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import cn.skysigns.registry.ModBlocks;
import cn.skysigns.block.entity.MeterBlockEntity;
import cn.skysigns.weather.AtmosphericReadings;
import cn.skysigns.weather.SkyWeatherType;
import cn.skysigns.weather.WeatherManager;
import cn.skysigns.weather.WindManager;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/** Opt-in disposable singleplayer measurement; never starts a dedicated server. */
@SuppressWarnings("UnstableApiUsage")
final class Issue9PerformanceTest {
    private static final BlockPos GRID = new BlockPos(0, 80, 0);
    private static final double CAMERA_X = 8.0, CAMERA_Y = 84.0, CAMERA_Z = -10.0;
    private static final float CAMERA_YAW = 0.0F, CAMERA_PITCH = 25.0F;
    private static final int[][] ORDERS = {{0, 64, 128}, {64, 128, 0}, {128, 0, 64}};
    private static volatile boolean watching;
    private static volatile String conditionFailure;
    private static volatile boolean expectedFocus;
    private static Issue9Probe.Phase current;

    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (watching && conditionFailure == null) conditionFailure = clientFailure(client);
        });
    }
    private Issue9PerformanceTest() { }

    static void run(ClientGameTestContext context) {
        String mode = System.getenv("SKYSIGNS_ISSUE9_MODE");
        check("preflight".equals(mode) || "diagnostic".equals(mode) || "baseline".equals(mode) || "detailed".equals(mode), "Invalid Issue9 mode");
        boolean validationOnly = "preflight".equals(mode) || "diagnostic".equals(mode);
        String output = System.getenv("SKYSIGNS_ISSUE9_OUTPUT");
        check(output != null && Path.of(output).isAbsolute(), "Absolute output directory is required");
        Path root = Path.of(output);
        Path phaseDir = null;
        try {
            Files.createDirectories(root);
            check(!Files.exists(root.resolve("environment.json")), "Output directory must not contain a previous run");
            Map<String, Object> loadingOptions = context.computeOnClient(Issue9PerformanceTest::clientEnvironment);

            // A saved template, including the weather/wind state, is copied into a new directory per scenario.
            Path original;
            Map<String, Object> templateCamera;
            try (TestSingleplayerContext game = context.worldBuilder().adjustSettings(creator -> creator.setSeed("1")).create()) {
                configureMeasurement(context);
                game.getClientWorld().waitForChunksDownload();
                game.getServer().runOnServer(server -> {
                    ServerWorld world = server.getOverworld();
                    for (int x = -8; x < 24; x++) for (int z = -16; z < 16; z++) {
                        world.setBlockState(new BlockPos(x, 79, z), Blocks.STONE.getDefaultState());
                        for (int y = 80; y < 90; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
                    }
                    WeatherManager.setManual(world, SkyWeatherType.STORM);
                    WindManager.setManual(world, 90, .5F);
                    world.setTimeOfDay(6000);
                });
                game.getServer().runCommand("gamemode spectator @a");
                teleportToDeclaredCamera(context, game);
                context.waitTicks(10);
                templateCamera = context.computeOnClient(Issue9PerformanceTest::camera);
                original = game.getWorldSave().getSaveDirectory();
            }
            Path template = root.resolve("world-template");
            copyWorld(original, template);
            Map<String, Object> environment = context.computeOnClient(client -> clientEnvironment(client));
            environment.put("mode", mode);
            environment.put("javaHome", System.getProperty("java.home"));
            environment.put("javaVersion", System.getProperty("java.runtime.version"));
            environment.put("javaVendor", System.getProperty("java.vendor"));
            environment.put("jvmArgs", ManagementFactory.getRuntimeMXBean().getInputArguments());
            environment.put("jvmStartEpochMs", ManagementFactory.getRuntimeMXBean().getStartTime());
            environment.put("sourceCommit", String.valueOf(System.getenv("SKYSIGNS_ISSUE9_COMMIT")));
            environment.put("seed", 1);
            environment.put("template", template.toString());
            environment.put("grid", Map.of("x", 0, "y", 80, "z", 0, "width", 16, "depth", 8));
            environment.put("declaredCamera", declaredCamera());
            environment.put("templateActualCamera", templateCamera);
            environment.put("warmupServerTicks", 200);
            environment.put("sampleServerTicks", "diagnostic".equals(mode) ? 120 : "preflight".equals(mode) ? 20 : 600);
            environment.put("windowPurpose", validationOnly ? "validation-only" : "formal-measurement");
            environment.put("formalSampleCount", 0);
            environment.put("measurementPart", "A-server-humidity-cost");
            environment.put("formalFrameHarnessApproved", false);
            environment.put("renderDataPurpose", "scheduling-diagnostic-only");
            environment.put("probeHooks", "preflight".equals(mode) || "detailed".equals(mode));
            environment.put("mods", FabricLoader.getInstance().getAllMods().stream().map(mod ->
                    mod.getMetadata().getId() + "=" + mod.getMetadata().getVersion().getFriendlyString()).sorted().toList());
            environment.put("loadingOptions", loadingOptions);
            Issue9Probe.writeJson(root.resolve("environment.json"), environment);

            int rounds = validationOnly ? 1 : 3;
            for (int round = 0; round < rounds; round++) {
                for (int load : "diagnostic".equals(mode) ? new int[]{0} : ORDERS[round]) {
                    phaseDir = root.resolve("round-" + (round + 1) + "-load-" + load);
                    Files.createDirectories(phaseDir);
                    Path scenario = original.getParent().resolve("issue9-" + mode + "-r" + (round + 1)
                            + "-n" + load + "-" + UUID.randomUUID());
                    copyWorld(template, scenario);
                    // Keep initialization on Fabric's defaults; fixed capture options follow a successful load.
                    context.restoreDefaultGameOptions();
                    context.runOnClient(client -> client.getWindow().setWindowedSize(
                            (Integer) loadingOptions.get("windowWidth"), (Integer) loadingOptions.get("windowHeight")));
                    try (TestSingleplayerContext game = new TestWorldSaveImpl(context, scenario).open()) {
                        configureMeasurement(context);
                        game.getClientWorld().waitForChunksDownload();
                        game.getServer().runOnServer(server -> {
                            ServerWorld world = server.getOverworld();
                            check(world.getSeed() == 1, "Seed mismatch");
                            for (int i = 0; i < load; i++) world.setBlockState(GRID.add(i % 16, 0, i / 16), ModBlocks.HYGROMETER.getDefaultState());
                            verifyWorld(world, load, false);
                        });
                        teleportToDeclaredCamera(context, game);
                        context.setScreen(() -> null);
                        conditionFailure = null;
                        expectedFocus = context.computeOnClient(MinecraftClient::isWindowFocused);
                        check(expectedFocus, "Test window must be focused before warmup");
                        watching = true;
                        long warmupElapsed = waitServerTicks(context, game, 200);
                        Issue9Probe.writeJson(phaseDir.resolve("warmup.json"), Map.of("requestedServerTicks", 200,
                                "observedServerTicks", warmupElapsed, "completed", true));
                        check(conditionFailure == null, "Warmup condition: " + conditionFailure);
                        game.getServer().runOnServer(server -> verifyWorld(server.getOverworld(), load, true));
                        Issue9Probe.writeJson(phaseDir.resolve("conditions-before.json"), context.computeOnClient(Issue9PerformanceTest::clientEnvironment));
                        if ("diagnostic".equals(mode)) {
                            boolean constrained = diagnoseFrames(context, game, phaseDir.resolve("beta7-waitTicks"), true);
                            // Do not try to force free-running rendering if the beta.7 path itself is tick-limited.
                            if (!constrained) diagnoseFrames(context, game, phaseDir.resolve("predicate-waitFor"), false);
                            watching = false;
                            Issue9Probe.writeGc(root);
                            continue;
                        }
                        int ticks = "preflight".equals(mode) ? 20 : 600;
                        long startJvmMs = ManagementFactory.getRuntimeMXBean().getUptime();
                        current = game.getServer().computeOnServer(server -> Issue9Probe.arm(ticks, !"baseline".equals(mode)));
                        waitForServerCapture(context);
                        check(conditionFailure == null, "Measured condition: " + conditionFailure);
                        // Synchronize with the render thread before exporting its last buffered sample.
                        context.runOnClient(client -> { });
                        watching = false;
                        current.export(phaseDir);
                        Issue9Probe.writeJson(phaseDir.resolve("window.json"), Map.of("startJvmMs", startJvmMs,
                                "endJvmMs", ManagementFactory.getRuntimeMXBean().getUptime()));
                        Issue9Probe.writeJson(phaseDir.resolve("conditions-after.json"), context.computeOnClient(Issue9PerformanceTest::clientEnvironment));
                        check(current.complete && current.tickCount == ticks && !current.overflow, "Incomplete/overflowed phase");
                        current.validateServerCapture();
                        if (current.detailed) {
                            check(current.callCount == load * (ticks / 20), "Unexpected natural humidity sample count");
                            for (int i = 0; i < current.callCount; i++) {
                                check(current.callData[i][9] == 1 && current.callData[i][5] == 245 && current.callData[i][7] == 0,
                                        "Fluid query count or dry-region invariant mismatch");
                                check(current.callData[i][3] > 0 && current.callData[i][4] > 0 && current.callData[i][6] > 0,
                                        "Mixin timings are missing");
                            }
                        }
                        game.getServer().runOnServer(server -> verifyWorld(server.getOverworld(), load, true));
                        Issue9Probe.writeJson(phaseDir.resolve("result.json"), Map.of("passed", true, "load", load, "round", round + 1,
                                "formalTickSamples", validationOnly ? 0 : current.tickCount,
                                "formalFrameSamples", 0));
                        current = null;
                    }
                    Issue9Probe.writeGc(root);
                }
            }
            Issue9Probe.writeJson(root.resolve("result.json"), Map.of("passed", true, "mode", mode, "rounds", rounds,
                    "formalTickSamples", validationOnly ? 0 : rounds * 3 * 600));
        } catch (Throwable error) {
            watching = false;
            Issue9Probe.abort();
            try {
                if (current != null && phaseDir != null) { context.runOnClient(client -> { }); current.export(phaseDir); }
                StringWriter trace = new StringWriter();
                error.printStackTrace(new PrintWriter(trace));
                Issue9Probe.writeJson(root.resolve("failure.json"), Map.of("passed", false, "error", trace.toString()));
            } catch (Throwable exportError) { error.addSuppressed(exportError); }
            throw new AssertionError("Issue #9 stopped; preserve output at " + root, error);
        } finally {
            watching = false;
            Issue9Probe.abort();
            try { Issue9Probe.writeGc(root); } catch (IOException error) { throw new AssertionError("Cannot preserve GC events", error); }
        }
    }

    private static void configureMeasurement(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.options.getEnableVsync().setValue(true);
            client.options.getInactivityFpsLimit().setValue(InactivityFpsLimit.MINIMIZED);
            client.options.getMaxFps().setValue(120);
            client.options.getViewDistance().setValue(5);
            client.options.getSimulationDistance().setValue(12);
            client.options.getGuiScale().setValue(0);
            client.options.getGraphicsMode().setValue(GraphicsMode.FANCY);
            client.options.getFov().setValue(70);
            client.options.setPerspective(Perspective.FIRST_PERSON);
            client.options.pauseOnLostFocus = true;
            client.getWindow().setWindowedSize(1280, 720);
            client.getWindow().setVsync(true);
        });
        context.waitTicks(10);
        check(context.computeOnClient(client -> client.getWindow().getFramebufferWidth() == 1280
                && client.getWindow().getFramebufferHeight() == 720), "Requested framebuffer size is not active");
    }

    private static boolean diagnoseFrames(ClientGameTestContext context, TestSingleplayerContext game, Path directory,
                                          boolean beta7Wait) throws IOException {
        // Reuse the actual beta.7 capture alongside raw timestamps from the SAME END_MAIN callback.
        PerformanceProbe.Phase legacy = PerformanceProbe.start();
        current = game.getServer().computeOnServer(server -> Issue9Probe.arm(120, false));
        if (beta7Wait) {
            context.waitTicks(120);
            check(current.complete, "beta.7 waitTicks path did not finish 120 server ticks");
        } else {
            context.waitFor(client -> current.complete || conditionFailure != null, 1800);
        }
        PerformanceProbe.stop();
        context.runOnClient(client -> { });
        current.export(directory, true);
        Issue9Probe.writeJson(directory.resolve("conditions.json"), context.computeOnClient(Issue9PerformanceTest::clientEnvironment));
        check(conditionFailure == null, "Diagnostic condition: " + conditionFailure);
        current.validateCapture();
        double hz = current.timestampCount * 1e9 / (current.ended - current.started);
        boolean nearTickRate = hz <= 25;
        Issue9Probe.writeJson(directory.resolve("diagnosis.json"), Map.of("waitMechanism", beta7Wait ? "context.waitTicks(120)" : "context.waitFor(predicate)",
                "callbackHz", hz, "near20Hz", nearTickRate, "formalFrameHarnessApproved", false,
                "formalSampleCount", 0, "beta7TickSummary", legacy.tickSummary(), "beta7FrameIntervalSummary", legacy.frameSummary()));
        current = null;
        return nearTickRate;
    }

    private static void waitForServerCapture(ClientGameTestContext context) {
        long deadline = System.nanoTime() + 120_000_000_000L;
        for (int waited = 0; waited < 1200 && System.nanoTime() < deadline; waited++) {
            check(conditionFailure == null, "Measured condition: " + conditionFailure);
            if (current.complete) return;
            context.waitTick();
        }
        throw new AssertionError("Server capture timed out before target tick count");
    }
    private static long waitServerTicks(ClientGameTestContext context, TestSingleplayerContext game, int ticks) {
        long start = game.getServer().computeOnServer(server -> server.getOverworld().getTime());
        long deadline = System.nanoTime() + 120_000_000_000L;
        // This method is called on the gametest thread. Client predicates must never dispatch server work.
        for (int waited = 0; waited < 1200 && System.nanoTime() < deadline; waited++) {
            check(conditionFailure == null, "Warmup condition: " + conditionFailure);
            long now = game.getServer().computeOnServer(server -> server.getOverworld().getTime());
            if (now - start >= ticks) return now - start;
            context.waitTick();
        }
        throw new AssertionError("Server warmup timed out waiting for " + ticks + " ticks");
    }
    private static void teleportToDeclaredCamera(ClientGameTestContext context, TestSingleplayerContext game) {
        // Explicit decimal coordinates disable Brigadier's block-centering of integer x/z coordinates.
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.3f %.3f",
                CAMERA_X, CAMERA_Y, CAMERA_Z, CAMERA_YAW, CAMERA_PITCH));
        for (int waited = 0; waited < 100; waited++) {
            if (context.computeOnClient(Issue9PerformanceTest::cameraMatches)) return;
            context.waitTick();
        }
        throw new AssertionError("Declared camera position was not synchronized within 100 client ticks");
    }
    private static boolean cameraMatches(MinecraftClient client) {
        return client.player != null && Math.abs(client.player.getX() - CAMERA_X) <= .01
                && Math.abs(client.player.getY() - CAMERA_Y) <= .01 && Math.abs(client.player.getZ() - CAMERA_Z) <= .01
                && Math.abs(client.player.getYaw() - CAMERA_YAW) <= .01 && Math.abs(client.player.getPitch() - CAMERA_PITCH) <= .01;
    }
    private static Map<String, Object> declaredCamera() {
        return Map.of("x", CAMERA_X, "y", CAMERA_Y, "z", CAMERA_Z, "yaw", CAMERA_YAW, "pitch", CAMERA_PITCH);
    }
    private static Map<String, Object> camera(MinecraftClient client) {
        check(client.player != null, "Player is missing when recording camera");
        return Map.of("x", client.player.getX(), "y", client.player.getY(), "z", client.player.getZ(),
                "yaw", client.player.getYaw(), "pitch", client.player.getPitch());
    }
    private static void verifyWorld(ServerWorld world, int load, boolean verifyReadings) {
        check(WeatherManager.get(world).type() == SkyWeatherType.STORM && !WeatherManager.get(world).automatic(), "Weather mismatch");
        check(!WindManager.get(world).automatic() && WindManager.get(world).directionDegrees() == 90
                && WindManager.get(world).strength() == .5F, "Wind mismatch");
        for (int i = 0; i < 128; i++) {
            BlockPos pos = GRID.add(i % 16, 0, i / 16);
            check(world.getBlockState(pos).isOf(i < load ? ModBlocks.HYGROMETER : Blocks.AIR), "Grid mismatch at " + pos);
            if (i < load && verifyReadings) {
                check(world.getBlockEntity(pos) instanceof MeterBlockEntity meter
                        && meter.readingPercent() == AtmosphericReadings.humidityPercent(world, pos), "Reading mismatch");
            }
        }
        for (int x = -3; x <= 18; x++) for (int y = 78; y <= 82; y++) for (int z = -3; z <= 10; z++) {
            check(world.getFluidState(new BlockPos(x, y, z)).isEmpty(), "Waterless scan region changed");
        }
    }
    private static String clientFailure(MinecraftClient client) {
        if (client.world == null || client.player == null || client.currentScreen != null || client.isPaused()) return "World/screen/pause state changed";
        if (client.isWindowFocused() != expectedFocus) return "Window focus changed";
        if (!client.getInactivityFpsLimiter().getLimitReason().toString().equals("NONE")) return "Inactivity limiter active";
        if (client.getWindow().getFramebufferWidth() != 1280 || client.getWindow().getFramebufferHeight() != 720) return "Framebuffer changed";
        if (GLFW.glfwGetWindowAttrib(client.getWindow().getHandle(), GLFW.GLFW_ICONIFIED) != 0) return "Window minimized";
        if (!client.options.getEnableVsync().getValue() || client.options.getMaxFps().getValue() != 120
                || client.options.getViewDistance().getValue() != 5 || client.options.getSimulationDistance().getValue() != 12
                || client.options.getGuiScale().getValue() != 0) return "Fixed rendering options changed";
        if (!cameraMatches(client)) return "Camera changed";
        return null;
    }
    private static Map<String, Object> clientEnvironment(MinecraftClient client) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("framebufferWidth", client.getWindow().getFramebufferWidth());
        map.put("framebufferHeight", client.getWindow().getFramebufferHeight());
        map.put("windowWidth", client.getWindow().getWidth()); map.put("windowHeight", client.getWindow().getHeight());
        map.put("guiWidth", client.getWindow().getScaledWidth()); map.put("guiHeight", client.getWindow().getScaledHeight());
        map.put("vsync", client.options.getEnableVsync().getValue()); map.put("maxFps", client.options.getMaxFps().getValue());
        map.put("renderDistance", client.options.getViewDistance().getValue());
        map.put("simulationDistance", client.options.getSimulationDistance().getValue());
        map.put("guiScale", client.options.getGuiScale().getValue()); map.put("graphics", client.options.getGraphicsMode().getValue().toString());
        map.put("focused", client.isWindowFocused()); map.put("pauseOnLostFocus", client.options.pauseOnLostFocus);
        map.put("gpuVendor", String.valueOf(GL11.glGetString(GL11.GL_VENDOR)));
        map.put("gpuRenderer", String.valueOf(GL11.glGetString(GL11.GL_RENDERER)));
        map.put("glVersion", String.valueOf(GL11.glGetString(GL11.GL_VERSION)));
        if (client.player != null) map.put("camera", camera(client));
        return map;
    }
    private static void copyWorld(Path source, Path target) throws IOException {
        check(!Files.exists(target), "Refusing to overwrite a world/output directory: " + target);
        try (var paths = Files.walk(source)) {
            for (Path file : paths.toList()) {
                if (file.getFileName().toString().equals("session.lock")) continue;
                Path destination = target.resolve(source.relativize(file));
                if (Files.isDirectory(file)) Files.createDirectories(destination); else Files.copy(file, destination);
            }
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
