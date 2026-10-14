package cn.skysigns.test;

import cn.skysigns.block.ObservationInstrumentBlock;
import cn.skysigns.block.WeatherStationBlock;
import cn.skysigns.block.WeatherVaneBlock;
import cn.skysigns.block.WaterAwareInstrument;
import cn.skysigns.client.screen.WeatherStationScreen;
import cn.skysigns.registry.ModBlocks;
import cn.skysigns.weather.WindManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Blocks;
import net.minecraft.block.Waterloggable;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

@SuppressWarnings("UnstableApiUsage")
final class WaterObservationAssertions {
    static void verify(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        BlockPos pos = singleplayer.getServer().computeOnServer(server -> {
            var world = server.getOverworld();
            var player = server.getPlayerManager().getPlayerList().getFirst();
            BlockPos p = player.getBlockPos().east(2);
            for (BlockPos cell : BlockPos.iterate(p.add(-1, 0, -1), p.add(1, 2, 1))) {
                world.setBlockState(cell, Blocks.AIR.getDefaultState());
            }
            for (BlockPos cell : BlockPos.iterate(p.add(-1, -1, -1), p.add(1, -1, 1))) {
                world.setBlockState(cell, Blocks.STONE.getDefaultState());
            }
            world.setBlockState(p, ModBlocks.WEATHER_VANE.getDefaultState().with(WeatherVaneBlock.ANGLE, 90));
            WindManager.setManual(world, 180, 0.5F);
            for (var block : new net.minecraft.block.Block[]{ModBlocks.WEATHER_VANE, ModBlocks.WEATHER_STATION}) {
                for (var item : new net.minecraft.item.Item[]{net.minecraft.item.Items.WATER_BUCKET, net.minecraft.item.Items.BUCKET}) {
                    require(block.getDefaultState().onUseWithItem(new ItemStack(item), world, player, Hand.MAIN_HAND,
                            new BlockHitResult(Vec3d.ofCenter(p), Direction.UP, p, false)) == net.minecraft.util.ActionResult.PASS,
                            "Water containers must reach item use instead of observation interaction");
                }
                require(!((Waterloggable) block).tryFillWithFluid(world, p, block.getDefaultState(),
                        Fluids.FLOWING_WATER.getFlowing(4, false)), "Flow must not be converted into a water source");
            }
            require(((Waterloggable) ModBlocks.WEATHER_VANE).tryFillWithFluid(world, p,
                    world.getBlockState(p), Fluids.WATER.getStill(false)), "Vane must retain incoming water");
            return p;
        });
        context.waitTicks(5);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            require(world.getBlockState(pos).get(WeatherVaneBlock.ANGLE) == 90,
                    "Submerged vane must preserve its last angle");
            ((Waterloggable) ModBlocks.WEATHER_VANE).tryDrainFluid(null, world, pos, world.getBlockState(pos));
        });
        // Drained source water leaves ordinary flowing neighbors that must recede.
        context.waitTicks(120);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            var player = server.getPlayerManager().getPlayerList().getFirst();
            require(!WaterAwareInstrument.isSubmerged(world, pos), "Drained vane water must recede");
            require(world.getBlockState(pos).get(WeatherVaneBlock.ANGLE) == 180,
                    "Dry vane must resume following wind");
            // Test real BlockItem placement, not just a synthetic default state.
            for (var water : new net.minecraft.block.BlockState[]{Blocks.WATER.getDefaultState(),
                    Blocks.WATER.getDefaultState().with(net.minecraft.block.FluidBlock.LEVEL, 3)}) {
                world.setBlockState(pos, water);
                ItemStack stack = new ItemStack(ModBlocks.WEATHER_STATION.asItem());
                var placement = new ItemPlacementContext(world, player, Hand.MAIN_HAND, stack,
                        new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
                require(placement.getBlockPos().equals(pos), "Fixture must target water cell");
                require(ModBlocks.WEATHER_STATION.getPlacementState(placement) == null,
                        "Station placement in source/flowing water must be rejected");
                require(!((BlockItem) stack.getItem()).place(placement).isAccepted(),
                        "Station BlockItem must reject underwater placement");
                require(world.getBlockState(pos).equals(water) && stack.getCount() == 1,
                        "Rejected placement must preserve water and item");
            }
            world.setBlockState(pos, ModBlocks.WEATHER_STATION.getDefaultState());
            ObservationInstrumentBlock.sendStationReport(world, player, pos, true);
        });
        context.waitForScreen(WeatherStationScreen.class);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            require(((Waterloggable) ModBlocks.WEATHER_STATION).tryFillWithFluid(world, pos,
                    world.getBlockState(pos), Fluids.WATER.getStill(false)), "Station must retain flood water");
            require(world.getBlockState(pos).get(WeatherStationBlock.WATERLOGGED), "Station must be waterlogged");
        });
        context.waitFor(client -> !(client.currentScreen instanceof WeatherStationScreen));
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            ObservationInstrumentBlock.sendStationReport(world, server.getPlayerManager().getPlayerList().getFirst(),
                    pos, true);
        });
        context.waitTicks(5);
        context.runOnClient(client -> require(!(client.currentScreen instanceof WeatherStationScreen),
                "Submerged station must not reopen with valid readings"));
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            ((Waterloggable) ModBlocks.WEATHER_STATION).tryDrainFluid(null, world, pos, world.getBlockState(pos));
        });
        context.waitTicks(120);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            require(!WaterAwareInstrument.isSubmerged(world, pos), "Drained station water must recede");
            ObservationInstrumentBlock.sendStationReport(world, server.getPlayerManager().getPlayerList().getFirst(),
                    pos, true);
        });
        context.waitForScreen(WeatherStationScreen.class);
        // Water flowing down from above must also flood a previously dry station.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            // Make this a new source transition, independent of water left by earlier cases.
            world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
            world.setBlockState(pos.up(), Blocks.WATER.getDefaultState());
            world.scheduleFluidTick(pos.up(), Fluids.WATER, Fluids.WATER.getTickRate(world));
        });
        context.waitTicks(40);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            require(WaterAwareInstrument.isSubmerged(world, pos), "Natural water contact must stop observation");
            require(!world.getBlockState(pos).get(WeatherStationBlock.WATERLOGGED),
                    "Natural flow must not create a persistent source inside the station");
        });
        context.runOnClient(client -> require(!(client.currentScreen instanceof WeatherStationScreen),
                "Natural flooding must close station screen"));
        singleplayer.getServer().runOnServer(server -> server.getOverworld().setBlockState(pos.up(),
                Blocks.AIR.getDefaultState()));
        context.waitTicks(120);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            require(!WaterAwareInstrument.isSubmerged(world, pos), "Water must recede after removing its external source");
            world.setBlockState(pos, ModBlocks.WEATHER_VANE.getDefaultState().with(WeatherVaneBlock.ANGLE, 90));
            world.setBlockState(pos.up(), Blocks.WATER.getDefaultState());
            world.scheduleFluidTick(pos.up(), Fluids.WATER, Fluids.WATER.getTickRate(world));
        });
        context.waitTicks(40);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            require(world.getBlockState(pos).get(WeatherVaneBlock.ANGLE) == 90, "Flowing water must freeze vane angle");
            require(!world.getBlockState(pos).get(WeatherVaneBlock.WATERLOGGED), "Flow must not create a source in the vane");
            world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
        });
        context.waitTicks(120);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            require(!WaterAwareInstrument.isSubmerged(world, pos), "Vane surroundings must dry after external source removal");
            require(world.getBlockState(pos).get(WeatherVaneBlock.ANGLE) == 180, "Vane must resume after flow recedes");
        });
        cn.skysigns.SkySigns.LOGGER.info("Water observation assertions passed: no flow-to-source conversion, water-container interaction, drain/recede and resume");
        ChunkReadiness.captureTarget(context, singleplayer, "water-vane-resumed",
                ChunkReadiness.Targets.area(pos.add(-8, -2, -8), pos.add(8, 3, 8)).block(pos, ModBlocks.WEATHER_VANE), pos);
        singleplayer.getServer().runOnServer(server -> server.getOverworld().setBlockState(pos,
                Blocks.AIR.getDefaultState()));
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
