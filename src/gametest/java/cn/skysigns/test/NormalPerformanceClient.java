package cn.skysigns.test;

import cn.skysigns.SkySigns;
import cn.skysigns.block.entity.MeterBlockEntity;
import cn.skysigns.registry.ModBlocks;
import cn.skysigns.weather.AtmosphericReadings;
import cn.skysigns.weather.WeatherManager;
import cn.skysigns.weather.SkyWeatherType;
import cn.skysigns.weather.WindManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.InactivityFpsLimit;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.GameMode;
import org.lwjgl.opengl.GL11;

/** Opt-in ordinary integrated client. All worlds must be disposable launcher-created copies. */
public final class NormalPerformanceClient implements ClientModInitializer {
    private static final BlockPos GRID = new BlockPos(0, 80, 0);
    private static final boolean PREFLIGHT = "1".equals(System.getenv("SKYSIGNS_NORMAL_PREFLIGHT"));
    private static final int[] LOADS = PREFLIGHT ? new int[]{0, 64, 128} : new int[]{0, 64, 128, 64, 128, 0, 128, 0, 64};
    private static final int SAMPLE_TICKS = PREFLIGHT ? 20 : 600;
    private static final int WARMUP_TICKS = PREFLIGHT ? 40 : 200;
    private volatile String state = "LOAD";
    private volatile Issue9Probe.Phase phase;
    private CompletableFuture<Void> prepared;
    private Path output;
    private boolean launched;
    private int window, warmup;
    private long started;

    @Override public void onInitializeClient() {
        if (!"1".equals(System.getenv("SKYSIGNS_NORMAL_PERF"))) return;
        require(System.getProperty("fabric.client.gametest") == null, "Ordinary run must not enable GameTest");
        require(System.getenv("SKYSIGNS_ISSUE9_MODE") == null
                && System.getenv("SKYSIGNS_NETWORK_DIAGNOSTICS") == null
                && System.getenv("SKYSIGNS_NETWORK_WAIT_GUARD") == null
                && System.getenv("SKYSIGNS_NETWORK_SELFTEST") == null,
                "Ordinary frame measurement must not enable GameTest diagnostics, guards or selftests");
        output = Path.of(System.getenv("SKYSIGNS_NORMAL_OUTPUT"));
        require(output.isAbsolute() && !Files.exists(output), "Require a new absolute output directory");
        try { Files.createDirectories(output); } catch (Exception e) { throw new RuntimeException(e); }
        Issue9Probe.abort(); // register probe callbacks before the controller
        started = System.nanoTime();
        SkySigns.LOGGER.info("SKYSIGNS_NORMAL enabled; GameTest property absent; preflight={}", PREFLIGHT);
        ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
        ServerTickEvents.END_WORLD_TICK.register(this::serverTick);
    }

