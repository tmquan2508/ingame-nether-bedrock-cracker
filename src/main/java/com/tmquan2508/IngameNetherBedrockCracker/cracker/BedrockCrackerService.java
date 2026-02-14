package com.tmquan2508.IngameNetherBedrockCracker.cracker;

import com.github.netherbedrockcracker.*;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.dto.BlockInput;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.dto.CrackerProgressInfo;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.enums.CrackerMode;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.enums.CrackerOutputMode;
import com.tmquan2508.IngameNetherBedrockCracker.helpers.NativeHelper;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
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
import java.util.concurrent.atomic.AtomicLong;

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

    private final AtomicLong totalUnitsToProcess = new AtomicLong(0);
    private final AtomicLong unitsProcessedSoFar = new AtomicLong(0);
    private final AtomicLong seedsFoundByCallbackCount = new AtomicLong(0);

    public BedrockCrackerService() {
    }

    private void onInit(long totalUnits, long initialSeedsFound) {
        this.totalUnitsToProcess.set(totalUnits);
        this.unitsProcessedSoFar.set(0);
        this.seedsFoundByCallbackCount.set(initialSeedsFound);
        MinecraftClient.getInstance().execute(() -> {
            updateStatusOverlay(Text.literal("Cracking Started...").formatted(Formatting.YELLOW));
        });
    }

    private void onProgress(long processedDelta) {
        long current = this.unitsProcessedSoFar.addAndGet(processedDelta);
        long total = this.totalUnitsToProcess.get();
        if (total > 0) {
            double percentage = (double) current / total * 100.0;
            if (percentage > 100.0)
                percentage = 100.0;
            String percentStr = String.format("%.2f%%", percentage);
            MinecraftClient.getInstance().execute(() -> {
                updateStatusOverlay(Text.literal("Cracking: ").formatted(Formatting.AQUA)
                        .append(Text.literal(percentStr).formatted(Formatting.GREEN)));
            });
        }
    }

    private void updateStatusOverlay(Text message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(message, false);
        }
    }

    private void onSeedFound(long seed) {
        this.progressivelyFoundSeeds.add(seed);
        this.seedsFoundByCallbackCount.incrementAndGet();
        MinecraftClient.getInstance().execute(() -> {
            this.displaySeedToPlayer("Seed discovered: ", seed);
            updateStatusOverlay(Text.literal("★ SEED FOUND: ").formatted(Formatting.GOLD, Formatting.BOLD)
                    .append(Text.literal(String.valueOf(seed)).formatted(Formatting.WHITE)));
        });
    }

    private void sendPlayerMessage(Text message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(message, false);
        }
    }

    private void displaySeedToPlayer(String label, long seed) {
        String seedString = String.valueOf(seed);
        MutableText message = Text.empty()
                .append(Text.literal(label + "[").setStyle(Style.EMPTY.withColor(Formatting.WHITE)))
                .append(Text.literal(seedString)
                        .setStyle(Style.EMPTY
                                .withColor(Formatting.GREEN)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, seedString))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Text.literal("Copy to clipboard")))))
                .append(Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
        sendPlayerMessage(message);
    }

    public synchronized boolean startCracking(List<BlockInput> blockInputs, CrackerMode mode, int threads) {
        if (isCrackingInternal.getAndSet(true)) {
            return false;
        }
        if (blockInputs == null || blockInputs.isEmpty()) {
            isCrackingInternal.set(false);
            return false;
        }

        bedrock_cracker_h.reset_cracker_state_ffi();

        progressivelyFoundSeeds.clear();
        totalUnitsToProcess.set(0);
        unitsProcessedSoFar.set(0);
        seedsFoundByCallbackCount.set(0);

        if (this.callbackArena != null && this.callbackArena.scope().isAlive()) {
            this.callbackArena.close();
        }
        this.callbackArena = Arena.ofShared();

        crackingTaskFuture = CompletableFuture.supplyAsync(() -> {
            List<Long> finalCollectedSeeds = new ArrayList<>();
            try {
                NativeHelper nativeHelper = new NativeHelper(this.callbackArena);
                MethodHandles.Lookup lookup = MethodHandles.lookup();

                MethodHandle seedFoundMH = lookup.findVirtual(BedrockCrackerService.class, "onSeedFound",
                        MethodType.methodType(void.class, long.class)).bindTo(this);
                MethodHandle progressMH = lookup.findVirtual(BedrockCrackerService.class, "onProgress",
                        MethodType.methodType(void.class, long.class)).bindTo(this);
                MethodHandle initMH = lookup.findVirtual(BedrockCrackerService.class, "onInit",
                        MethodType.methodType(void.class, long.class, long.class)).bindTo(this);

                MemorySegment seedCallbackNativePtr = nativeHelper.createUpcall(seedFoundMH,
                        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
                MemorySegment progressCallbackNativePtr = nativeHelper.createUpcall(progressMH,
                        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
                MemorySegment initCallbackNativePtr = nativeHelper.createUpcall(initMH,
                        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

                MemorySegment blocksArraySegment = Block.allocateArray(blockInputs.size(), this.callbackArena);
                for (int i = 0; i < blockInputs.size(); i++) {
                    BlockInput input = blockInputs.get(i);
                    MemorySegment blockSegment = blocksArraySegment.asSlice((long) i * Block.sizeof());
                    Block.x$set(blockSegment, input.getX());
                    Block.y$set(blockSegment, input.getY());
                    Block.z$set(blockSegment, input.getZ());
                    Block.block_type$set(blockSegment, input.getBlockType());
                }

                int outputModeNative = CrackerOutputMode.WORLD_SEED.getNativeValue();

                MemorySegment vecI64Segment = bedrock_cracker_h.crack_ffi(
                        this.callbackArena,
                        blocksArraySegment,
                        (long) blockInputs.size(),
                        (long) threads,
                        mode.getNativeValue(),
                        outputModeNative,
                        seedCallbackNativePtr,
                        progressCallbackNativePtr,
                        initCallbackNativePtr
                );

                MemorySegment ptr = VecI64.ptr$get(vecI64Segment);
                long len = VecI64.len$get(vecI64Segment);

                if (!ptr.equals(MemorySegment.NULL) && len > 0) {
                    for (long i = 0; i < len; i++) {
                        finalCollectedSeeds.add(ptr.get(ValueLayout.JAVA_LONG, i * ValueLayout.JAVA_LONG.byteSize()));
                    }
                }
                bedrock_cracker_h.free_seed_vector_ffi(vecI64Segment);
                return finalCollectedSeeds;

            } catch (Throwable t) {
                MinecraftClient.getInstance().execute(() -> sendPlayerMessage(
                        Text.literal("Cracking Error: " + t.getMessage()).formatted(Formatting.RED)));
                return Collections.emptyList();
            }
        }, crackerExecutor);

        crackingTaskFuture.whenCompleteAsync((seedsFromNativeFunction, throwable) -> {
            isCrackingInternal.set(false);
            bedrock_cracker_h.reset_cracker_state_ffi();

            if (this.callbackArena != null && this.callbackArena.scope().isAlive()) {
                this.callbackArena.close();
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            if (throwable != null) {
                sendPlayerMessage(
                        Text.literal("Cracking task failed: " + throwable.getMessage()).formatted(Formatting.RED));
                mc.execute(() -> updateStatusOverlay(Text.literal("Cracking Failed: ").formatted(Formatting.RED)
                        .append(Text.literal(throwable.getMessage()).formatted(Formatting.WHITE))));
            } else {
                if (totalUnitsToProcess.get() > 0) {
                    unitsProcessedSoFar.set(totalUnitsToProcess.get());
                }
                if (mc.player != null) {
                    mc.execute(
                            () -> updateStatusOverlay(Text.literal("Cracking Finished!").formatted(Formatting.GREEN)));
                    String summaryMessage;
                    long seedsFromCallbackTotalCount = seedsFoundByCallbackCount.get();
                    long seedsFromNativeFinalListCount = (seedsFromNativeFunction != null)
                            ? seedsFromNativeFunction.size()
                            : 0;

                    if (seedsFromCallbackTotalCount > 0) {
                        summaryMessage = "Cracking finished! " + seedsFromCallbackTotalCount
                                + " seed(s) were discovered progressively (see list below).";
                        if (seedsFromNativeFinalListCount > 0) {
                            if (seedsFromNativeFinalListCount != seedsFromCallbackTotalCount) {
                                summaryMessage += " The native function also returned a final list of "
                                        + seedsFromNativeFinalListCount + " seed(s).";
                            } else {
                                summaryMessage += " The native function's final list confirmed these seeds.";
                            }
                        }
                    } else if (seedsFromNativeFinalListCount > 0) {
                        summaryMessage = "Cracking finished! Native function returned " + seedsFromNativeFinalListCount
                                + " final seed(s). No seeds were reported progressively via callback.";
                    } else {
                        summaryMessage = "Cracking finished. No seeds found.";
                    }
                    sendPlayerMessage(Text.literal(summaryMessage));

                    List<Long> callbackSeedsToPrint = new ArrayList<>(progressivelyFoundSeeds);
                    if (!callbackSeedsToPrint.isEmpty()) {
                        sendPlayerMessage(Text.literal("-------------- All seeds --------------").formatted(Formatting.YELLOW));
                        for (int i = 0; i < callbackSeedsToPrint.size(); i++) {
                            String label = callbackSeedsToPrint.size() == 1 ? "Seed: " : ("Seed " + (i + 1) + ": ");
                            displaySeedToPlayer(label, callbackSeedsToPrint.get(i));
                        }
                        sendPlayerMessage(Text.literal("-------------------------------------").formatted(Formatting.YELLOW));
                    }
                }
            }
        }, MinecraftClient.getInstance()::execute);

        return true;
    }

    public CrackerProgressInfo getProgress() {
        long total = totalUnitsToProcess.get();
        long current = unitsProcessedSoFar.get();
        long found = seedsFoundByCallbackCount.get();
        boolean isActive = isCrackingActive();

        if (total > 0 && current > total) {
            current = total;
        }
        if (!isActive && total > 0 && current < total && crackingTaskFuture != null && crackingTaskFuture.isDone()
                && !crackingTaskFuture.isCompletedExceptionally()) {
            current = total;
        }
        return new CrackerProgressInfo(total, current, found, isActive);
    }

    public void requestStop() {
        bedrock_cracker_h.request_stop_crack_ffi();
        MinecraftClient.getInstance()
                .execute(() -> updateStatusOverlay(Text.literal("Cracking Stopped").formatted(Formatting.GRAY)));
        if (crackingTaskFuture == null || crackingTaskFuture.isDone()) {
            isCrackingInternal.set(false);
            bedrock_cracker_h.reset_cracker_state_ffi();
        }
    }

    public boolean isCrackingActive() {
        return isCrackingInternal.get() && crackingTaskFuture != null && !crackingTaskFuture.isDone();
    }

    public void shutdown() {
        if (isCrackingActive()) {
            requestStop();
        }
        crackerExecutor.shutdown();
        try {
            if (!crackerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                crackerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            crackerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (this.callbackArena != null && this.callbackArena.scope().isAlive()) {
            this.callbackArena.close();
            this.callbackArena = null;
        }
        bedrock_cracker_h.reset_cracker_state_ffi();
    }
}