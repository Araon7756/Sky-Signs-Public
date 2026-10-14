package cn.skysigns.block;

import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import net.minecraft.util.math.Direction;
import cn.skysigns.block.entity.WeatherVaneBlockEntity;
import cn.skysigns.weather.WeatherManager;
import cn.skysigns.weather.WindManager;
import cn.skysigns.weather.WindState;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class WeatherVaneBlock extends BlockWithEntity implements WaterAwareInstrument {
    public static final MapCodec<WeatherVaneBlock> CODEC = createCodec(WeatherVaneBlock::new);
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    public static final IntProperty ANGLE = IntProperty.of("angle", 0, 359);

    private static final VoxelShape POST_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(6.5, 0.0, 6.5, 9.5, 14.0, 9.5),
            Block.createCuboidShape(6.0, 12.0, 6.0, 10.0, 16.0, 10.0)
    );
    private static final ConcurrentMap<Integer, VoxelShape> ROTATED_SHAPES = new ConcurrentHashMap<>();

    public WeatherVaneBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(ANGLE, 0).with(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        boolean submerged = context.getWorld().getFluidState(context.getBlockPos())
                .isIn(FluidTags.WATER);
        BlockState state = getDefaultState().with(WATERLOGGED, submerged);
        if (!submerged && context.getWorld() instanceof ServerWorld serverWorld) {
            return state.with(ANGLE, angleFor(WindManager.get(serverWorld).directionDegrees()));
        }
        return state;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ANGLE, WATERLOGGED);
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient()) {
            world.scheduleBlockTick(pos, this, 1);
        }
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (WaterAwareInstrument.isSubmerged(world, pos)) {
            world.scheduleBlockTick(pos, this, 1);
            return;
        }
        int targetAngle = angleFor(WindManager.get(world).directionDegrees());
        if (state.get(ANGLE) != targetAngle) {
            world.setBlockState(pos, state.with(ANGLE, targetAngle), Block.NOTIFY_LISTENERS);
        }
        world.scheduleBlockTick(pos, this, 1);
    }

    @Override
    protected ActionResult onUseWithItem(net.minecraft.item.ItemStack stack, BlockState state, World world,
            BlockPos pos, PlayerEntity player, net.minecraft.util.Hand hand, BlockHitResult hit) {
        if ((stack.isOf(net.minecraft.item.Items.WATER_BUCKET) || stack.isOf(net.minecraft.item.Items.BUCKET))) return ActionResult.PASS;
        return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world instanceof ServerWorld serverWorld) {
            if (WaterAwareInstrument.isSubmerged(world, pos)) {
                player.sendMessage(Text.translatable("message.skysigns.vane_submerged"), false);
                return ActionResult.SUCCESS_SERVER;
            }
            WindState wind = WindManager.get(serverWorld);
            player.sendMessage(Text.translatable("message.skysigns.status_vane",
                    WeatherManager.describe(serverWorld), WindManager.describe(wind)), false);
            return ActionResult.SUCCESS_SERVER;
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false)
                : super.getFluidState(state);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world,
            ScheduledTickView ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, Random random) {
        if (state.get(WATERLOGGED)) {
            ticks.scheduleFluidTick(pos, Fluids.WATER,
                    Fluids.WATER.getTickRate(world));
        }
        return super.getStateForNeighborUpdate(state, world, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeFor(state.get(ANGLE));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeFor(state.get(ANGLE));
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new WeatherVaneBlockEntity(pos, state);
    }

    private static int angleFor(float degrees) {
        return Math.floorMod(Math.round(degrees), 360);
    }

    private static VoxelShape shapeFor(int angle) {
        return ROTATED_SHAPES.computeIfAbsent(angle, WeatherVaneBlock::createRotatedShape);
    }

    private static VoxelShape createRotatedShape(int angle) {
        // The item renderer turns the rotor around (8, 8, 8) and places it at Y=0.82.
        // Split the long, narrow shaft so its collision does not become a large box at diagonals.
        double radians = Math.toRadians(90.0 - angle);
        VoxelShape shape = POST_SHAPE;
        for (int segment = 0; segment < 12; segment++) {
            double fromX = 1.0 + segment * 14.0 / 12.0;
            double toX = 1.0 + (segment + 1) * 14.0 / 12.0;
            shape = VoxelShapes.union(shape, rotatedCuboid(fromX, 7.25, 7.25,
                    toX, 8.75, 8.75, radians));
        }
        shape = VoxelShapes.union(shape, rotatedCuboid(12.0, 5.5, 7.25,
                15.0, 10.5, 8.75, radians));
        return VoxelShapes.union(shape, rotatedCuboid(2.0, 8.75, 7.25,
                5.0, 11.5, 8.75, radians));
    }

    private static VoxelShape rotatedCuboid(double fromX, double fromY, double fromZ,
                                            double toX, double toY, double toZ, double radians) {
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{fromX, toX}) {
            for (double z : new double[]{fromZ, toZ}) {
                double centeredX = x - 8.0;
                double centeredZ = z - 8.0;
                double rotatedX = 8.0 + centeredX * cos + centeredZ * sin;
                double rotatedZ = 8.0 - centeredX * sin + centeredZ * cos;
                minX = Math.min(minX, rotatedX);
                maxX = Math.max(maxX, rotatedX);
                minZ = Math.min(minZ, rotatedZ);
                maxZ = Math.max(maxZ, rotatedZ);
            }
        }
        double rotorBaseY = 0.82 * 16.0 - 8.0;
        return Block.createCuboidShape(minX, fromY + rotorBaseY, minZ,
                maxX, toY + rotorBaseY, maxZ);
    }
}