    private void clientTick(MinecraftClient client) {
        try {
            if (state.equals("DONE") || state.equals("FAILED")) return;
            if (System.nanoTime() - started > 600_000_000_000L) throw new AssertionError("Ordinary run timed out");
            if (!launched) {
                if (client.currentScreen instanceof AccessibilityOnboardingScreen) {
                    client.setScreen(new TitleScreen());
                    return;
                }
                if (!(client.currentScreen instanceof TitleScreen)) return;
                String worldName = System.getenv("SKYSIGNS_NORMAL_WORLD");
                require(worldName != null && worldName.startsWith("issue9-normal-")
                        && !worldName.contains("/") && !worldName.contains("\\"), "Require a disposable world copy");
                require(Files.isRegularFile(client.getLevelStorage().getSavesDirectory().resolve(worldName).resolve("level.dat")),
                        "Launcher must supply a saved world copy");
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
                launched = true;
                client.createIntegratedServerLoader().start(worldName, () -> fail(client, new AssertionError("World load canceled")));
                return;
            }
            if (client.world == null || client.player == null || client.getServer() == null) {
                require(state.equals("LOAD"), "Ordinary world disconnected during capture");
                return;
            }
            if (prepared == null) {
                prepared = CompletableFuture.runAsync(() -> {
                    ServerWorld world = client.getServer().getOverworld();
                    WeatherManager.setManual(world, SkyWeatherType.STORM);
                    WindManager.setManual(world, 90, .5F);
                    world.setTimeOfDay(6000);
                    var player = client.getServer().getPlayerManager().getPlayerList().getFirst();
                    player.changeGameMode(GameMode.SPECTATOR);
                    player.requestTeleport(8, 84, -10);
                    player.setYaw(0); player.setPitch(25);
                }, client.getServer());
                return;
            }
            if (!prepared.isDone()) return;
            prepared.join();
            if (state.equals("LOAD")) {
                if (client.currentScreen != null || Math.abs(client.player.getY() - 84) > .01) return;
                client.player.setYaw(0); client.player.setPitch(25);
                client.mouse.unlockCursor();
                checkClient(client);
                Map<String, Object> environment = new LinkedHashMap<>();
                environment.put("sourceCommit", System.getenv("SKYSIGNS_NORMAL_COMMIT"));
                environment.put("measurement", "ordinary client WorldRenderEvents.END_MAIN intervals; not GPU/present time");
                environment.put("gametestEnabled", false);
                environment.put("javaVersion", System.getProperty("java.runtime.version"));
                environment.put("gpuVendor", GL11.glGetString(GL11.GL_VENDOR));
                environment.put("gpuRenderer", GL11.glGetString(GL11.GL_RENDERER));
                environment.put("glVersion", GL11.glGetString(GL11.GL_VERSION));
                environment.put("framebuffer", "1280x720"); environment.put("vsync", true);
                environment.put("fpsLimit", 120); environment.put("renderDistance", 5);
                environment.put("simulationDistance", 12); environment.put("fov", 70);
                environment.put("warmupTicks", WARMUP_TICKS); environment.put("sampleTicks", SAMPLE_TICKS);
                environment.put("preflightOnly", PREFLIGHT);
                environment.put("seed", client.getServer().getOverworld().getSeed());
                environment.put("camera", Map.of("x", 8, "y", 84, "z", -10, "yaw", 0, "pitch", 25));
                Issue9Probe.writeJson(output.resolve("environment.json"), environment);
                state = "CONFIGURE";
                client.getServer().execute(() -> configure(client.getServer().getOverworld()));
                return;
            }
            checkClient(client);
            Issue9Probe.Phase current = phase;
            if (current == null || !current.complete) return;
            current.validateServerCapture(); current.validateCapture();
            require(current.frameCount > SAMPLE_TICKS / 2 && current.tickCount == SAMPLE_TICKS, "Insufficient ordinary-client samples");
            Path directory = output.resolve("round-" + (window / 3 + 1) + "-load-" + LOADS[window]);
            current.export(directory);
            Files.move(directory.resolve("scheduling-diagnostic/frames.csv"), directory.resolve("normal-render-intervals.csv"));
            Issue9Probe.writeJson(directory.resolve("normal-summary.json"), Map.of(
                    "source", "ordinary client without GameTest mixins", "load", LOADS[window],
                    "round", window / 3 + 1, "tick", Issue9Probe.summary(current.ticks, current.tickCount),
                    "renderInterval", Issue9Probe.summary(current.frames, current.frameCount),
                    "durationMs", (current.ended - current.started) / 1e6));
            ScreenshotRecorder.saveScreenshot(client.runDirectory, "normal-r" + (window / 3 + 1)
                    + "-n" + LOADS[window] + ".png", client.getFramebuffer(), 1, text -> {});
            SkySigns.LOGGER.info("SKYSIGNS_NORMAL window={} load={} frames={} p95={}", window, LOADS[window],
                    current.frameCount, Issue9Probe.summary(current.frames, current.frameCount).get("p95Ms"));
            phase = null;
            if (++window == LOADS.length) {
                Issue9Probe.writeGc(output);
                Issue9Probe.writeJson(output.resolve("result.json"), Map.of("passed", true, "windows", window,
                        "formalTickSamples", PREFLIGHT ? 0 : 5400, "normalRendering", true));
                state = "DONE";
                client.scheduleStop();
            } else {
                state = "CONFIGURE";
                client.getServer().execute(() -> configure(client.getServer().getOverworld()));
            }
        } catch (Throwable error) { fail(client, error); }
    }

