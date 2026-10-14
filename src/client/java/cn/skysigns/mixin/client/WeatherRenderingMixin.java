package cn.skysigns.mixin.client;

import cn.skysigns.client.render.WindPrecipitationVisual;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.WeatherRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WeatherRendering.class)
public abstract class WeatherRenderingMixin {
    @ModifyArg(method = "renderPieces", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;vertex(FFF)Lnet/minecraft/client/render/VertexConsumer;",
            ordinal = 0), index = 0, require = 1)
    private float skysigns$leanFirstTopX(float x, @Local WeatherRendering.Piece piece) {
        return x + WindPrecipitationVisual.topOffsetX(piece.topY() - piece.bottomY());
    }

    @ModifyArg(method = "renderPieces", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;vertex(FFF)Lnet/minecraft/client/render/VertexConsumer;",
            ordinal = 0), index = 2, require = 1)
    private float skysigns$leanFirstTopZ(float z, @Local WeatherRendering.Piece piece) {
        return z + WindPrecipitationVisual.topOffsetZ(piece.topY() - piece.bottomY());
    }

    @ModifyArg(method = "renderPieces", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;vertex(FFF)Lnet/minecraft/client/render/VertexConsumer;",
            ordinal = 1), index = 0, require = 1)
    private float skysigns$leanSecondTopX(float x, @Local WeatherRendering.Piece piece) {
        return x + WindPrecipitationVisual.topOffsetX(piece.topY() - piece.bottomY());
    }

    @ModifyArg(method = "renderPieces", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;vertex(FFF)Lnet/minecraft/client/render/VertexConsumer;",
            ordinal = 1), index = 2, require = 1)
    private float skysigns$leanSecondTopZ(float z, @Local WeatherRendering.Piece piece) {
        return z + WindPrecipitationVisual.topOffsetZ(piece.topY() - piece.bottomY());
    }
}
