package cn.skysigns.test;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestClientWorldContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.mixin.client.gametest.ClientChunkManagerAccessor;
import net.fabricmc.fabric.mixin.client.gametest.ClientChunkManagerClientChunkMapAccessor;
import net.fabricmc.fabric.mixin.client.gametest.ClientWorldAccessor;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ChunkFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkStatus;
import cn.skysigns.block.ObservationInstrumentBlock;
import cn.skysigns.block.entity.MeterBlockEntity;

/** Integrated-singleplayer test contract; never shipped in the product JAR. */
@SuppressWarnings("UnstableApiUsage")
final class ChunkReadiness {
    static final int VIEW = 8;
    static final int TIMEOUT = TestClientWorldContext.DEFAULT_CHUNK_LOAD_TIMEOUT;
    private static final Gson JSON = new Gson();
    private static final String RUN = UUID.randomUUID().toString();
    // This pinned handler inherits Object identity equality. Weak keys release closed worlds;
    // the current client and Binding retain the active connection, so its epoch stays stable.
    private static final Map<ClientPlayNetworkHandler, Long> EPOCHS = new WeakHashMap<>();
    private static long nextEpoch;
    // These fields are only accessed on the render/client thread.
    private static long frames;
    private static ClientWorld renderedWorld;
    private static boolean prepared;
    private static Path output;

    private ChunkReadiness() { }

