package cn.skysigns.client.render;

import cn.skysigns.block.WeatherVaneBlock;
import cn.skysigns.block.entity.WeatherVaneBlockEntity;
import cn.skysigns.registry.ModItems;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class WeatherVaneBlockEntityRenderer
        implements BlockEntityRenderer<WeatherVaneBlockEntity, WeatherVaneRenderState> {
    private static final float SMOOTHING = 0.22F;
    private final ItemModelManager itemModelManager;

    public WeatherVaneBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        this.itemModelManager = context.itemModelManager();
    }

    @Override
    public WeatherVaneRenderState createRenderState() {
        return new WeatherVaneRenderState();
    }

    @Override
    public void updateRenderState(
            WeatherVaneBlockEntity blockEntity,
            WeatherVaneRenderState state,
            float tickProgress,
            Vec3d cameraPos,
            CrumblingOverlayCommand crumblingOverlay
    ) {
        BlockEntityRenderer.super.updateRenderState(
                blockEntity, state, tickProgress, cameraPos, crumblingOverlay
        );

        int targetAngle = blockEntity.getCachedState().get(WeatherVaneBlock.ANGLE);
        if (!state.initialized) {
            state.renderedAngle = targetAngle;
            state.initialized = true;
        } else if (!cn.skysigns.block.WaterAwareInstrument.isSubmerged(
                blockEntity.getEntityWorld(), blockEntity.getPos())) {
            state.renderedAngle = MathHelper.lerpAngleDegrees(
                    SMOOTHING, state.renderedAngle, targetAngle
            );
        }

        itemModelManager.clearAndUpdate(
                state.rotor,
                ModItems.WEATHER_VANE_ROTOR.getDefaultStack(),
                ItemDisplayContext.FIXED,
                blockEntity.getEntityWorld(),
                blockEntity,
                (int) blockEntity.getPos().asLong()
        );
    }

    @Override
    public void render(
            WeatherVaneRenderState state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraState
    ) {
        matrices.push();
        matrices.translate(0.5F, 0.82F, 0.5F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F - state.renderedAngle));
        state.rotor.render(
                matrices,
                queue,
                state.lightmapCoordinates,
                OverlayTexture.DEFAULT_UV,
                0
        );
        matrices.pop();
    }
}
