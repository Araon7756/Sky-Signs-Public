package cn.skysigns.test;

import com.google.gson.GsonBuilder;
import com.sun.management.GarbageCollectionNotificationInfo;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Bounded in-memory buffers: no CSV, per-frame logging or list growth in a measured window. */
public final class Issue9Probe {
    private static volatile Phase active;
    private static Phase humidityPhase;
    private static long humidityStarted, waterStarted, waterNanos, queries, queryNanos, position, scans;
    private static boolean waterFound;
    private static final List<Map<String, Object>> GC_EVENTS = Collections.synchronizedList(new ArrayList<>());

    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Phase p = active;
            if (p != null && p.started != 0) p.clientTicks++;
        });
        ServerTickEvents.START_WORLD_TICK.register(world -> {
            Phase p = active;
            if (p != null && world.getRegistryKey() == World.OVERWORLD) {
                p.tickStart = System.nanoTime();
                if (p.started == 0) p.started = p.tickStart;
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            Phase p = active;
            if (p != null && world.getRegistryKey() == World.OVERWORLD && p.tickStart != 0) {
                long now = System.nanoTime();
                int n = p.tickCount;
                p.tickOffsets[n] = now - p.started;
                p.ticks[n] = now - p.tickStart;
                p.tickCount = n + 1;
                p.tickStart = 0;
                if (p.tickCount == p.target) {
                    p.ended = now;
                    active = null;
                    p.complete = true;
                }
            }
        });
        WorldRenderEvents.END_MAIN.register(context -> {
            Phase p = active;
            if (p == null) return;
            p.renderCallbacks++;
            if (p.started == 0) { p.callbacksBeforeStart++; return; }
            long now = System.nanoTime();
            if (p.ended != 0 && now > p.ended) { p.callbacksAfterEnd++; return; }
            if (p.timestampCount == p.timestamps.length) { p.overflow = true; return; }
            p.timestamps[p.timestampCount++] = now;
            if (p.lastFrame != 0) {
                int n = p.frameCount;
                if (n == p.frames.length) { p.overflow = true; return; }
                p.frameOffsets[n] = now - p.started;
                p.frames[n] = now - p.lastFrame;
                p.frameCount = n + 1;
            }
            p.lastFrame = now;
        });
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) return;
                    var info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
                    GC_EVENTS.add(Map.of("collector", info.getGcName(), "cause", info.getGcCause(),
                            "action", info.getGcAction(), "startJvmMs", info.getGcInfo().getStartTime(),
                            "durationMs", info.getGcInfo().getDuration()));
                }, null, null);
            }
        }
    }

    private Issue9Probe() { }
    public static boolean isCounting() { return humidityPhase != null; }
    public static void humidityStart(ServerWorld world, BlockPos pos) {
        Phase p = active;
        if (p == null || !p.detailed || p.started == 0 || world.getRegistryKey() != World.OVERWORLD) return;
        if (humidityPhase != null) throw new AssertionError("Nested humidity call is not supported by this probe");
        humidityPhase = p;
        position = pos.asLong();
        queries = queryNanos = waterNanos = scans = 0;
        waterFound = false;
        humidityStarted = System.nanoTime();
    }
    public static void waterStart() {
        if (humidityPhase != null) { scans++; waterStarted = System.nanoTime(); }
    }
    public static void fluidLookup(long nanos) { queries++; queryNanos += nanos; }
    public static void waterEnd(boolean found) {
        if (humidityPhase != null) { waterNanos += System.nanoTime() - waterStarted; waterFound = found; }
    }
    public static void humidityEnd(int reading) {
        Phase p = humidityPhase;
        if (p == null) return;
        long duration = System.nanoTime() - humidityStarted;
        int n = p.callCount;
        if (n == p.callData.length) { p.overflow = true; humidityPhase = null; return; }
        long[] row = p.callData[n];
        row[0] = System.nanoTime() - p.started; row[1] = p.tickCount; row[2] = position;
        row[3] = duration; row[4] = waterNanos; row[5] = queries; row[6] = queryNanos;
        row[7] = waterFound ? 1 : 0; row[8] = reading; row[9] = scans;
        p.callCount = n + 1;
        humidityPhase = null;
    }

    static Phase arm(int target, boolean detailed) {
        if (active != null) throw new AssertionError("Probe already active");
        Phase p = new Phase(target, detailed);
        active = p;
        return p;
    }
    static void abort() { active = null; humidityPhase = null; }
    static void writeGc(Path directory) throws IOException {
        synchronized (GC_EVENTS) { writeJson(directory.resolve("gc-events.json"), new ArrayList<>(GC_EVENTS)); }
    }
    static void writeJson(Path path, Object value) throws IOException {
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(value));
    }
    static Map<String, Object> summary(long[] values, int count) {
        if (count == 0) return Map.of("count", 0);
        long[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        return Map.of("count", count, "medianMs", sorted[count / 2] / 1e6,
                "p95Ms", sorted[(int) Math.ceil(count * .95) - 1] / 1e6, "maxMs", sorted[count - 1] / 1e6);
    }

    static final class Phase {
        final int target;
        final boolean detailed;
        final long[] ticks, tickOffsets;
        final long[] frames = new long[20000], frameOffsets = new long[20000];
        final long[] timestamps = new long[20001];
        final long[][] callData = new long[10000][10];
        volatile boolean complete, overflow;
        volatile long started, ended;
        long tickStart, lastFrame;
        int tickCount, frameCount, callCount;
        int clientTicks, renderCallbacks, timestampCount, callbacksBeforeStart, callbacksAfterEnd;
        Phase(int target, boolean detailed) {
            this.target = target; this.detailed = detailed;
            ticks = new long[target]; tickOffsets = new long[target];
        }
        void export(Path directory) throws IOException { export(directory, false); }
        void export(Path directory, boolean frameDiagnosis) throws IOException {
            Files.createDirectories(directory);
            Path scheduling = directory.resolve("scheduling-diagnostic");
            Files.createDirectories(scheduling);
            writeSeries(directory.resolve("ticks.csv"), "world_tick_ns", tickOffsets, ticks, tickCount);
            writeSeries(scheduling.resolve("frames.csv"), "frame_interval_ns", frameOffsets, frames, frameCount);
            StringBuilder timestampCsv = new StringBuilder("sample,timestamp_ns,offset_ns\n");
            for (int i = 0; i < timestampCount; i++) timestampCsv.append(i).append(',').append(timestamps[i])
                    .append(',').append(timestamps[i] - started).append('\n');
            Files.writeString(scheduling.resolve("frame-timestamps.csv"), timestampCsv);
            Map<String, Object> capture = new java.util.LinkedHashMap<>();
            capture.put("windowStartNs", started); capture.put("windowEndNs", ended);
            capture.put("wallMs", (ended - started) / 1e6); capture.put("serverTicks", tickCount);
            capture.put("clientTicks", clientTicks); capture.put("renderCallback", "WorldRenderEvents.END_MAIN");
            capture.put("renderCallbackCalls", renderCallbacks); capture.put("frameTimestamps", timestampCount);
            capture.put("frameIntervals", frameCount); capture.put("callbacksBeforeStart", callbacksBeforeStart);
            capture.put("callbacksAfterEnd", callbacksAfterEnd);
            if (timestampCount > 0) {
                capture.put("firstFrameTimestampNs", timestamps[0]); capture.put("lastFrameTimestampNs", timestamps[timestampCount - 1]);
                capture.put("leadingGapMs", (timestamps[0] - started) / 1e6);
                capture.put("trailingGapMs", (ended - timestamps[timestampCount - 1]) / 1e6);
                capture.put("callbackHz", timestampCount * 1e9 / (ended - started));
            }
            capture.put("purpose", "harness-scheduling-diagnostic-only");
            capture.put("formalFrameHarnessApproved", false);
            if (frameDiagnosis) capture.put("intervalSummary", summary(frames, frameCount));
            capture.put("complete", complete); capture.put("overflow", overflow);
            writeJson(scheduling.resolve("capture.json"), capture);
            StringBuilder calls = new StringBuilder("sample,offset_ns,tick_index,block_pos_long,humidity_ns,water_scan_ns,fluid_queries,fluid_query_ns,water_found,humidity_percent,water_scans\n");
            long humidityTotal = 0, waterTotal = 0, queryTotal = 0, queryTime = 0, scanTotal = 0;
            long[] humidityDurations = new long[callCount], scanDurations = new long[callCount], queryDurations = new long[callCount];
            for (int i = 0; i < callCount; i++) {
                calls.append(i);
                for (long v : callData[i]) calls.append(',').append(v);
                calls.append('\n');
                humidityTotal += callData[i][3]; waterTotal += callData[i][4];
                queryTotal += callData[i][5]; queryTime += callData[i][6]; scanTotal += callData[i][9];
                humidityDurations[i] = callData[i][3]; scanDurations[i] = callData[i][4]; queryDurations[i] = callData[i][6];
            }
            Files.writeString(directory.resolve("sampling.csv"), calls);
            writeJson(directory.resolve("summary.json"), Map.of(
                    "complete", complete, "overflow", overflow, "wallMs", (ended - started) / 1e6,
                    "tick", summary(ticks, tickCount), "tickScope", "overworld START_WORLD_TICK to END_WORLD_TICK",
                    "formalFrameSamples", 0, "detailedDistributions", Map.of("unit", "per-humidity-call",
                            "humidity", summary(humidityDurations, callCount), "waterScan", summary(scanDurations, callCount),
                            "fluidQueriesCumulative", summary(queryDurations, callCount)),
                    "detailed", detailed, "sampling", Map.of("observed", detailed, "humidityCalls", callCount, "waterScans", scanTotal,
                            "fluidQueries", queryTotal, "humidityNs", humidityTotal,
                            "waterScanNs", waterTotal, "fluidQueryNs", queryTime)));
        }
        void validateServerCapture() {
            if (!complete || overflow || ended <= started || tickCount != target) {
                throw new AssertionError("Incomplete server world-tick capture");
            }
            for (int i = 0; i < tickCount; i++) {
                if (ticks[i] <= 0 || tickOffsets[i] < ticks[i]
                        || tickOffsets[i] > ended - started
                        || (i > 0 && tickOffsets[i] <= tickOffsets[i - 1])) {
                    throw new AssertionError("Invalid server world-tick sample or window coverage");
                }
            }
            if (tickOffsets[tickCount - 1] != ended - started) {
                throw new AssertionError("Server window does not end on the final captured tick");
            }
            validateSchedulingTimestamps();
        }
        private void validateSchedulingTimestamps() {
            if (frameCount != Math.max(0, timestampCount - 1)) throw new AssertionError("Invalid diagnostic interval linkage");
            for (int i = 0; i < timestampCount; i++) {
                if (timestamps[i] < started || timestamps[i] > ended
                        || (i > 0 && (timestamps[i] <= timestamps[i - 1] || frames[i - 1] != timestamps[i] - timestamps[i - 1]))) {
                    throw new AssertionError("Invalid scheduling diagnostic timestamp");
                }
            }
        }
        void validateCapture() {
            if (!complete || overflow || ended <= started || timestampCount < 3
                    || frameCount != timestampCount - 1) throw new AssertionError("Incomplete frame capture or interval linkage");
            for (int i = 0; i < timestampCount; i++) {
                if (timestamps[i] < started || timestamps[i] > ended
                        || (i > 0 && (timestamps[i] <= timestamps[i - 1] || frames[i - 1] != timestamps[i] - timestamps[i - 1]))) {
                    throw new AssertionError("Nonmonotonic/out-of-window frame timestamp or invalid interval");
                }
            }
            // Verify activity near BOTH boundaries; this is independent of server/client tick counts.
            if (timestamps[0] - started > 150_000_000L || ended - timestamps[timestampCount - 1] > 150_000_000L) {
                throw new AssertionError("Render activity does not cover the window boundaries within 150ms");
            }
        }
        private void writeSeries(Path path, String valueName, long[] offsets, long[] values, int count) throws IOException {
            StringBuilder output = new StringBuilder("sample,offset_ns," + valueName + "\n");
            for (int i = 0; i < count; i++) output.append(i).append(',').append(offsets[i]).append(',').append(values[i]).append('\n');
            Files.writeString(path, output);
        }
    }
}