    static void prepare(ClientGameTestContext context) {
        require(!prepared, "INF-01 prepare called twice");
        require(!"1".equals(System.getenv("SKYSIGNS_NETWORK_WAIT_GUARD")), "INF-01 forbids the old wait guard");
        String directory = System.getenv("SKYSIGNS_INF01_OUTPUT");
        if (directory != null) {
            output = Path.of(directory).toAbsolutePath();
            require(Files.isDirectory(output.getParent()) && !Files.exists(output), "Output must be a new directory: " + output);
            try { Files.createDirectory(output); } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        context.runOnClient(client -> {
            require(client.world == null, "Set explicit view before creating the first world");
            client.options.getViewDistance().setValue(VIEW);
            WorldRenderEvents.END_MAIN.register(render -> {
                frames++;
                renderedWorld = MinecraftClient.getInstance().world;
            });
        });
        prepared = true;
        event("prepare", Map.of("view", VIEW, "timeout", TIMEOUT, "guard", false));
    }

    record ExpectedBlock(BlockPos pos, Block block, Direction facing, boolean meter) { }

    record Targets(Set<ChunkPos> chunks, List<ExpectedBlock> blocks) {
        Targets {
            chunks = Set.copyOf(chunks);
            blocks = List.copyOf(blocks);
        }
        static Targets area(BlockPos first, BlockPos last) {
            Set<ChunkPos> chunks = new HashSet<>();
            for (int x = first.getX() >> 4; x <= last.getX() >> 4; x++) {
                for (int z = first.getZ() >> 4; z <= last.getZ() >> 4; z++) chunks.add(new ChunkPos(x, z));
            }
            require(!chunks.isEmpty(), "Empty scene region");
            return new Targets(chunks, List.of());
        }
        Targets block(BlockPos pos, Block block) { return add(new ExpectedBlock(pos.toImmutable(), block, null, false)); }
        Targets facing(BlockPos pos, Block block, Direction facing) { return add(new ExpectedBlock(pos.toImmutable(), block, facing, false)); }
        Targets meter(BlockPos pos, Block block, Direction facing) { return add(new ExpectedBlock(pos.toImmutable(), block, facing, true)); }
        private Targets add(ExpectedBlock block) {
            Set<ChunkPos> nextChunks = new HashSet<>(chunks);
            nextChunks.add(new ChunkPos(block.pos));
            List<ExpectedBlock> nextBlocks = new ArrayList<>(blocks);
            nextBlocks.add(block);
            return new Targets(nextChunks, nextBlocks);
        }
    }

    private record Binding(long epoch, MinecraftServer server, ClientPlayNetworkHandler connection, ClientWorld world, UUID player) { }
    record Snapshot(long epoch, String dimension, UUID player, ChunkPos playerChunk, ChunkPos center,
                    int serverView, int playerView, int filterView, List<ChunkPos> expected, String hash) { }
    record Observation(List<ChunkPos> missing, List<String> blocks, boolean aligned,
                               ChunkPos mapCenter, ChunkPos playerChunk, int optionView, int clampedView,
                               boolean rendered, long frames) {
        boolean downloaded() { return aligned && missing.isEmpty() && blocks.isEmpty(); }
    }

    static Session bind(ClientGameTestContext context, TestSingleplayerContext game) {
        require(prepared, "INF-01 prepare was not called");
        Binding binding = context.computeOnClient(client -> {
            require(client.world != null && client.player != null && client.getNetworkHandler() != null && client.getServer() != null,
                    "No current integrated singleplayer connection");
            client.options.sendClientSettings();
            var connection = client.getNetworkHandler();
            long epoch = EPOCHS.computeIfAbsent(connection, ignored -> ++nextEpoch);
            return new Binding(epoch, client.getServer(), connection, client.world, client.player.getUuid());
        });
        game.getServer().runOnServer(server -> require(server == binding.server, "Wrong TestSingleplayerContext server"));
        return new Session(context, game, binding);
    }

    static void await(ClientGameTestContext context, TestSingleplayerContext game, String scene, Targets targets) {
        bind(context, game).await(scene, targets);
    }

    static void capture(ClientGameTestContext context, TestSingleplayerContext game, String scene, Targets targets) {
        Session session = bind(context, game);
        session.await(scene, targets);
        String name = "inf01-" + RUN + "-e" + session.binding.epoch + "-" + scene;
        context.takeScreenshot(name);
        event("screenshot", Map.of("scene", scene, "epoch", session.binding.epoch, "name", name));
    }

    static void captureTarget(ClientGameTestContext context, TestSingleplayerContext game, String scene, Targets targets, BlockPos lookAt) {
        double[] original = game.getServer().computeOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            return new double[]{player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()};
        });
        // Use the same vanilla camera/teleport path as the existing model fixture, and restore it afterwards.
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f 180 15",
                lookAt.getX() + 0.5, lookAt.getY() + 1.0, lookAt.getZ() + 4.5));
        try { capture(context, game, scene, targets); }
        finally {
            game.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f %.3f %.3f",
                    original[0], original[1], original[2], original[3], original[4]));
            await(context, game, scene + "-camera-restored", targets);
        }
    }

    static void closed(ClientGameTestContext context, String scene) {
        context.runOnClient(client -> require(client.world == null && client.getServer() == null && client.getNetworkHandler() == null,
                "Session not fully closed after " + scene));
        event("closed", Map.of("scene", scene));
    }

    static final class Session {
        private final ClientGameTestContext context;
        private final TestSingleplayerContext game;
        private final Binding binding;

        private Session(ClientGameTestContext context, TestSingleplayerContext game, Binding binding) {
            this.context = context; this.game = game; this.binding = binding;
        }

        Snapshot snapshot() {
            return game.getServer().computeOnServer(server -> {
                require(server == binding.server && !server.isStopped(), "Server session ended");
                var player = server.getPlayerManager().getPlayer(binding.player);
                require(player != null && server.getPlayerManager().getPlayerList().size() == 1, "Expected one current test player");
                ChunkFilter filter = player.getChunkFilter();
                // This version uses a package-private nested record: don't cast to its implementation.
                // A dimension transition can expose IGNORE_ALL before tracking is established.
                // Pending in the SAME deadline; an empty set must never satisfy the download contract.
                if (filter == ChunkFilter.IGNORE_ALL) {
                    return new Snapshot(binding.epoch, player.getEntityWorld().getRegistryKey().getValue().toString(),
                            player.getUuid(), player.getChunkPos(), null, server.getPlayerManager().getViewDistance(),
                            player.getViewDistance(), 0, List.of(), hash(List.of()));
                }
                List<ChunkPos> positions = new ArrayList<>();
                filter.forEach(pos -> positions.add(new ChunkPos(pos.x, pos.z)));
                positions.sort(Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z));
                require(!positions.isEmpty() && new HashSet<>(positions).size() == positions.size(), "Invalid player filter enumeration");
                // Compare to the native factory, rather than copying its geometry or guessing from loaded chunks.
                ChunkPos center = player.getChunkPos();
                int effectiveView = Math.min(server.getPlayerManager().getViewDistance(), player.getViewDistance());
                List<ChunkPos> canonical = new ArrayList<>();
                ChunkFilter nativeFilter = ChunkFilter.cylindrical(center, effectiveView);
                require(filter.getClass() == nativeFilter.getClass(), "Unsupported player filter type: " + filter.getClass().getName());
                nativeFilter.forEach(canonical::add);
                canonical.sort(Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z));
                boolean currentCenter = canonical.equals(positions);
                return new Snapshot(binding.epoch, player.getEntityWorld().getRegistryKey().getValue().toString(),
                        player.getUuid(), center, currentCenter ? center : null, server.getPlayerManager().getViewDistance(),
                        player.getViewDistance(), effectiveView, List.copyOf(positions), hash(positions));
            });
        }

        Observation observe(Snapshot snapshot, Targets targets) {
            return context.computeOnClient(client -> {
                require(client.getServer() == binding.server && client.getNetworkHandler() == binding.connection && client.world == binding.world,
                        "Stale INF-01 epoch " + binding.epoch + ": world/connection/server changed");
                require(snapshot.epoch == binding.epoch && snapshot.player.equals(binding.player), "Snapshot belongs to a different epoch/player");
                require(client.player != null && client.player.getUuid().equals(binding.player), "Client player changed");
                var map = ((ClientChunkManagerAccessor) client.world.getChunkManager()).getChunks();
                var accessor = (ClientChunkManagerClientChunkMapAccessor) (Object) map;
                ChunkPos mapCenter = new ChunkPos(accessor.getCenterChunkX(), accessor.getCenterChunkZ());
                ChunkPos clientCenter = client.player.getChunkPos();
                int option = client.options.getViewDistance().getValue(), clamped = client.options.getClampedViewDistance();
                boolean aligned = snapshot.center != null && snapshot.center.equals(mapCenter) && snapshot.center.equals(clientCenter)
                        && snapshot.dimension.equals(client.world.getRegistryKey().getValue().toString())
                        && option == VIEW && clamped == VIEW && snapshot.serverView == VIEW && snapshot.playerView == VIEW && snapshot.filterView == VIEW;
                // An unsynchronized center is retried, but never shrinks the expected set.
                if (aligned) require(snapshot.expected.containsAll(targets.chunks), "Scene targets outside current filter: " + targets.chunks);
                List<ChunkPos> missing = new ArrayList<>();
                for (ChunkPos pos : snapshot.expected) if (client.world.getChunk(pos.x, pos.z, ChunkStatus.FULL, false) == null) missing.add(pos);
                List<String> blocks = new ArrayList<>();
                for (ExpectedBlock target : targets.blocks) {
                    ChunkPos pos = new ChunkPos(target.pos);
                    var chunk = client.world.getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
                    if (chunk == null) { blocks.add(target.pos + ": not FULL"); continue; }
                    var state = chunk.getBlockState(target.pos);
                    if (!state.isOf(target.block)) blocks.add(target.pos + ": wrong block " + state);
                    if (target.facing != null && (!state.contains(ObservationInstrumentBlock.FACING)
                            || state.get(ObservationInstrumentBlock.FACING) != target.facing)) blocks.add(target.pos + ": wrong facing");
                    if (target.meter && !(chunk.getBlockEntity(target.pos) instanceof MeterBlockEntity)) blocks.add(target.pos + ": missing meter BE");
                }
                boolean rendered = renderedWorld == client.world && ((ClientWorldAccessor) client.world).getChunkUpdaters().isEmpty()
                        && client.worldRenderer.isTerrainRenderComplete();
                return new Observation(List.copyOf(missing), List.copyOf(blocks), aligned, mapCenter, clientCenter, option, clamped, rendered, frames);
            });
        }

        void await(String scene, Targets targets) {
            event("begin", Map.of("scene", scene, "epoch", binding.epoch, "targetChunks", targets.chunks,
                    "targetBlocks", targets.blocks.stream().map(Object::toString).toList()));
            Budget download = new Budget(TIMEOUT);
            Snapshot snapshot = download(scene, targets, download);
            event("downloaded", Map.of("scene", scene, "snapshot", snapshot, "client", observe(snapshot, targets), "ticks", download.used));
            Budget render = new Budget(TIMEOUT);
            try {
                while (true) {
                    snapshot = download(scene, targets, render);
                    long previousFrames = observe(snapshot, targets).frames;
                    render.tick(context); // A real tick/frame after the current blocks and camera.
                    render.used += game.getClientWorld().waitForChunksRender(false, render.remaining());
                    Snapshot after = snapshot();
                    Observation observation = observe(after, targets);
                    if (snapshot.equals(after) && observation.downloaded() && observation.rendered && observation.frames > previousFrames) {
                        event("ready", Map.of("scene", scene, "snapshot", after, "client", observation,
                                "renderTicks", render.used, "frames", observation.frames));
                        return;
                    }
                    render.tick(context);
                }
            } catch (AssertionError failure) { fail(scene, "render", snapshot, targets, failure); }
        }

        private Snapshot download(String scene, Targets targets, Budget budget) {
            Snapshot previous = null;
            try {
                while (true) {
                    Snapshot current = snapshot();
                    Observation observation = observe(current, targets);
                    if (current.equals(previous) && observation.downloaded()) return current;
                    previous = observation.downloaded() ? current : null;
                    budget.tick(context);
                }
            } catch (AssertionError failure) {
                fail(scene, "download", previous, targets, failure);
                throw failure;
            }
        }

        private void fail(String scene, String phase, Snapshot previous, Targets targets, AssertionError failure) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("scene", scene); detail.put("phase", phase); detail.put("epoch", binding.epoch); detail.put("error", failure.toString());
            try {
                Snapshot snapshot = snapshot();
                Observation observation = observe(snapshot, targets);
                detail.put("snapshot", snapshot); detail.put("client", observation);
                detail.put("serverPlayerState", game.getServer().computeOnServer(server -> {
                    var player = server.getPlayerManager().getPlayer(binding.player);
                    return Map.of("loaded", player.isLoaded(), "teleporting", player.isInTeleportationState(),
                            "removed", player.isRemoved(), "notInAnyWorld", player.notInAnyWorld,
                            "alive", player.isAlive(), "watchedSection", player.getWatchedSection().toString(),
                            "inWorldPlayers", player.getEntityWorld().getPlayers().contains(player),
                            "trackedEntity", player.getEntityWorld().getEntity(player.getUuid()) == player);
                }));
                detail.put("clientScreen", context.computeOnClient(client ->
                        client.currentScreen == null ? "none" : client.currentScreen.getClass().getName()));
                detail.put("serverMissingStates", game.getServer().computeOnServer(server -> {
                    var player = server.getPlayerManager().getPlayer(binding.player);
                    return observation.missing.stream().map(pos -> Map.of("pos", pos,
                            "serverFull", player.getEntityWorld().getChunkManager().getWorldChunk(pos.x, pos.z) != null,
                            "withinFilter", player.getChunkFilter().isWithinDistance(pos))).toList();
                }));
                detail.put("packets", Inf01PacketProbe.summary());
            } catch (RuntimeException | AssertionError ended) { detail.put("sessionError", ended.toString()); }
            event("failure", detail);
            throw failure; // Preserve the assertion and original cause, not a success fallback.
        }
    }

    private static final class Budget {
        private final int limit;
        private int used;
        Budget(int limit) { this.limit = limit; }
        int remaining() { require(used < limit, "Timed out waiting for predicate (INF-01)"); return limit - used; }
        void tick(ClientGameTestContext context) { remaining(); context.waitTick(); used++; }
    }

    static String hash(List<ChunkPos> positions) {
        try {
            String text = positions.stream().map(pos -> pos.x + "," + pos.z + "\n").reduce("", String::concat);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    static void event(String kind, Object detail) {
        String line = JSON.toJson(Map.of("run", RUN, "event", kind, "detail", detail));
        System.out.println("INF01 " + line);
        if (output != null) {
            try { Files.writeString(output.resolve("events.jsonl"), line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }

    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
