package com.tmquan2508.IngameNetherBedrockCracker.cracker;

import com.github.netherbedrockcracker.*;
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

    private static BedrockCrackerService instanceForCallback;

    public BedrockCrackerService() {
        instanceForCallback = this;
    }

    private static void javaInitCallback(long totalUnits, long initialSeedsFound) {
        BedrockCrackerService service = instanceForCallback;
        if (service != null) {
            service.totalUnitsToProcess.set(totalUnits);
            service.unitsProcessedSoFar.set(0);
            service.seedsFoundByCallbackCount.set(initialSeedsFound);
        }
    }

    private static void javaProgressCallback(long processedDelta) {
        BedrockCrackerService service = instanceForCallback;
        if (service != null) {
            service.unitsProcessedSoFar.addAndGet(processedDelta);
        }
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
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Copy to clipboard")))
                        ))
                .append(Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
        sendPlayerMessage(message);
    }

    private static void javaSeedFoundCallback(long seed) {
        BedrockCrackerService service = instanceForCallback;
        if (service != null) {
            service.progressivelyFoundSeeds.add(seed);
            service.seedsFoundByCallbackCount.incrementAndGet();
            MinecraftClient.getInstance().execute(() -> service.displaySeedToPlayer("Seed discovered: ", seed));
        }
    }
    
    private MemorySegment createUpcall(MethodHandles.Lookup lookup, Class<?> targetClass, String methodName, MethodType methodType, FunctionDescriptor nativeDescriptor) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle mh = lookup.findStatic(targetClass, methodName, methodType);
        return Linker.nativeLinker().upcallStub(mh, nativeDescriptor, this.callbackArena);
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
                MethodHandles.Lookup lookup = MethodHandles.lookup();

                MemorySegment seedCallbackNativePtr = createUpcall(lookup, BedrockCrackerService.class, "javaSeedFoundCallback",
                        MethodType.methodType(void.class, long.class),
                        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));

                MemorySegment initCallbackNativePtr = createUpcall(lookup, BedrockCrackerService.class, "javaInitCallback",
                        MethodType.methodType(void.class, long.class, long.class),
                        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

                MemorySegment progressCallbackNativePtr = createUpcall(lookup, BedrockCrackerService.class, "javaProgressCallback",
                        MethodType.methodType(void.class, long.class),
                        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));

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
                MinecraftClient.getInstance().execute(() -> 
                    sendPlayerMessage(Text.literal("Cracking Error: " + t.getMessage()).formatted(Formatting.RED))
                );
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
                 sendPlayerMessage(Text.literal("Cracking task failed: " + throwable.getMessage()).formatted(Formatting.RED));
            } else {
                if (totalUnitsToProcess.get() > 0) {
                    unitsProcessedSoFar.set(totalUnitsToProcess.get());
                }
                if (mc.player != null) {
                    String summaryMessage;
                    long seedsFromCallbackTotalCount = seedsFoundByCallbackCount.get();
                    long seedsFromNativeFinalListCount = (seedsFromNativeFunction != null) ? seedsFromNativeFunction.size() : 0;
                    
                    if (seedsFromCallbackTotalCount > 0) {
                        summaryMessage = "Cracking finished! " + seedsFromCallbackTotalCount + " seed(s) were discovered progressively (see list below).";
                        if (seedsFromNativeFinalListCount > 0) {
                            if (seedsFromNativeFinalListCount != seedsFromCallbackTotalCount) {
                                summaryMessage += " The native function also returned a final list of " + seedsFromNativeFinalListCount + " seed(s).";
                            } else {
                                summaryMessage += " The native function's final list confirmed these seeds.";
                            }
                        }
                    } else if (seedsFromNativeFinalListCount > 0) {
                        summaryMessage = "Cracking finished! Native function returned " + seedsFromNativeFinalListCount + " final seed(s). No seeds were reported progressively via callback.";
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
        if (!isActive && total > 0 && current < total && crackingTaskFuture != null && crackingTaskFuture.isDone() && !crackingTaskFuture.isCompletedExceptionally()) {
            current = total;
        }
        return new CrackerProgressInfo(total, current, found, isActive);
    }

    public void requestStop() {
        bedrock_cracker_h.request_stop_crack_ffi();
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
        instanceForCallback = null;
    }
}