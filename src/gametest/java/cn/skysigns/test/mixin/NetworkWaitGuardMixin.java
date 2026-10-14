package cn.skysigns.test.mixin;

import com.google.common.collect.ConcurrentHashMultiset;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import net.fabricmc.fabric.impl.client.gametest.threading.NetworkSynchronizer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Explicit test-only opt-in. Keeps the original pending-packet and queued-task timeouts intact. */
@Mixin(value = NetworkSynchronizer.class, remap = false)
public abstract class NetworkWaitGuardMixin {
    @Shadow @Final private AtomicInteger inFlightPackets;
    @Shadow @Final private ConcurrentHashMultiset<?> mainThreadPacketHandlers;

    @Unique private boolean skysigns$needsAwait() {
        return inFlightPackets.get() > 0 && mainThreadPacketHandlers.isEmpty();
    }

    @WrapOperation(method = "waitForPacketHandlers", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/locks/Condition;await(JLjava/util/concurrent/TimeUnit;)Z"), require = 1)
    private boolean skysigns$guardWait(Condition condition, long duration, TimeUnit unit, Operation<Boolean> original) {
        // Fabric holds morePacketsLock here. Recheck after acquiring it, before atomically releasing it in await.
        if (!skysigns$needsAwait()) return true;
        boolean signaled = original.call(condition, duration, unit);
        // A timeout may race a completed receive; only an outstanding predicate should invalidate the synchronizer.
        return signaled || !skysigns$needsAwait();
    }
}
