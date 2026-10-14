package cn.skysigns.test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkStatus;
import cn.skysigns.registry.ModBlocks;
import cn.skysigns.block.ObservationInstrumentBlock;

/** Explicit contract/fault tests, independent of the default product assertions. */
@SuppressWarnings("UnstableApiUsage")
final class Inf01ReadinessAssertions {
    private Inf01ReadinessAssertions() { }

    static void run(ClientGameTestContext context) {
        if (Boolean.getBoolean("inf01.framework")) ChunkReadiness.require(
                !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("skysigns"), "Framework control unexpectedly loaded Sky Signs");
        String mode = Objects.requireNonNullElse(System.getenv("SKYSIGNS_INF01_CASE"), "all");
        ChunkReadiness.require(Set.of("all", "native", "negative", "lifecycle").contains(mode), "Unknown INF-01 case: " + mode);
        if (mode.equals("all") || mode.equals("native")) nativeFailure(context);
        if (mode.equals("all") || mode.equals("negative")) negatives(context);
        if (mode.equals("all") || mode.equals("lifecycle")) lifecycle(context);
        ChunkReadiness.event("selftestPassed", Map.of("case", mode, "nativeSquareFixed", false));
    }

    static void nativeFailure(ClientGameTestContext context) {
        Inf01PacketProbe.begin(null);
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            var targets = region(game);
            var session = ChunkReadiness.bind(context, game);
            session.await("native-control-ready", targets);
            var snapshot = session.snapshot();
            verifyShape(snapshot);
            AssertionError failure = expectFailure("Timed out waiting for predicate", () -> game.getClientWorld().waitForChunksDownload());
            var missing = context.computeOnClient(client -> {
                List<ChunkPos> result = new ArrayList<>();
                for (int x = snapshot.center().x - 8; x <= snapshot.center().x + 8; x++)
                    for (int z = snapshot.center().z - 8; z <= snapshot.center().z + 8; z++)
                        if (client.world.getChunk(x, z, ChunkStatus.FULL, false) == null) result.add(new ChunkPos(x, z));
                return result;
            });
            Set<ChunkPos> corners = corners(snapshot.center());
            ChunkReadiness.require(new HashSet<>(missing).equals(corners), "Native failure was not the four excluded corners: " + missing);
            StringWriter trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace));
            ChunkReadiness.event("nativeExpectedFailure", Map.of("snapshot", snapshot, "missing", missing,
                    "timeout", ChunkReadiness.TIMEOUT, "trace", trace.toString(), "packets", Inf01PacketProbe.summary()));
        }
        ChunkReadiness.closed(context, "native-control");
    }

    private static void negatives(ClientGameTestContext context) {
        Inf01PacketProbe.begin(null);
        ChunkReadiness.Session oldSession;
        ChunkReadiness.Snapshot oldSnapshot;
        ChunkReadiness.Targets oldTargets;
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            var targets = region(game);
            var session = ChunkReadiness.bind(context, game);
            session.await("negative-baseline", targets);
            var snapshot = session.snapshot();
            verifyShape(snapshot);
            verifyPackets(snapshot);
            var foreign = new ChunkReadiness.Snapshot(snapshot.epoch() + 1, snapshot.dimension(), snapshot.player(),
                    snapshot.playerChunk(), snapshot.center(), 8, 8, 8, snapshot.expected(), snapshot.hash());
            expectFailure("different epoch/player", () -> session.observe(foreign, targets));
            var wrongCenter = new ChunkReadiness.Snapshot(snapshot.epoch(), snapshot.dimension(), snapshot.player(),
                    snapshot.playerChunk(), new ChunkPos(snapshot.center().x + 1, snapshot.center().z), 8, 8, 8, snapshot.expected(), snapshot.hash());
            ChunkReadiness.require(!session.observe(wrongCenter, targets).downloaded(), "Wrong center was accepted");
            var unsynced = new ChunkReadiness.Snapshot(snapshot.epoch(), snapshot.dimension(), snapshot.player(),
                    snapshot.playerChunk(), snapshot.center(), 8, 12, 8, snapshot.expected(), snapshot.hash());
            ChunkReadiness.require(!session.observe(unsynced, targets).downloaded(), "Unaccepted view was treated as ready");
            // Change only the actual client option, without sending it; never reduce the target below eight.
            context.runOnClient(client -> client.options.getViewDistance().setValue(12));
            try {
                ChunkReadiness.require(!session.observe(snapshot, targets).downloaded(), "Actual unsynchronized client option was accepted");
            } finally { context.runOnClient(client -> client.options.getViewDistance().setValue(ChunkReadiness.VIEW)); }
            var outside = ChunkReadiness.Targets.area(new BlockPos(16000, 0, 16000), new BlockPos(16000, 0, 16000));
            expectFailure("outside current filter", () -> session.observe(snapshot, outside));

            // A real block update cannot pass merely because the render builder was empty before receipt.
            BlockPos marker = game.getServer().computeOnServer(server -> {
                BlockPos pos = server.getPlayerManager().getPlayerList().getFirst().getBlockPos().east(3);
                server.getOverworld().setBlockState(pos, Blocks.GOLD_BLOCK.getDefaultState());
                return pos;
            });
            var updatedTargets = targets.block(marker, Blocks.GOLD_BLOCK);
            ChunkReadiness.require(!session.observe(snapshot, updatedTargets).downloaded(), "Unreceived scene update was accepted as ready");
            ChunkReadiness.captureTarget(context, game, "updated-render-target", updatedTargets, marker);
            oldSession = session; oldSnapshot = session.snapshot(); oldTargets = targets;
            ChunkReadiness.event("negativePassed", Map.of("cases", List.of("foreign-epoch", "wrong-center", "unsynchronized-view-snapshot",
                    "actual-unsynchronized-client-option", "target-outside-filter", "unreceived-block-update", "real-block-update-and-frame")));
        }
        ChunkReadiness.closed(context, "negative-baseline");
        expectFailure("Stale INF-01 epoch", () -> oldSession.observe(oldSnapshot, oldTargets));
        Inf01PacketProbe.begin(new ChunkPos(1, 1));
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            var targets = region(game);
            var session = ChunkReadiness.bind(context, game);
            var snapshot = session.snapshot();
            for (int tick = 0; (snapshot.center() == null || snapshot.filterView() != 8) && tick < ChunkReadiness.TIMEOUT; tick++) {
                context.waitTick();
                snapshot = session.snapshot();
            }
            ChunkReadiness.require(snapshot.filterView() == 8 && snapshot.center() != null
                    && snapshot.expected().contains(new ChunkPos(1, 1)) && !corners(snapshot.playerChunk()).contains(new ChunkPos(1, 1)),
                    "Dropped chunk must be a normally sent non-corner");
            expectFailure("Timed out waiting for predicate", () -> session.await("actual-missing-chunk", targets));
            var observation = session.observe(session.snapshot(), targets);
            ChunkReadiness.require(observation.missing().equals(List.of(new ChunkPos(1, 1))), "Unexpected missing coordinates: " + observation.missing());
            // Native render-only completion can coexist with missing download: helper must not accept that.
            game.getClientWorld().waitForChunksRender(false);
            ChunkReadiness.require(!session.observe(session.snapshot(), targets).downloaded(), "Render queue bypassed missing FULL");
            ChunkReadiness.event("negativePassed", Map.of("cases", List.of("stale-closed-session", "actual-missing-full-timeout", "render-only-cannot-replace-download"),
                    "missing", observation.missing(), "packets", Inf01PacketProbe.summary()));
        } finally { Inf01PacketProbe.begin(null); }
        ChunkReadiness.closed(context, "missing-chunk");
    }

    private static void lifecycle(ClientGameTestContext context) {
        // Fixed before execution; do not replace failed cycles with extra successful attempts.
        for (int cycle = 0; cycle < 10; cycle++) {
            TestWorldSave save;
            BlockPos meter;
            Inf01PacketProbe.begin(null);
            try (TestSingleplayerContext game = context.worldBuilder().create()) {
                save = game.getWorldSave();
                ChunkReadiness.await(context, game, "cycle-" + cycle + "-created", region(game));
                meter = game.getServer().computeOnServer(server -> {
                    var player = server.getPlayerManager().getPlayerList().getFirst();
                    BlockPos pos = player.getBlockPos().east(2);
                    server.getOverworld().setBlockState(pos, ModBlocks.HYGROMETER.getDefaultState().with(ObservationInstrumentBlock.FACING, Direction.EAST));
                    return pos;
                });
                var targets = region(game).meter(meter, ModBlocks.HYGROMETER, Direction.EAST);
                ChunkReadiness.captureTarget(context, game, "cycle-" + cycle + "-meter", targets, meter);
                // Nonzero negative center, scene crossing chunk boundaries, then restore the original test location.
                game.getServer().runCommand("tp @a -16.5 -60 -16.5");
                ChunkReadiness.capture(context, game, "cycle-" + cycle + "-negative-center", region(game));
                game.getServer().runCommand("tp @a 0.5 -60 0.5");
                ChunkReadiness.await(context, game, "cycle-" + cycle + "-returned", region(game).meter(meter, ModBlocks.HYGROMETER, Direction.EAST));
            }
            ChunkReadiness.closed(context, "cycle-" + cycle + "-save");
            for (int reopen = 0; reopen < 2; reopen++) {
                Inf01PacketProbe.begin(null);
                try (TestSingleplayerContext game = save.open()) {
                    var session = ChunkReadiness.bind(context, game);
                    session.await("cycle-" + cycle + "-reopen-" + reopen, region(game).meter(meter, ModBlocks.HYGROMETER, Direction.EAST));
                    verifyPackets(session.snapshot());
                    ChunkReadiness.captureTarget(context, game, "cycle-" + cycle + "-reopen-" + reopen,
                            region(game).meter(meter, ModBlocks.HYGROMETER, Direction.EAST), meter);
                }
                ChunkReadiness.closed(context, "cycle-" + cycle + "-reopen-" + reopen);
            }
            ChunkReadiness.event("cyclePassed", Map.of("cycle", cycle, "reopens", 2));
        }
    }

    private static ChunkReadiness.Targets region(TestSingleplayerContext game) {
        return game.getServer().computeOnServer(server -> {
            BlockPos pos = server.getPlayerManager().getPlayerList().getFirst().getBlockPos();
            return ChunkReadiness.Targets.area(pos.add(-17, -1, -17), pos.add(17, 5, 17));
        });
    }

    private static Set<ChunkPos> corners(ChunkPos center) {
        return Set.of(new ChunkPos(center.x - 8, center.z - 8), new ChunkPos(center.x - 8, center.z + 8),
                new ChunkPos(center.x + 8, center.z - 8), new ChunkPos(center.x + 8, center.z + 8));
    }

    private static void verifyShape(ChunkReadiness.Snapshot snapshot) {
        int inSquare = 0;
        for (ChunkPos pos : snapshot.expected()) if (Math.abs(pos.x - snapshot.center().x) <= 8 && Math.abs(pos.z - snapshot.center().z) <= 8) inSquare++;
        ChunkReadiness.require(snapshot.expected().size() == 329 && inSquare == 285
                && Collections.disjoint(snapshot.expected(), corners(snapshot.center())), "Unexpected native range geometry: " + snapshot);
    }

    private static void verifyPackets(ChunkReadiness.Snapshot snapshot) {
        Set<ChunkPos> expected = Set.copyOf(snapshot.expected());
        ChunkReadiness.require(Inf01PacketProbe.sent(snapshot.dimension()).equals(expected), "Sent coordinates differ from full filter");
        ChunkReadiness.require(Inf01PacketProbe.received(snapshot.dimension()).equals(expected), "Received coordinates differ from full filter");
        ChunkReadiness.event("packetsMatched", Map.of("epoch", snapshot.epoch(), "snapshot", snapshot, "packets", Inf01PacketProbe.summary()));
    }

    private static AssertionError expectFailure(String message, Runnable action) {
        try { action.run(); } catch (AssertionError failure) {
            ChunkReadiness.require(failure.getMessage() != null && failure.getMessage().contains(message), "Wrong failure: " + failure);
            return failure;
        }
        throw new AssertionError("Negative case unexpectedly succeeded: " + message);
    }
}
