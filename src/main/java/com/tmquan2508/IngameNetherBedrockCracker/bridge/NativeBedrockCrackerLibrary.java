package com.tmquan2508.IngameNetherBedrockCracker.bridge;

import com.github.netherbedrockcracker.bedrock_cracker_h;
import com.github.netherbedrockcracker.Block;
import com.github.netherbedrockcracker.VecI64;
import com.tmquan2508.IngameNetherBedrockCracker.commands.NetherCrackerCommand;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

import static com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker.LOGGER;
import static com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker.MOD_ID;

public class NativeBedrockCrackerLibrary {

    public static long estimateResultAmount(List<NetherCrackerCommand.FoundBedrock> blockInfoList) {
        if (blockInfoList.isEmpty()) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment blocksNative = Block.allocateArray(blockInfoList.size(), arena);
            populateNativeBlockArray(blocksNative, blockInfoList);
            return bedrock_cracker_h.estimate_result_amount(blocksNative, blockInfoList.size());
        } catch (Throwable e) {
            LOGGER.error("[{}] Error in estimateResultAmount: {}", MOD_ID, e.getMessage(), e);
            return 0;
        }
    }

    public static List<Long> crack(List<NetherCrackerCommand.FoundBedrock> blockInfoList, int threads,
                                   int generationMode,
                                   int outputMode,
                                   NativeMemoryHolder memoryHolder) {
        List<Long> foundSeeds = new ArrayList<>();
        if (blockInfoList.isEmpty()) {
            return foundSeeds;
        }

        try (Arena crackArena = Arena.ofConfined()) {
            MemorySegment blocksNative = Block.allocateArray(blockInfoList.size(), crackArena);
            populateNativeBlockArray(blocksNative, blockInfoList);

            LOGGER.info("[{}] Calling native crack function with {} blocks, {} threads, genMode: {}, outMode: {}",
                    MOD_ID, blockInfoList.size(), threads, generationMode, outputMode);

            MemorySegment vecI64Native = bedrock_cracker_h.crack(crackArena, blocksNative, blockInfoList.size(), threads, generationMode, outputMode);

            if (vecI64Native == null || vecI64Native.address() == 0) {
                LOGGER.error("[{}] Native crack function returned a null or zero address segment.", MOD_ID);
                return foundSeeds;
            }
            
            MemorySegment persistentVecI64 = Arena.global().allocate(VecI64.$LAYOUT());
            persistentVecI64.copyFrom(vecI64Native);
            memoryHolder.setNativeResults(persistentVecI64);

            MemorySegment seedsPtr = VecI64.ptr$get(persistentVecI64);
            long seedsLen = VecI64.len$get(persistentVecI64);

            LOGGER.info("[{}] Native crack function returned {} seeds.", MOD_ID, seedsLen);

            if (seedsPtr == null || seedsPtr.address() == 0) {
                if (seedsLen > 0) {
                     LOGGER.warn("[{}] Native crack function returned a null pointer for seeds, but length is {}.", MOD_ID, seedsLen);
                }
                return foundSeeds;
            }

            for (long i = 0; i < seedsLen; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    LOGGER.info("[{}] Crack task interrupted while processing results.", MOD_ID);
                    break;
                }
                foundSeeds.add(seedsPtr.get(ValueLayout.JAVA_LONG, i * ValueLayout.JAVA_LONG.byteSize()));
            }
            return foundSeeds;

        } catch (Throwable e) {
            LOGGER.error("[{}] Error in native crack: {}", MOD_ID, e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return foundSeeds;
        }
    }

    public static void freeSeedVector(MemorySegment vecI64Segment) {
        if (vecI64Segment != null && vecI64Segment.address() != 0) {
            try {
                LOGGER.info("[{}] Freeing native seed vector.", MOD_ID);
                bedrock_cracker_h.free_seed_vector(vecI64Segment);
            } catch (Throwable e) {
                LOGGER.error("[{}] Error freeing native seed vector: {}", MOD_ID, e.getMessage(), e);
            }
        }
    }

    private static void populateNativeBlockArray(MemorySegment blocksNativeArray, List<NetherCrackerCommand.FoundBedrock> blockInfoList) {
        for (int i = 0; i < blockInfoList.size(); i++) {
            NetherCrackerCommand.FoundBedrock info = blockInfoList.get(i);
            MemorySegment blockSegment = blocksNativeArray.asSlice((long)i * Block.sizeof(), Block.sizeof());
            Block.x$set(blockSegment, info.x());
            Block.y$set(blockSegment, info.y());
            Block.z$set(blockSegment, info.z());
            Block.block_type$set(blockSegment, info.type());
        }
    }
    
    public static class NativeMemoryHolder {
        private MemorySegment nativeResults;

        public MemorySegment getNativeResults() {
            return nativeResults;
        }

        public void setNativeResults(MemorySegment nativeResults) {
            this.nativeResults = nativeResults;
        }
    }
}