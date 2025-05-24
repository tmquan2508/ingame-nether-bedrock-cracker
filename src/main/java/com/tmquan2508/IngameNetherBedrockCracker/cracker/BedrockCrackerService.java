package com.tmquan2508.IngameNetherBedrockCracker.cracker;

import com.github.netherbedrockcracker.*;
import com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.dto.BlockInput;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.dto.CrackerProgressInfo;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.enums.CrackerMode;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.enums.CrackerOutputMode;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class BedrockCrackerService {

    private final ExecutorService crackerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BedrockCrackerThread");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean isCrackingInternal = new AtomicBoolean(false);
    private CompletableFuture<List<Long>> crackingTaskFuture;

    public BedrockCrackerService() {}

    public synchronized boolean startCracking(List<BlockInput> blockInputs, CrackerMode mode, int threads) {
        if (isCrackingInternal.getAndSet(true)) {
            IngameNetherBedrockCracker.LOGGER.warn("Cracking process was already active or attempted to start concurrently.");
            isCrackingInternal.set(true);
            return false;
        }
        if (blockInputs == null || blockInputs.isEmpty()) {
            IngameNetherBedrockCracker.LOGGER.error("Block input list is empty, cannot start cracking.");
            isCrackingInternal.set(false);
            return false;
        }

        IngameNetherBedrockCracker.LOGGER.info("Starting native cracking process. Blocks: {}, Mode: {}, Threads: {}", blockInputs.size(), mode.name(), threads);
        bedrock_cracker_h.reset_crack_progress_externally_ffi();

        crackingTaskFuture = CompletableFuture.supplyAsync(() -> {
            List<Long> foundSeeds = new ArrayList<>();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment blocksArraySegment = Block.allocateArray(blockInputs.size(), arena);
                for (int i = 0; i < blockInputs.size(); i++) {
                    BlockInput input = blockInputs.get(i);
                    MemorySegment blockSegment = blocksArraySegment.asSlice((long) i * Block.sizeof());
                    Block.x$set(blockSegment, input.getX());
                    Block.y$set(blockSegment, input.getY());
                    Block.z$set(blockSegment, input.getZ());
                    Block.block_type$set(blockSegment, input.getBlockType());
                }

                long estimatedResults = bedrock_cracker_h.estimate_result_amount_ffi(blocksArraySegment, blockInputs.size());
                IngameNetherBedrockCracker.LOGGER.info("Native: Estimated results to find: {}", estimatedResults);
                if (estimatedResults == 0 && blockInputs.size() > 0) {
                    IngameNetherBedrockCracker.LOGGER.warn("Native: Estimated 0 results despite having {} input blocks. This might indicate an issue or no possible solutions.", blockInputs.size());
                }

                int outputModeNative = CrackerOutputMode.WORLD_SEED.getNativeValue();
                // int outputModeNative = CrackerOutputMode.STRUCTURE_SEED.getNativeValue();
                MemorySegment vecI64Segment = bedrock_cracker_h.crack_ffi(arena, blocksArraySegment, blockInputs.size(), (long) threads, mode.getNativeValue(), outputModeNative);
                MemorySegment ptr = VecI64.ptr$get(vecI64Segment);
                long len = VecI64.len$get(vecI64Segment);

                if (!ptr.equals(MemorySegment.NULL) && len > 0) {
                    for (long i = 0; i < len; i++) {
                        foundSeeds.add(ptr.get(ValueLayout.JAVA_LONG, i * ValueLayout.JAVA_LONG.byteSize()));
                    }
                }
                IngameNetherBedrockCracker.LOGGER.info("Native cracking process finished. Found {} seeds.", foundSeeds.size());
                bedrock_cracker_h.free_seed_vector_ffi(vecI64Segment);
                return foundSeeds;

            } catch (Throwable t) {
                IngameNetherBedrockCracker.LOGGER.error("Exception during native cracking process: ", t);
                MinecraftClient.getInstance().execute(() -> {
                     if (MinecraftClient.getInstance().player != null) {
                        MinecraftClient.getInstance().player.sendMessage(Text.literal("Cracking Error: " + t.getMessage()), false);
                    }
                });
                return Collections.emptyList();
            }
        }, crackerExecutor);

        crackingTaskFuture.whenCompleteAsync((seeds, throwable) -> {
            isCrackingInternal.set(false);
            bedrock_cracker_h.reset_crack_progress_externally_ffi();

            MinecraftClient.getInstance().execute(() -> {
                if (throwable != null) {
                    IngameNetherBedrockCracker.LOGGER.error("Cracking task completed with an error (callback): ", throwable);
                     if (MinecraftClient.getInstance().player != null) {
                        MinecraftClient.getInstance().player.sendMessage(Text.literal("Cracking task failed: " + throwable.getMessage()), false);
                    }
                } else {
                    IngameNetherBedrockCracker.LOGGER.info("Cracking task completed (callback). Seeds found: {}", (seeds != null ? seeds.size() : "N/A"));
                    if (MinecraftClient.getInstance().player != null) {
                        if (seeds != null && !seeds.isEmpty()) {
                            MinecraftClient.getInstance().player.sendMessage(Text.literal("Cracking finished! Found " + seeds.size() + " seed(s):"), false);
                            for (Long seed : seeds) {
                                MinecraftClient.getInstance().player.sendMessage(Text.literal("- " + seed), false);
                            }
                        } else {
                            MinecraftClient.getInstance().player.sendMessage(Text.literal("Cracking finished. No seeds found for the given bedrock pattern."), false);
                        }
                    }
                }
            });
        }, MinecraftClient.getInstance()::execute);

        return true;
    }

    public CrackerProgressInfo getProgress() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment progressDataSegment = bedrock_cracker_h.get_crack_progress_ffi(arena);

            long rawTotal = ProgressData.total_units_to_process$get(progressDataSegment);
            long rawCurrent = ProgressData.units_processed_so_far$get(progressDataSegment);
            long rawFound = ProgressData.seeds_found_count$get(progressDataSegment);
            boolean rawNativeActive = ProgressData.is_cracking_active$get(progressDataSegment);

            System.out.println(
                String.format("[PROGRESS_DEBUG] Native Raw Data: Total=%d, Current=%d, Found=%d, Active=%b",
                rawTotal, rawCurrent, rawFound, rawNativeActive)
            );

            long total = rawTotal;
            long current = rawCurrent;
            long found = rawFound;
            boolean nativeActive = rawNativeActive;

            boolean serviceManagingTask = isCrackingInternal.get() && crackingTaskFuture != null && !crackingTaskFuture.isDone();
            boolean finalActiveState = nativeActive && serviceManagingTask;

            if (!nativeActive && isCrackingInternal.get() && (crackingTaskFuture == null || crackingTaskFuture.isDone())) {
                System.out.println("[PROGRESS_DEBUG] Discrepancy: Native inactive, service internal flag true, but task is done/null. Resetting internal flag.");
                isCrackingInternal.set(false);
                finalActiveState = false;
            } else if (!nativeActive && isCrackingInternal.get() && crackingTaskFuture != null && !crackingTaskFuture.isDone()){
                 System.out.println(
                    String.format("[PROGRESS_DEBUG] Discrepancy: Native inactive, service internal flag true, task NOT done. Final active: %b (Was nativeActive: %b, serviceManaging: %b)",
                    finalActiveState, nativeActive, serviceManagingTask)
                );
                 finalActiveState = false; // If native says not active, it's not active.
            }


            System.out.println(
                String.format("[PROGRESS_DEBUG] Calculated States: serviceManagingTask=%b, finalActiveState=%b",
                serviceManagingTask, finalActiveState)
            );

            return new CrackerProgressInfo(total, current, found, finalActiveState);

        } catch (Throwable t) {
            System.err.println("[PROGRESS_DEBUG] Error in getProgress: " + t.getMessage());
            t.printStackTrace(System.err);
            boolean fallbackActive = isCrackingInternal.get() && crackingTaskFuture != null && !crackingTaskFuture.isDone();
            return new CrackerProgressInfo(0, 0, 0, fallbackActive);
        }
    }

    public void requestStop() {
        IngameNetherBedrockCracker.LOGGER.info("Requesting to stop cracking process...");
        bedrock_cracker_h.request_stop_crack_ffi();
        if (crackingTaskFuture != null && !crackingTaskFuture.isDone()) {
             IngameNetherBedrockCracker.LOGGER.info("Stop request sent. Native process should terminate soon.");
        } else {
             IngameNetherBedrockCracker.LOGGER.info("No active cracking task to stop, or task already completed.");
             isCrackingInternal.set(false);
             bedrock_cracker_h.reset_crack_progress_externally_ffi();
        }
    }

    public boolean isCrackingActive() {
        return isCrackingInternal.get() && crackingTaskFuture != null && !crackingTaskFuture.isDone();
    }

    public void shutdown() {
        IngameNetherBedrockCracker.LOGGER.info("Shutting down BedrockCrackerService...");
        if (isCrackingActive()) {
            IngameNetherBedrockCracker.LOGGER.info("Cracking is active during shutdown. Requesting stop.");
            requestStop();
        }
        crackerExecutor.shutdown();
        try {
            if (!crackerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                IngameNetherBedrockCracker.LOGGER.warn("Cracker executor did not terminate in time, forcing shutdown.");
                crackerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            IngameNetherBedrockCracker.LOGGER.warn("Interrupted while waiting for cracker executor to terminate.");
            crackerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        IngameNetherBedrockCracker.LOGGER.info("BedrockCrackerService shutdown procedure complete.");
    }
}