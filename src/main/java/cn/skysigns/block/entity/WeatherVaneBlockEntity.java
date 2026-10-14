package cn.skysigns.block.entity;

import cn.skysigns.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Objects;

public final class WeatherVaneBlockEntity extends BlockEntity implements HeldItemContext {
    public WeatherVaneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WEATHER_VANE, pos, state);
    }

    @Override
    public World getEntityWorld() {
        return Objects.requireNonNull(getWorld(), "Weather vane is not attached to a world");
    }

    @Override
    public Vec3d getEntityPos() {
        return Vec3d.ofCenter(getPos());
    }

    @Override
    public float getBodyYaw() {
        return 0.0F;
    }
}
