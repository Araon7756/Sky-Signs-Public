package cn.skysigns.test.mixin;

import cn.skysigns.test.Issue9Probe;
import cn.skysigns.weather.AtmosphericReadings;
import cn.skysigns.weather.SkyWeatherType;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Development-test-only observation; every fluid lookup still executes exactly once. */
@Mixin(value = AtmosphericReadings.class, remap = false)
public abstract class HumidityMeasurementMixin {
    @Inject(method = "humidity", at = @At("HEAD"), remap = false, require = 1)
    private static void issue9$humidityStart(ServerWorld world, BlockPos pos, SkyWeatherType weather,
                                           CallbackInfoReturnable<Integer> cir) {
        Issue9Probe.humidityStart(world, pos);
    }
    @Inject(method = "humidity", at = @At("RETURN"), remap = false, require = 1)
    private static void issue9$humidityEnd(ServerWorld world, BlockPos pos, SkyWeatherType weather,
                                         CallbackInfoReturnable<Integer> cir) {
        Issue9Probe.humidityEnd(cir.getReturnValue());
    }
    @Inject(method = "hasNearbyWater", at = @At("HEAD"), remap = false, require = 1)
    private static void issue9$waterStart(ServerWorld world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Issue9Probe.waterStart();
    }
    @Inject(method = "hasNearbyWater", at = @At("RETURN"), remap = false, require = 1)
    private static void issue9$waterEnd(ServerWorld world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Issue9Probe.waterEnd(cir.getReturnValue());
    }
    @Redirect(method = "hasNearbyWater", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;getFluidState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/fluid/FluidState;"),
            remap = false, require = 1)
    private static FluidState issue9$fluidLookup(ServerWorld world, BlockPos pos) {
        if (!Issue9Probe.isCounting()) return world.getFluidState(pos);
        long start = System.nanoTime();
        FluidState result = world.getFluidState(pos);
        Issue9Probe.fluidLookup(System.nanoTime() - start);
        return result;
    }
}
