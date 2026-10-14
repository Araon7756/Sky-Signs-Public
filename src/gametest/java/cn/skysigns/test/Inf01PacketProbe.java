package cn.skysigns.test;

import java.util.*;
import net.minecraft.util.math.ChunkPos;

/** Only woven for the explicit INF-01 selftest. No packet hooks in default regression. */
public final class Inf01PacketProbe {
    private static final Map<Object, Integer> CONNECTIONS = new IdentityHashMap<>();
    private static final Map<String, Set<ChunkPos>> SENT = new TreeMap<>(), RECEIVED = new TreeMap<>(), DROPPED = new TreeMap<>();
    private static ChunkPos drop;

    private Inf01PacketProbe() { }

    static synchronized void begin(ChunkPos missing) {
        CONNECTIONS.clear(); SENT.clear(); RECEIVED.clear(); DROPPED.clear(); drop = missing;
    }

    public static synchronized void sent(Object connection, String dimension, int x, int z) {
        record(SENT, connection, dimension, x, z);
    }

    public static synchronized boolean received(Object connection, String dimension, int x, int z) {
        record(RECEIVED, connection, dimension, x, z);
        boolean cancel = "minecraft:overworld".equals(dimension) && new ChunkPos(x, z).equals(drop);
        if (cancel) record(DROPPED, connection, dimension, x, z);
        return cancel;
    }

    private static void record(Map<String, Set<ChunkPos>> target, Object connection, String dimension, int x, int z) {
        int id = CONNECTIONS.computeIfAbsent(connection, ignored -> CONNECTIONS.size() + 1);
        target.computeIfAbsent("connection-" + id + "/" + dimension, ignored -> new HashSet<>()).add(new ChunkPos(x, z));
    }

    static synchronized Set<ChunkPos> sent(String dimension) { return union(SENT, dimension); }
    static synchronized Set<ChunkPos> received(String dimension) { return union(RECEIVED, dimension); }
    private static Set<ChunkPos> union(Map<String, Set<ChunkPos>> source, String dimension) {
        Set<ChunkPos> result = new HashSet<>();
        source.forEach((key, positions) -> { if (key.endsWith("/" + dimension)) result.addAll(positions); });
        return Set.copyOf(result);
    }

    public static synchronized Map<String, Object> summary() {
        return Map.of("enabled", "1".equals(System.getenv("SKYSIGNS_INF01_SELFTEST")),
                "sent", sorted(SENT), "received", sorted(RECEIVED), "dropped", sorted(DROPPED));
    }

    private static Map<String, List<ChunkPos>> sorted(Map<String, Set<ChunkPos>> source) {
        Map<String, List<ChunkPos>> result = new TreeMap<>();
        source.forEach((key, positions) -> result.put(key, positions.stream()
                .sorted(Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z)).toList()));
        return result;
    }
}
