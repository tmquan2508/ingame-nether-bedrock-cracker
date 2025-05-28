package com.tmquan2508.IngameNetherBedrockCracker.cracker;

import com.github.netherbedrockcracker.*;
import com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.dto.BlockInput;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.dto.CrackerProgressInfo;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.enums.CrackerMode;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.enums.CrackerOutputMode;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

public class BedrockCrackerService {

    private final ExecutorService crackerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BedrockCrackerThread");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean isCrackingInternal = new AtomicBoolean(false);
    private CompletableFuture<List<Long>> crackingTaskFuture;
    private Arena callbackArena;
    private final Queue<Long> progressivelyFoundSeeds = new ConcurrentLinkedQueue<>();

    private static BedrockCrackerService instanceForCallback;


    public BedrockCrackerService() {
        instanceForCallback = this;
    }

    private static void javaSeedFoundCallback(long seed) {
        IngameNetherBedrockCracker.LOGGER.info("Java Callback: Seed found from native: {}", seed);
        if (instanceForCallback != null) {
            instanceForCallback.progressivelyFoundSeeds.add(seed);
        }
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                String seedString = String.valueOf(seed);

                MutableText message = Text.empty()
                    .append(Text.literal("Seed discovered [")
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)))
                    .append(Text.literal(seedString)
                        .setStyle(Style.EMPTY
                            .withColor(Formatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, seedString))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Copy to clipboard")))
                        ))
                    .append(Text.literal("]")
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)));

                MinecraftClient.getInstance().player.sendMessage(message, false);
            }
        });
    }
    public synchronized boolean startCracking(List<BlockInput> blockInputs, CrackerMode mode, int threads) {
        if (isCrackingInternal.getAndSet(true)) {
            IngameNetherBedrockCracker.LOGGER.warn("Cracking process was already active or attempted to start concurrently.");
            return false;
        }
        if (blockInputs == null || blockInputs.isEmpty()) {
            IngameNetherBedrockCracker.LOGGER.error("Block input list is empty, cannot start cracking.");
            isCrackingInternal.set(false);
            return false;
        }

        IngameNetherBedrockCracker.LOGGER.info("Starting native cracking process with callbacks. Blocks: {}, Mode: {}, Threads: {}", blockInputs.size(), mode.name(), threads);
        bedrock_cracker_h.reset_crack_progress_externally_ffi();
        progressivelyFoundSeeds.clear();

        if (this.callbackArena != null && this.callbackArena.scope().isAlive()) {
            this.callbackArena.close();
        }
        this.callbackArena = Arena.ofShared();


        crackingTaskFuture = CompletableFuture.supplyAsync(() -> {
            List<Long> finalCollectedSeeds = new ArrayList<>();
            try {
                MethodHandle javaCallbackMH = MethodHandles.lookup().findStatic(
                        BedrockCrackerService.class,
                        "javaSeedFoundCallback",
                        MethodType.methodType(void.class, long.class)
                );

                FunctionDescriptor seedCallbackNativeDescriptor = FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG);
                MemorySegment seedCallbackNativePtr = Linker.nativeLinker().upcallStub(
                        javaCallbackMH,
                        seedCallbackNativeDescriptor,
                        this.callbackArena
                );

                MemorySegment blocksArraySegment = Block.allocateArray(blockInputs.size(), this.callbackArena);
                for (int i = 0; i < blockInputs.size(); i++) {
                    BlockInput input = blockInputs.get(i);
                    MemorySegment blockSegment = blocksArraySegment.asSlice((long) i * Block.sizeof());
                    Block.x$set(blockSegment, input.getX());
                    Block.y$set(blockSegment, input.getY());
                    Block.z$set(blockSegment, input.getZ());
                    Block.block_type$set(blockSegment, input.getBlockType());
                }

                long estimatedResults = bedrock_cracker_h.estimate_result_amount_ffi(blocksArraySegment, (long) blockInputs.size());
                IngameNetherBedrockCracker.LOGGER.info("Native: Estimated results to find: {}", estimatedResults);
                if (estimatedResults == 0 && blockInputs.size() > 0) {
                    IngameNetherBedrockCracker.LOGGER.warn("Native: Estimated 0 results despite having {} input blocks.", blockInputs.size());
                }

                int outputModeNative = CrackerOutputMode.WORLD_SEED.getNativeValue();
                // int outputModeNative = CrackerOutputMode.STRUCTURE_SEED.getNativeValue();

                MemorySegment vecI64Segment = bedrock_cracker_h.crack_ffi(
                        this.callbackArena,
                        blocksArraySegment,
                        (long) blockInputs.size(),
                        (long) threads,
                        mode.getNativeValue(),
                        outputModeNative,
                        seedCallbackNativePtr
                );

                MemorySegment ptr = VecI64.ptr$get(vecI64Segment);
                long len = VecI64.len$get(vecI64Segment);

                if (!ptr.equals(MemorySegment.NULL) && len > 0) {
                    for (long i = 0; i < len; i++) {
                        finalCollectedSeeds.add(ptr.get(ValueLayout.JAVA_LONG, i * ValueLayout.JAVA_LONG.byteSize()));
                    }
                }
                IngameNetherBedrockCracker.LOGGER.info("Native cracking process finished. Final collected seeds: {}. Seeds reported via callback: {}", finalCollectedSeeds.size(), progressivelyFoundSeeds.size());
                bedrock_cracker_h.free_seed_vector_ffi(vecI64Segment);
                return finalCollectedSeeds;

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

            if (this.callbackArena != null && this.callbackArena.scope().isAlive()) {
                this.callbackArena.close();
            }

            MinecraftClient.getInstance().execute(() -> {
                if (throwable != null) {
                    IngameNetherBedrockCracker.LOGGER.error("Cracking task completed with an error (whenComplete): ", throwable);
                    if (MinecraftClient.getInstance().player != null) {
                        MinecraftClient.getInstance().player.sendMessage(Text.literal("Cracking task failed: " + throwable.getMessage()), false);
                    }
                } else {
                    IngameNetherBedrockCracker.LOGGER.info("Cracking task completed (whenComplete). Final seeds from FFI: {}", (seeds != null ? seeds.size() : "N/A"));
                    if (MinecraftClient.getInstance().player != null) {
                        String message;
                        if (seeds != null && !seeds.isEmpty()) {
                            message = "Cracking finished! Final " + seeds.size() + " seed(s) list from native. Seeds were also reported progressively.";
                        } else if (!progressivelyFoundSeeds.isEmpty()) {
                            message = "Cracking finished! No final list from native, but " + progressivelyFoundSeeds.size() + " seed(s) reported progressively.";
                        } else {
                            message = "Cracking finished. No seeds found.";
                        }
                        MinecraftClient.getInstance().player.sendMessage(Text.literal(message), false);
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

            long total = rawTotal;
            long current = rawCurrent;
            long found = rawFound;
            boolean nativeActive = rawNativeActive;

            boolean serviceManagingTask = isCrackingInternal.get() && crackingTaskFuture != null && !crackingTaskFuture.isDone();
            boolean finalActiveState = nativeActive && serviceManagingTask;

            if (!nativeActive && isCrackingInternal.get() && (crackingTaskFuture == null || crackingTaskFuture.isDone())) {
                isCrackingInternal.set(false);
                finalActiveState = false;
            } else if (!nativeActive && isCrackingInternal.get() && crackingTaskFuture != null && !crackingTaskFuture.isDone()){
                finalActiveState = false;
            }
            return new CrackerProgressInfo(total, current, found, finalActiveState);

        } catch (Throwable t) {
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

        if (this.callbackArena != null && this.callbackArena.scope().isAlive()) {
            IngameNetherBedrockCracker.LOGGER.info("Closing callback arena on shutdown.");
            this.callbackArena.close();
            this.callbackArena = null;
        }
        instanceForCallback = null;
        IngameNetherBedrockCracker.LOGGER.info("BedrockCrackerService shutdown procedure complete.");
    }
}