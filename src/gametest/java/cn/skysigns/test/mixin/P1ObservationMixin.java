package cn.skysigns.test.mixin;

import cn.skysigns.test.P1Probe;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public abstract class P1ObservationMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;tickBlockEntities()V"), require = 1)
    private void skysigns$observe(BooleanSupplier supplier, CallbackInfo ci) { P1Probe.beforeBlockEntities((ServerWorld) (Object) this); }
}
