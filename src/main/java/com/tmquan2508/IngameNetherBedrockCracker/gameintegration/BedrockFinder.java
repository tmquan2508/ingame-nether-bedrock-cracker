package com.tmquan2508.IngameNetherBedrockCracker.gameintegration;

import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.List;

public class BedrockFinder {

    public static final int SEARCH_RADIUS_BLOCKS = 128;
    public static final int Y_LEVEL_FLOOR = 4;
    public static final int Y_LEVEL_CEILING = 123;

    public record FoundBedrock(int x, int y, int z) {}

    public static List<FoundBedrock> findBedrockNearby(ClientWorld world, BlockPos center) {
        List<FoundBedrock> bedrockPositions = new ArrayList<>();
        ChunkPos centerChunkPos = new ChunkPos(center);
        int chunkRadius = (SEARCH_RADIUS_BLOCKS >> 4) + 1;
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        long searchRadiusSq = (long)SEARCH_RADIUS_BLOCKS * SEARCH_RADIUS_BLOCKS;

        for (int r = 0; r <= chunkRadius; r++) {
            for (int cxOffset = -r; cxOffset <= r; cxOffset++) {
                for (int czOffset = -r; czOffset <= r; czOffset++) {
                    if (r > 0 && Math.abs(cxOffset) < r && Math.abs(czOffset) < r) {
                        continue;
                    }

                    int currentChunkX = centerChunkPos.x + cxOffset;
                    int currentChunkZ = centerChunkPos.z + czOffset;

                    if (!world.isChunkLoaded(currentChunkX, currentChunkZ)) {
                        continue;
                    }
                    Chunk chunk = world.getChunk(currentChunkX, currentChunkZ);

                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int worldX = (chunk.getPos().x << 4) + x;
                            int worldZ = (chunk.getPos().z << 4) + z;

                            BlockPos currentColumnXZ = new BlockPos(worldX, center.getY(), worldZ);
                            if (center.getSquaredDistance(currentColumnXZ) > searchRadiusSq) {
                                continue;
                            }

                            mutablePos.set(worldX, Y_LEVEL_FLOOR, worldZ);
                            if (chunk.getBlockState(mutablePos).isOf(Blocks.BEDROCK)) {
                                bedrockPositions.add(new FoundBedrock(worldX, Y_LEVEL_FLOOR, worldZ));
                            }

                            mutablePos.set(worldX, Y_LEVEL_CEILING, worldZ);
                            if (chunk.getBlockState(mutablePos).isOf(Blocks.BEDROCK)) {
                                bedrockPositions.add(new FoundBedrock(worldX, Y_LEVEL_CEILING, worldZ));
                            }
                        }
                    }
                }
            }
        }
        return bedrockPositions;
    }
}