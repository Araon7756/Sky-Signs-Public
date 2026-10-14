package cn.skysigns.test.mixin;

import cn.skysigns.test.P1Probe;
import cn.skysigns.weather.AtmosphericReadings;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AtmosphericReadings.class)
public abstract class P1ReadingsMixin {
    @Inject(method = "hasNearbyWater", at = @At("HEAD"), remap = false, require = 1)
    private static void skysigns$count(ServerWorld w, BlockPos pos, CallbackInfoReturnable<Boolean> ci) { P1Probe.waterChecks++; }
}
