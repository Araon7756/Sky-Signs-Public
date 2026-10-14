package cn.skysigns.client.render;

import cn.skysigns.block.ObservationInstrumentBlock;
import cn.skysigns.block.entity.MeterBlockEntity;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class MeterBlockEntityRenderer implements BlockEntityRenderer<MeterBlockEntity, MeterRenderState> {
    private final ItemModelManager itemModelManager;

    public MeterBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        this.itemModelManager = context.itemModelManager();
    }

    @Override
    public MeterRenderState createRenderState() {
        return new MeterRenderState();
    }

    @Override
    public void updateRenderState(MeterBlockEntity blockEntity, MeterRenderState state, float tickProgress,
                                  Vec3d cameraPos, CrumblingOverlayCommand crumblingOverlay) {
        BlockEntityRenderer.super.updateRenderState(blockEntity, state, tickProgress, cameraPos, crumblingOverlay);
        int target = blockEntity.readingPercent();
        if (!state.initialized) {
            state.renderedPercent = target;
            state.initialized = true;
        } else {
            state.renderedPercent = MathHelper.lerp(0.2F, state.renderedPercent, target);
        }
        boolean barometer = ((ObservationInstrumentBlock) blockEntity.getCachedState().getBlock()).kind()
                == ObservationInstrumentBlock.Kind.BAROMETER;
        Direction facing = blockEntity.getCachedState().get(ObservationInstrumentBlock.FACING);
        state.facingDegrees = switch (facing) {
            case EAST -> 90.0F;
            case NORTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        itemModelManager.clearAndUpdate(state.needle,
                (barometer ? ModItems.BAROMETER_NEEDLE : ModItems.HYGROMETER_NEEDLE).getDefaultStack(),
                ItemDisplayContext.FIXED, blockEntity.getEntityWorld(), blockEntity,
                (int) blockEntity.getPos().asLong());
    }

    @Override
    public void render(MeterRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
                       CameraRenderState cameraState) {
        matrices.push();
        matrices.translate(0.5F, 0.0F, 0.5F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.facingDegrees));
        matrices.translate(-0.5F, 0.0F, -0.5F);
        // Keep the entire needle in front of the raised 11.2/16 bezel.
        matrices.translate(0.5F, 7.0F / 16.0F, 11.5F / 16.0F);
        // A positive Z rotation moves the upward-pointing needle toward the left.
        // Keep low readings on the left and high readings on the right of the dial.
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(70.0F - state.renderedPercent * 1.4F));
        state.needle.render(matrices, queue, state.lightmapCoordinates, OverlayTexture.DEFAULT_UV, 0);
        matrices.pop();
    }
}
