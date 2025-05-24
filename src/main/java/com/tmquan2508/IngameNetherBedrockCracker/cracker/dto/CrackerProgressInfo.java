package com.tmquan2508.IngameNetherBedrockCracker.cracker.dto;

public class CrackerProgressInfo {
    private final long totalUnitsToProcess;
    private final long unitsProcessedSoFar;
    private final long seedsFoundCount;
    private final boolean isCrackingActive;

    public CrackerProgressInfo(long totalUnitsToProcess, long unitsProcessedSoFar, long seedsFoundCount, boolean isCrackingActive) {
        this.totalUnitsToProcess = totalUnitsToProcess;
        this.unitsProcessedSoFar = unitsProcessedSoFar;
        this.seedsFoundCount = seedsFoundCount;
        this.isCrackingActive = isCrackingActive;
    }

    public long getTotalUnitsToProcess() { return totalUnitsToProcess; }
    public long getUnitsProcessedSoFar() { return unitsProcessedSoFar; }
    public long getSeedsFoundCount() { return seedsFoundCount; }
    public boolean isCrackingActive() { return isCrackingActive; }

    public double getPercentage() {
        if (!isCrackingActive || totalUnitsToProcess == 0) return isCrackingActive ? 0.0 : 100.0; // Nếu không active và không có lỗi, coi như 100%
        return Math.min(100.0, (double) unitsProcessedSoFar * 100.0 / totalUnitsToProcess);
    }

    @Override
    public String toString() {
        return String.format("Progress: %.2f%% (%d/%d units), %d seeds found. Active: %s",
                getPercentage(), unitsProcessedSoFar, totalUnitsToProcess, seedsFoundCount, isCrackingActive);
    }
}