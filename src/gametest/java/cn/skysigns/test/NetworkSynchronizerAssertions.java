package cn.skysigns.test;

import cn.skysigns.SkySigns;
import com.google.gson.GsonBuilder;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import net.fabricmc.fabric.impl.client.gametest.threading.NetworkSynchronizer;
import net.minecraft.util.thread.ThreadExecutor;

/** Controlled Fabric race reproducer. Never operates on either live global synchronizer. */
final class NetworkSynchronizerAssertions {
    private NetworkSynchronizerAssertions() { }

    static void run() {
        require(System.getenv("SKYSIGNS_ISSUE9_MODE") == null
                && !"1".equals(System.getenv("SKYSIGNS_PERFORMANCE_TEST")), "Selftest is separate from performance capture");
        String output = System.getenv("SKYSIGNS_NETWORK_SELFTEST_OUTPUT");
        require(output != null && Path.of(output).isAbsolute(), "Require a new absolute selftest output directory");
        Path root = Path.of(output);
        require(!Files.exists(root), "Refusing to overwrite a previous selftest");
        boolean guarded = "1".equals(System.getenv("SKYSIGNS_NETWORK_WAIT_GUARD"));
        List<Map<String, Object>> cases = new ArrayList<>();
        try {
            Files.createDirectory(root);
            NetworkSynchronizer balanced = new NetworkSynchronizer();
            balanced.preSendPacket();
            balanced.preNettyHandlePacket();
            balanced.postNettyHandlePacket();
            balanced.waitForPacketHandlers(new EmptyExecutor());
            require(count(balanced) == 0 && !invalid(balanced), "Balanced packet must remain valid");
            cases.add(Map.of("case", "balanced", "inFlight", count(balanced), "invalid", invalid(balanced)));

            NetworkSynchronizer raced = new NetworkSynchronizer();
            Lock lock = (Lock) field(raced, "morePacketsLock");
            WaitAttempt attempt;
            lock.lock();
            try {
                raced.preSendPacket();
                attempt = startWait(raced);
                // Wait until the worker has checked the predicate and is blocked acquiring this lock.
                long deadline = System.nanoTime() + 5_000_000_000L;
                boolean blocked = false;
                while (System.nanoTime() < deadline) {
                    var info = ManagementFactory.getThreadMXBean().getThreadInfo(attempt.worker.threadId());
                    if (info != null && info.getLockOwnerId() == Thread.currentThread().threadId()) {
                        blocked = true;
                        break;
                    }
                    Thread.sleep(1);
                }
                require(blocked, "Worker did not reach the guarded lock-acquisition boundary");
                raced.preNettyHandlePacket();
                raced.postNettyHandlePacket(); // balanced receive signals before the worker starts awaiting
                require(count(raced) == 0, "The single packet must complete before await");
            } finally {
                lock.unlock();
            }
            awaitWorker(attempt);
            if (guarded) {
                require(attempt.failure.get() == null && !invalid(raced) && attempt.elapsedMs.get() < 9000,
                        "Guarded empty state must return without a ten-second timeout");
            } else {
                requireSynchronizerFailure(attempt.failure.get());
                require(invalid(raced) && attempt.elapsedMs.get() >= 9000,
                        "Unmodified Fabric must reproduce the lost-notification timeout");
            }
            cases.add(Map.of("case", "receive-before-await", "inFlight", count(raced),
                    "invalid", invalid(raced), "elapsedMs", attempt.elapsedMs.get(), "guarded", guarded));

            NetworkSynchronizer pending = new NetworkSynchronizer();
            pending.preSendPacket(); // intentionally never received, on this isolated object only
            WaitAttempt unresolved = startWait(pending);
            awaitWorker(unresolved);
            requireSynchronizerFailure(unresolved.failure.get());
            require(count(pending) == 1 && invalid(pending) && unresolved.elapsedMs.get() >= 9000,
                    "A real pending packet must still fail after the original timeout");
            cases.add(Map.of("case", "unreceived-packet", "inFlight", count(pending),
                    "invalid", invalid(pending), "elapsedMs", unresolved.elapsedMs.get()));

            NetworkSynchronizer negative = new NetworkSynchronizer();
            negative.postNettyHandlePacket(); // deliberately unmatched receive on an isolated object
            Throwable negativeFailure = null;
            try { negative.postTaskRun(() -> { }); } catch (AssertionError error) { negativeFailure = error; }
            requireSynchronizerFailure(negativeFailure);
            require(count(negative) == -1 && invalid(negative), "Negative counter protection must remain active");
            cases.add(Map.of("case", "unmatched-receive", "inFlight", count(negative), "invalid", invalid(negative)));

            Files.writeString(root.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(
                    Map.of("passed", true, "controlledReproducer", true, "liveSynchronizersModified", false,
                            "sourceCommit", String.valueOf(System.getenv("SKYSIGNS_NETWORK_SELFTEST_COMMIT")),
                            "guarded", guarded, "cases", cases)));
            SkySigns.LOGGER.info("SKYSIGNS_NETWORK_SELFTEST passed guarded={} cases={}", guarded, cases);
        } catch (Throwable error) {
            try { Files.writeString(root.resolve("failure.json"), error.toString()); }
            catch (Exception writeError) { error.addSuppressed(writeError); }
            throw new AssertionError("Controlled synchronizer selftest failed; preserve " + root, error);
        }
    }

    private static WaitAttempt startWait(NetworkSynchronizer synchronizer) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicLong elapsed = new AtomicLong();
        Thread worker = new Thread(() -> {
            long start = System.nanoTime();
            try { synchronizer.waitForPacketHandlers(new EmptyExecutor()); }
            catch (Throwable error) { failure.set(error); }
            finally { elapsed.set((System.nanoTime() - start) / 1_000_000); }
        }, "skysigns-isolated-synchronizer-wait");
        worker.setDaemon(true);
        worker.start();
        return new WaitAttempt(worker, failure, elapsed);
    }

    private static void awaitWorker(WaitAttempt attempt) throws InterruptedException {
        attempt.worker.join(15000);
        if (attempt.worker.isAlive()) {
            attempt.worker.interrupt();
            attempt.worker.join(1000);
            throw new AssertionError("Isolated waiter exceeded the original ten-second timeout budget");
        }
    }

    private static Object field(NetworkSynchronizer synchronizer, String name) throws ReflectiveOperationException {
        Field field = NetworkSynchronizer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(synchronizer);
    }
    private static int count(NetworkSynchronizer synchronizer) throws ReflectiveOperationException {
        return ((AtomicInteger) field(synchronizer, "inFlightPackets")).get();
    }
    private static boolean invalid(NetworkSynchronizer synchronizer) throws ReflectiveOperationException {
        return ((AtomicBoolean) field(synchronizer, "invalid")).get();
    }
    private static void requireSynchronizerFailure(Throwable failure) {
        require(failure instanceof AssertionError && failure.getMessage().contains("Network synchronizer in invalid state"),
                "Expected original Fabric invalid-state assertion, got " + failure);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private record WaitAttempt(Thread worker, AtomicReference<Throwable> failure, AtomicLong elapsedMs) { }
    private static final class EmptyExecutor extends ThreadExecutor<Runnable> {
        private final Thread owner = Thread.currentThread();
        EmptyExecutor() { super("skysigns-isolated-executor"); }
        @Override public Runnable createTask(Runnable runnable) { return runnable; }
        @Override protected boolean canExecute(Runnable runnable) { return true; }
        @Override protected Thread getThread() { return owner; }
    }
}
