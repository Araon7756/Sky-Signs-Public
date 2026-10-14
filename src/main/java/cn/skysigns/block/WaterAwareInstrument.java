package cn.skysigns.block;

import net.minecraft.block.Waterloggable;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

/** Observe water contact without turning incoming flowing water into a source. */
public interface WaterAwareInstrument extends Waterloggable {
    static boolean isSubmerged(BlockView world, BlockPos pos) {
        if (world.getFluidState(pos).isIn(FluidTags.WATER)
                || world.getFluidState(pos.up()).isIn(FluidTags.WATER)) return true;
        // Fixed local contact checks; no radius scan or fluid mutation.
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (world.getFluidState(pos.offset(direction)).isIn(FluidTags.WATER)) return true;
        }
        return false;
    }
}
