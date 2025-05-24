package com.tmquan2508.IngameNetherBedrockCracker.cracker.enums;

import com.github.netherbedrockcracker.bedrock_cracker_h;

public enum CrackerOutputMode {
    WORLD_SEED(bedrock_cracker_h.OutputMode_WorldSeed()),
    STRUCTURE_SEED(bedrock_cracker_h.OutputMode_StructureSeed());
    // Hiện tại bạn chỉ cần WORLD_SEED, nhưng có thể để sẵn STRUCTURE_SEED

    private final int nativeValue;

    CrackerOutputMode(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    public int getNativeValue() {
        return nativeValue;
    }
}