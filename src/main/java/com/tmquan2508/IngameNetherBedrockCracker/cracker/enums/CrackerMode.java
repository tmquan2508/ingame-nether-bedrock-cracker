package com.tmquan2508.IngameNetherBedrockCracker.cracker.enums;

import com.github.netherbedrockcracker.bedrock_cracker_h; // Quan trọng: import JExtract generated class

public enum CrackerMode {
    NORMAL(bedrock_cracker_h.BedrockGeneration_Normal()),
    PAPER1_18(bedrock_cracker_h.BedrockGeneration_Paper1_18()); // Đổi tên enum khớp với ALLOWED_GENERATION_TYPES

    private final int nativeValue;

    CrackerMode(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    public int getNativeValue() {
        return nativeValue;
    }

    public static CrackerMode fromString(String s) {
        for (CrackerMode mode : values()) {
            if (mode.name().equalsIgnoreCase(s)) {
                return mode;
            }
        }
        return null;
    }
}