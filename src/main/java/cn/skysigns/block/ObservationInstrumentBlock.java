package cn.skysigns.block;

import cn.skysigns.weather.AtmosphericReadings;
import cn.skysigns.weather.WeatherManager;
import cn.skysigns.weather.WindManager;
import cn.skysigns.weather.WindState;
import cn.skysigns.network.StationReportPayload;
import cn.skysigns.block.entity.MeterBlockEntity;
import cn.skysigns.registry.ModBlockEntities;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class ObservationInstrumentBlock extends Block implements BlockEntityProvider {
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    private static final VoxelShape METER_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(3.0, 0.0, 3.0, 13.0, 2.0, 13.0),
            Block.createCuboidShape(4.0, 2.0, 5.0, 12.0, 12.0, 11.0),
            Block.createCuboidShape(3.8, 12.0, 4.8, 12.2, 12.5, 11.2)
    );
    private static final VoxelShape METER_SIDE_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(3.0, 0.0, 3.0, 13.0, 2.0, 13.0),
            Block.createCuboidShape(5.0, 2.0, 4.0, 11.0, 12.0, 12.0),
            Block.createCuboidShape(4.8, 12.0, 3.8, 11.2, 12.5, 12.2)
    );
    private static final VoxelShape STATION_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 3.0, 14.0),
            Block.createCuboidShape(3.0, 3.0, 3.0, 13.0, 12.0, 13.0),
            Block.createCuboidShape(2.8, 12.0, 2.8, 13.2, 12.7, 13.2),
            Block.createCuboidShape(7.0, 12.7, 7.0, 9.0, 16.0, 9.0),
            Block.createCuboidShape(5.0, 14.5, 7.25, 11.0, 15.5, 8.75),
            Block.createCuboidShape(7.25, 14.5, 5.0, 8.75, 15.5, 11.0)
    );

    private final Kind kind;

    public ObservationInstrumentBlock(Settings settings, Kind kind) {
        super(settings);
        this.kind = kind;
        setDefaultState(getDefaultState().with(FACING, Direction.SOUTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return kind == Kind.WEATHER_STATION ? null : new MeterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world, BlockState state, BlockEntityType<T> type
    ) {
        if (world.isClient() || kind == Kind.WEATHER_STATION || type != ModBlockEntities.METER) {
            return null;
        }
        return (tickerWorld, pos, tickerState, blockEntity) -> {
            if (tickerWorld instanceof ServerWorld serverWorld && blockEntity instanceof MeterBlockEntity meter
                    // Keep one sample per second per meter, but spread meters across ticks
                    // so a cluster of hygrometers does not scan nearby fluids at once.
                    && (tickerWorld.getTime() + pos.hashCode()) % 20L == 0L) {
                meter.sample(serverWorld);
            }
        };
    }

    @Override
    protected ActionResult onUseWithItem(net.minecraft.item.ItemStack stack, BlockState state, World world,
            BlockPos pos, PlayerEntity player, net.minecraft.util.Hand hand, BlockHitResult hit) {
        if (kind == Kind.WEATHER_STATION && (stack.isOf(net.minecraft.item.Items.WATER_BUCKET) || stack.isOf(net.minecraft.item.Items.BUCKET))) return ActionResult.PASS;
        return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.SUCCESS;
        }

        if (kind == Kind.WEATHER_STATION) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                sendStationReport(serverWorld, serverPlayer, pos, true);
            }
        } else {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (!(blockEntity instanceof MeterBlockEntity)) {
                // Upgrade instruments placed before meters acquired block entities.
                MeterBlockEntity restored = new MeterBlockEntity(pos, state);
                serverWorld.addBlockEntity(restored);
                blockEntity = restored;
            }
            if (blockEntity instanceof MeterBlockEntity meter) {
                if (meter.isReadOnly()) {
                    player.sendMessage(Text.translatable("message.skysigns.save_read_only"), false);
                    return ActionResult.SUCCESS_SERVER;
                }
                meter.sample(serverWorld);
                if (kind == Kind.BAROMETER) {
                    player.sendMessage(Text.translatable("message.skysigns.barometer",
                            AtmosphericReadings.pressureText(serverWorld)), false);
                } else {
                    player.sendMessage(Text.translatable("message.skysigns.hygrometer",
                            AtmosphericReadings.humidityText(meter.readingPercent())), false);
                }
            }
        }
        return ActionResult.SUCCESS_SERVER;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeFor(state);
    }

    private VoxelShape shapeFor(BlockState state) {
        if (kind == Kind.WEATHER_STATION) return STATION_SHAPE;
        return state.get(FACING).getAxis() == Direction.Axis.X ? METER_SIDE_SHAPE : METER_SHAPE;
    }

    public static void sendStationReport(ServerWorld world, ServerPlayerEntity player,
                                         BlockPos pos, boolean openScreen) {
        if (WaterAwareInstrument.isSubmerged(world, pos)) {
            if (openScreen) {
                player.sendMessage(Text.translatable("message.skysigns.station_submerged"), false);
            }
            return;
        }
        if (!ServerPlayNetworking.canSend(player, StationReportPayload.ID)) {
            if (openScreen) {
                player.sendMessage(Text.translatableWithFallback("message.skysigns.station_requires_update",
                        "[Sky Signs] Update client and server to the same version to use the station."), false);
            }
            return;
        }
        AtmosphericReadings.Snapshot readings = AtmosphericReadings.measure(world, pos);
        WeatherManager.View weather = WeatherManager.view(world);
        WindState wind = WindManager.get(world);
        int flags = openScreen ? StationReportPayload.OPEN_FLAG : 0;
        int secondsRemaining = -1;
        if (weather.automatic()) {
            flags |= weather.progressPercent();
            secondsRemaining = weather.secondsRemaining();
        } else {
            flags |= StationReportPayload.MANUAL_FLAG;
        }
        ServerPlayNetworking.send(player, new StationReportPayload(
                WeatherManager.describe(weather), readings.localPrecipitation(),
                readings.pressureText(), readings.humidityText(),
                WindManager.describe(wind), readings.forecast(),
                Math.round(readings.pressureHpa()), readings.humidityPercent(),
                Math.round(wind.strength() * 100.0F), pos.asLong(), flags, secondsRemaining
        ));
    }

    public enum Kind {
        BAROMETER,
        HYGROMETER,
        WEATHER_STATION
    }
}
