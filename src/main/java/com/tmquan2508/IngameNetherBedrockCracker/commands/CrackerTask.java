package com.tmquan2508.IngameNetherBedrockCracker.commands;

import com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker;
import com.tmquan2508.IngameNetherBedrockCracker.bridge.NativeBedrockCrackerLibrary;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker.LOGGER;
import static com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker.MOD_ID;

public class CrackerTask implements Runnable {
    private final List<NetherCrackerCommand.FoundBedrock> blocksToCrack;
    private final int threads;
    private final int generationMode;
    private final int outputMode;

    public CrackerTask(List<NetherCrackerCommand.FoundBedrock> blocksToCrack, int threads,
                       int generationMode,
                       int outputMode) {
        this.blocksToCrack = blocksToCrack;
        this.threads = threads;
        this.generationMode = generationMode;
        this.outputMode = outputMode;
    }

    @Override
    public void run() {
        try {
            IngameNetherBedrockCracker.isCracking.set(true);
            IngameNetherBedrockCracker.startTimeMillis = System.currentTimeMillis();
            IngameNetherBedrockCracker.foundSeeds.clear();
            
            synchronized (IngameNetherBedrockCracker.LOCK) {
                if (IngameNetherBedrockCracker.nativeMemoryHolder.getNativeResults() != null) {
                    NativeBedrockCrackerLibrary.freeSeedVector(IngameNetherBedrockCracker.nativeMemoryHolder.getNativeResults());
                    IngameNetherBedrockCracker.nativeMemoryHolder.setNativeResults(null);
                }
            }


            LOGGER.info("[{}] Cracker task started with {} blocks.", MOD_ID, blocksToCrack.size());
            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(Text.literal(MOD_ID + ": Started cracking with " + blocksToCrack.size() + " bedrock locations."), false);
                }
            });

            List<Long> results = NativeBedrockCrackerLibrary.crack(
                    blocksToCrack, threads, generationMode, outputMode, IngameNetherBedrockCracker.nativeMemoryHolder
            );

            if (Thread.currentThread().isInterrupted()) {
                LOGGER.info("[{}] Cracker task was interrupted.", MOD_ID);
                 MinecraftClient.getInstance().execute(() -> {
                    if (MinecraftClient.getInstance().player != null) {
                        MinecraftClient.getInstance().player.sendMessage(Text.literal(MOD_ID + ": Cracking process was stopped."), false);
                    }
                });
                return;
            }

            IngameNetherBedrockCracker.foundSeeds.addAll(results);
            long endTime = System.currentTimeMillis();
            long duration = endTime - IngameNetherBedrockCracker.startTimeMillis;

            String timeTaken = String.format("%02d min, %02d sec",
                TimeUnit.MILLISECONDS.toMinutes(duration),
                TimeUnit.MILLISECONDS.toSeconds(duration) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))
            );

            LOGGER.info("[{}] Cracker task finished. Found {} seeds in {}.", MOD_ID, results.size(), timeTaken);
             MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(Text.literal(
                            String.format("%s: Finished cracking. Found %d seed(s) in %s.", MOD_ID, results.size(), timeTaken)
                    ), false);
                    if (!results.isEmpty()) {
                         MinecraftClient.getInstance().player.sendMessage(Text.literal("Found seeds: " + results), false);
                    }
                }
            });

        } catch (Exception e) {
            LOGGER.error("[{}] Exception in CrackerTask: {}", MOD_ID, e.getMessage(), e);
             MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(Text.literal(MOD_ID + ": An error occurred during cracking."), false);
                }
            });
        } finally {
            IngameNetherBedrockCracker.isCracking.set(false);
            IngameNetherBedrockCracker.currentTask = null;
        }
    }
}