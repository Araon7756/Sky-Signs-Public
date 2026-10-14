package cn.skysigns.block.entity;

import cn.skysigns.registry.ModBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

public final class MeterMigration {
    private MeterMigration() {
    }

    public static void initialize() {
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> restoreMissingMeters(chunk));
    }

    private static void restoreMissingMeters(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section.isEmpty() || !section.hasAny(MeterMigration::isMeter)) {
                continue;
            }
            int startY = (chunk.sectionIndexToCoord(sectionIndex)) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (isMeter(section.getBlockState(x, y, z))) {
                            // IMMEDIATE creates a block entity for a pre-0.5 meter lacking one.
                            BlockPos pos = new BlockPos(startX + x, startY + y, startZ + z);
                            boolean missing = !chunk.getBlockEntities().containsKey(pos);
                            var meter = chunk.getBlockEntity(pos);
                            if (missing && meter != null) meter.markDirty();
                        }
                    }
                }
            }
        }
    }

    private static boolean isMeter(BlockState state) {
        return state.isOf(ModBlocks.BAROMETER) || state.isOf(ModBlocks.HYGROMETER);
    }
}
