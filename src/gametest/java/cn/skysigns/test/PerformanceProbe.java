package cn.skysigns.test;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Test-only sampling of integrated-server world ticks and rendered frame intervals. */
final class PerformanceProbe {
    private static volatile Phase phase;

    static {
        ServerTickEvents.START_WORLD_TICK.register(PerformanceProbe::startTick);
        ServerTickEvents.END_WORLD_TICK.register(PerformanceProbe::endTick);
        WorldRenderEvents.END_MAIN.register(context -> endFrame());
    }

    private PerformanceProbe() {
    }

    static Phase start() {
        Phase next = new Phase();
        phase = next;
        return next;
    }

    static void stop() {
        phase = null;
    }

    private static void startTick(ServerWorld world) {
        Phase active = phase;
        if (active != null && world.getRegistryKey() == net.minecraft.world.World.OVERWORLD) {
            active.tickStartedAt = System.nanoTime();
        }
    }

    private static void endTick(ServerWorld world) {
        Phase active = phase;
        if (active != null && world.getRegistryKey() == net.minecraft.world.World.OVERWORLD && active.tickStartedAt != 0L) {
            active.addTick(System.nanoTime() - active.tickStartedAt);
        }
    }

    private static void endFrame() {
        Phase active = phase;
        long now = System.nanoTime();
        if (active != null) {
            if (active.lastFrameAt != 0L) active.addFrame(now - active.lastFrameAt);
            active.lastFrameAt = now;
        }
    }

    static final class Phase {
        // Each timestamp belongs to its phase and is accessed only on its own event thread.
        private long tickStartedAt;
        private long lastFrameAt;
        private final List<Long> ticks = Collections.synchronizedList(new ArrayList<>());
        private final List<Long> frames = Collections.synchronizedList(new ArrayList<>());

        private void addTick(long nanos) {
            ticks.add(nanos);
        }

        private void addFrame(long nanos) {
            frames.add(nanos);
        }

        Summary tickSummary() {
            return summarize(ticks);
        }

        Summary frameSummary() {
            return summarize(frames);
        }
    }

    record Summary(int count, double medianMs, double p95Ms) {
    }

    private static Summary summarize(List<Long> source) {
        List<Long> sorted;
        synchronized (source) {
            sorted = new ArrayList<>(source);
        }
        if (sorted.isEmpty()) {
            return new Summary(0, Double.NaN, Double.NaN);
        }
        sorted.sort(Long::compare);
        return new Summary(sorted.size(),
                sorted.get(sorted.size() / 2) / 1_000_000.0,
                sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1) / 1_000_000.0);
    }
}