    private void serverTick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD || !state.equals("WARMUP")) return;
        try {
            if (++warmup < WARMUP_TICKS) return;
            for (int i = 0; i < LOADS[window]; i++) {
                BlockPos pos = GRID.add(i % 16, 0, i / 16);
                require(world.getBlockEntity(pos) instanceof MeterBlockEntity meter
                        && meter.readingPercent() == AtmosphericReadings.humidityPercent(world, pos), "Meter mismatch");
            }
            phase = Issue9Probe.arm(SAMPLE_TICKS, false);
            state = "SAMPLE";
        } catch (Throwable error) { MinecraftClient.getInstance().execute(() -> fail(MinecraftClient.getInstance(), error)); }
    }

    private void configure(ServerWorld world) {
        for (int i = 0; i < 128; i++) {
            BlockPos pos = GRID.add(i % 16, 0, i / 16);
            world.setBlockState(pos, i < LOADS[window] ? ModBlocks.HYGROMETER.getDefaultState() : Blocks.AIR.getDefaultState());
        }
        require(world.getSeed() == 1 && WeatherManager.get(world).type() == SkyWeatherType.STORM, "World fixture changed");
        for (int x = -3; x <= 18; x++) for (int y = 78; y <= 82; y++) for (int z = -3; z <= 10; z++) {
            require(world.getFluidState(new BlockPos(x, y, z)).isEmpty(), "Waterless fixture changed");
        }
        warmup = 0;
        state = "WARMUP";
    }

    private void checkClient(MinecraftClient client) {
        require(client.currentScreen == null && !client.isPaused() && client.isWindowFocused(), "Screen/pause/focus changed");
        require(client.getWindow().getFramebufferWidth() == 1280 && client.getWindow().getFramebufferHeight() == 720,
                "Framebuffer changed");
        require(Math.abs(client.player.getX() - 8) < .01 && Math.abs(client.player.getY() - 84) < .01
                && Math.abs(client.player.getZ() + 10) < .01 && Math.abs(client.player.getYaw()) < .01
                && Math.abs(client.player.getPitch() - 25) < .01,
                "Camera changed: x=" + client.player.getX() + " y=" + client.player.getY() + " z=" + client.player.getZ()
                        + " yaw=" + client.player.getYaw() + " pitch=" + client.player.getPitch());
        require(client.getInactivityFpsLimiter().getLimitReason().toString().equals("NONE"), "Inactivity limiter active");
        require(client.options.getEnableVsync().getValue() && client.options.getMaxFps().getValue() == 120
                && client.options.getViewDistance().getValue() == 5 && client.options.getSimulationDistance().getValue() == 12
                && client.options.getGuiScale().getValue() == 0 && client.options.getFov().getValue() == 70
                && client.options.getPerspective() == Perspective.FIRST_PERSON && client.player.isSpectator(),
                "Fixed rendering/player options changed");
    }

    private void fail(MinecraftClient client, Throwable error) {
        state = "FAILED"; Issue9Probe.abort();
        SkySigns.LOGGER.error("Ordinary performance capture failed", error);
        try { Issue9Probe.writeJson(output.resolve("failure.json"), Map.of("error", error.toString(), "window", window)); }
        catch (Exception writeError) { SkySigns.LOGGER.error("Could not export failure", writeError); }
        client.scheduleStop();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
