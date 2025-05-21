package com.tmquan2508.IngameNetherBedrockCracker.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker;
import com.tmquan2508.IngameNetherBedrockCracker.bridge.NativeBedrockCrackerLibrary;
import com.github.netherbedrockcracker.bedrock_cracker_h;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker.LOGGER;
import static com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker.MOD_ID;

public class NetherCrackerCommand {

    private static final int SEARCH_RADIUS_BLOCKS = 128;
    private static final int Y_LEVEL_FLOOR = 4;
    private static final int Y_LEVEL_CEILING = 123;

    public record FoundBedrock(int x, int y, int z, int type) {}


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("nethercracker")
            .then(ClientCommandManager.literal("start")
                .executes(context -> {
                    if (IngameNetherBedrockCracker.isCracking.get()) {
                        context.getSource().sendError(Text.literal("A cracking task is already running. Use /nethercracker stop first."));
                        return 0;
                    }

                    ClientPlayerEntity player = context.getSource().getPlayer();
                    ClientWorld world = context.getSource().getWorld();
                    if (player == null || world == null) {
                        context.getSource().sendError(Text.literal("Command must be run by a player in a world."));
                        return 0;
                    }
                     if (!world.getRegistryKey().getValue().getPath().endsWith("the_nether")) {
                        context.getSource().sendError(Text.literal("This command can only be used in The Nether."));
                        return 0;
                    }


                    Vec3d playerPosVec = player.getPos();
                    BlockPos senderPos = new BlockPos(new Vec3i((int) playerPosVec.getX(), (int) playerPosVec.getY(), (int) playerPosVec.getZ()));
                    List<FoundBedrock> bedrockBlocks = findBedrockNearby(world, senderPos);

                    if (bedrockBlocks.isEmpty()) {
                        context.getSource().sendFeedback(Text.literal("No bedrock found at y=4 or y=123 within " + SEARCH_RADIUS_BLOCKS + " blocks."));
                        return 0;
                    }
                    
                    context.getSource().sendFeedback(Text.literal("Found " + bedrockBlocks.size() + " bedrock locations. Starting cracker..."));
                    
                    int threads = Math.max(1, Runtime.getRuntime().availableProcessors() -1);

                    CrackerTask task = new CrackerTask(
                            bedrockBlocks,
                            threads,
                            bedrock_cracker_h.BedrockGeneration_Normal(),
                            bedrock_cracker_h.OutputMode_WorldSeed()
                    );
                    IngameNetherBedrockCracker.currentTask = new Thread(task);
                    IngameNetherBedrockCracker.currentTask.setName("NBC-Worker");
                    IngameNetherBedrockCracker.currentTask.start();

                    return Command.SINGLE_SUCCESS;
                }))
            .then(ClientCommandManager.literal("info")
                .executes(context -> {
                    if (!IngameNetherBedrockCracker.isCracking.get()) {
                        context.getSource().sendFeedback(Text.literal("No cracking task is currently running."));
                        if (!IngameNetherBedrockCracker.foundSeeds.isEmpty()) {
                             context.getSource().sendFeedback(Text.literal("Last run found seeds: " + IngameNetherBedrockCracker.foundSeeds.toString()));
                        }
                        return Command.SINGLE_SUCCESS;
                    }

                    long currentTime = System.currentTimeMillis();
                    long elapsedTimeMillis = currentTime - IngameNetherBedrockCracker.startTimeMillis;
                    String elapsedTimeStr = String.format("%02d min, %02d sec",
                        TimeUnit.MILLISECONDS.toMinutes(elapsedTimeMillis),
                        TimeUnit.MILLISECONDS.toSeconds(elapsedTimeMillis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsedTimeMillis))
                    );

                    context.getSource().sendFeedback(Text.literal("Cracking task is running."));
                    context.getSource().sendFeedback(Text.literal("Time elapsed: " + elapsedTimeStr));
                    context.getSource().sendFeedback(Text.literal("Seeds found so far: " + IngameNetherBedrockCracker.foundSeeds.size()));
                    if(!IngameNetherBedrockCracker.foundSeeds.isEmpty()){
                         context.getSource().sendFeedback(Text.literal(IngameNetherBedrockCracker.foundSeeds.toString()));
                    }
                    
                    return Command.SINGLE_SUCCESS;
                }))
            .then(ClientCommandManager.literal("stop")
                .executes(context -> {
                    if (!IngameNetherBedrockCracker.isCracking.get()) {
                        context.getSource().sendFeedback(Text.literal("No cracking task is currently running."));
                        return 0;
                    }

                    if (IngameNetherBedrockCracker.currentTask != null) {
                        IngameNetherBedrockCracker.currentTask.interrupt();
                    }
                    IngameNetherBedrockCracker.isCracking.set(false); 
                    
                    synchronized(IngameNetherBedrockCracker.LOCK) {
                        if(IngameNetherBedrockCracker.nativeMemoryHolder.getNativeResults() != null) {
                            NativeBedrockCrackerLibrary.freeSeedVector(IngameNetherBedrockCracker.nativeMemoryHolder.getNativeResults());
                            IngameNetherBedrockCracker.nativeMemoryHolder.setNativeResults(null);
                            LOGGER.info("[{}] Freed native results due to stop command.", MOD_ID);
                        }
                    }

                    context.getSource().sendFeedback(Text.literal("Cracking task stopping..."));
                    return Command.SINGLE_SUCCESS;
                }))
            .executes(context -> {
                context.getSource().sendFeedback(Text.literal("Usage: /nethercracker <start|info|stop>"));
                return Command.SINGLE_SUCCESS;
            })
        );
    }

    private static List<FoundBedrock> findBedrockNearby(ClientWorld world, BlockPos center) {
        List<FoundBedrock> bedrockPositions = new ArrayList<>();
        ChunkPos centerChunkPos = new ChunkPos(center);
        int chunkRadius = (SEARCH_RADIUS_BLOCKS >> 4) + 1; 

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int r = 0; r < chunkRadius; r++) {
            for (int chunkX = centerChunkPos.x - r; chunkX <= centerChunkPos.x + r; chunkX++) {
                for (int chunkZ = centerChunkPos.z - r; chunkZ <= centerChunkPos.z + r; chunkZ += (chunkX == centerChunkPos.x - r || chunkX == centerChunkPos.x + r || r == 0) ? 1 : r + r) {
                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        continue;
                    }
                    Chunk chunk = world.getChunk(chunkX, chunkZ);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int worldX = (chunk.getPos().x << 4) + x;
                            int worldZ = (chunk.getPos().z << 4) + z;

                            if (Math.sqrt(center.getSquaredDistance(worldX, center.getY(), worldZ)) > SEARCH_RADIUS_BLOCKS) {
                                continue;
                            }

                            mutablePos.set(worldX, Y_LEVEL_FLOOR, worldZ);
                            if (chunk.getBlockState(mutablePos).isOf(Blocks.BEDROCK)) {
                                bedrockPositions.add(new FoundBedrock(worldX, Y_LEVEL_FLOOR, worldZ, bedrock_cracker_h.BlockType_BEDROCK()));
                            }

                            mutablePos.set(worldX, Y_LEVEL_CEILING, worldZ);
                            if (chunk.getBlockState(mutablePos).isOf(Blocks.BEDROCK)) {
                                bedrockPositions.add(new FoundBedrock(worldX, Y_LEVEL_CEILING, worldZ, bedrock_cracker_h.BlockType_BEDROCK()));
                            }
                        }
                    }
                }
            }
        }
        return bedrockPositions;
    }
}