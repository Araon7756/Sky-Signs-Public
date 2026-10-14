package cn.skysigns.test.mixin;

import cn.skysigns.SkySigns;
import com.google.common.collect.ConcurrentHashMultiset;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import net.fabricmc.fabric.impl.client.gametest.threading.NetworkSynchronizer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic-only history; never changes counters or suppresses synchronization errors. */
@Mixin(value = NetworkSynchronizer.class, remap = false)
public abstract class NetworkDiagnosticMixin {
    @Shadow @Final private AtomicInteger inFlightPackets;
    @Shadow @Final private ConcurrentHashMultiset<?> mainThreadPacketHandlers;
    @Unique private final String[] skysigns$history = new String[128];
    @Unique private int skysigns$cursor;

    @Unique private synchronized void skysigns$record(String event) {
        skysigns$history[skysigns$cursor++ % skysigns$history.length] = System.nanoTime() + ":"
                + Thread.currentThread().getName() + ":" + event + ":inFlight=" + inFlightPackets.get()
                + ":queued=" + mainThreadPacketHandlers.size();
    }
    @Inject(method = "preSendPacket", at = @At("RETURN"))
    private void skysigns$sent(CallbackInfo ci) { skysigns$record("sent"); }
    @Inject(method = "postNettyHandlePacket", at = @At("HEAD"))
    private void skysigns$received(CallbackInfo ci) { skysigns$record("receive-before-decrement"); }
    @Inject(method = "reset", at = @At("HEAD"))
    private void skysigns$reset(CallbackInfo ci) {
        skysigns$record("reset-before");
        SkySigns.LOGGER.info("SKYSIGNS_NETWORK_RESET instance={} inFlight={} thread={}",
                System.identityHashCode(this), inFlightPackets.get(), Thread.currentThread().getName());
    }
    @Inject(method = "reset", at = @At("RETURN"))
    private void skysigns$resetDone(CallbackInfo ci) { skysigns$record("reset-after"); }
    @WrapOperation(method = "waitForPacketHandlers", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/locks/Condition;await(JLjava/util/concurrent/TimeUnit;)Z"), require = 1)
    private boolean skysigns$wait(Condition condition, long duration, TimeUnit unit, Operation<Boolean> original) {
        skysigns$record("await-before");
        boolean signaled = original.call(condition, duration, unit);
        skysigns$record("await-after:signaled=" + signaled);
        return signaled;
    }
    @Inject(method = "markInvalid", at = @At("HEAD"))
    private synchronized void skysigns$invalid(CallbackInfo ci) {
        skysigns$record("invalid");
        String side = (Object) this == NetworkSynchronizer.CLIENTBOUND ? "clientbound"
                : (Object) this == NetworkSynchronizer.SERVERBOUND ? "serverbound" : "isolated-test-instance";
        SkySigns.LOGGER.error("SKYSIGNS_NETWORK_INVALID side={} inFlight={} queued={} instance={}",
                side, inFlightPackets.get(), mainThreadPacketHandlers.size(), System.identityHashCode(this),
                new IllegalStateException("Diagnostic callsite; original synchronizer failure is unchanged"));
        int start = Math.max(0, skysigns$cursor - skysigns$history.length);
        for (int i = start; i < skysigns$cursor; i++) {
            SkySigns.LOGGER.error("SKYSIGNS_NETWORK_DIAGNOSTIC instance={} {}",
                    System.identityHashCode(this), skysigns$history[i % skysigns$history.length]);
        }
    }
}
