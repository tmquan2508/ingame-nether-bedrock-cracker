package com.tmquan2508.IngameNetherBedrockCracker.cracker.dto;

public class BlockInput {
    private final int x;
    private final int y;
    private final int z;
    private final int blockType;

    public BlockInput(int x, int y, int z, int blockType) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockType = blockType;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getBlockType() { return blockType; }

    @Override
    public String toString() {
        return "BlockInput{" +
               "x=" + x +
               ", y=" + y +
               ", z=" + z +
               ", blockType=" + blockType +
               '}';
    }
}