package cn.skysigns.mixin;

import cn.skysigns.weather.WeatherManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public abstract class ServerWeatherMixin {
    @Inject(method = "tickWeather", at = @At("HEAD"), require = 1)
    private void skysigns$beforeWeather(CallbackInfo ci) { WeatherManager.beforeWeather((ServerWorld) (Object) this); }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;tickTime()V", shift = At.Shift.AFTER), require = 1)
    private void skysigns$afterClock(BooleanSupplier shouldKeepTicking, CallbackInfo ci) { WeatherManager.tick((ServerWorld) (Object) this); }

    @Inject(method = {"setWeather", "resetWeather"}, at = @At("HEAD"), require = 1)
    private void skysigns$beforeWrite(CallbackInfo ci) { WeatherManager.beginExternal((ServerWorld) (Object) this); }

    @Inject(method = "setWeather", at = @At("RETURN"), require = 1)
    private void skysigns$weatherWritten(int clear, int rain, boolean raining, boolean thundering, CallbackInfo ci) {
        WeatherManager.endExternal((ServerWorld) (Object) this, raining ? rain : clear);
    }
    @Inject(method = "resetWeather", at = @At("RETURN"), require = 1)
    private void skysigns$weatherReset(CallbackInfo ci) { WeatherManager.endExternal((ServerWorld) (Object) this, -1); }
}
