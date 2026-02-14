package com.tmquan2508.IngameNetherBedrockCracker.helpers;

import com.tmquan2508.IngameNetherBedrockCracker.cracker.enums.CrackerMode;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class BedrockValidator {

    private static final long MASK48 = 0xFFFFFFFFFFFFL;
    private static final long LCG_MULTIPLIER = 25214903917L;
    private static final long LCG_ADDEND = 11L;
    private static final long ROOF_HASH = 343340730L;
    private static final long FLOOR_HASH = 2042456806L;

    public static class JavaRandom {
        private long seed;

        public JavaRandom(long seed) {
            setSeed(seed);
        }

        public void setSeed(long seed) {
            this.seed = (seed ^ LCG_MULTIPLIER) & MASK48;
        }

        public int next(int bits) {
            seed = (seed * LCG_MULTIPLIER + LCG_ADDEND) & MASK48;
            return (int) (seed >>> (48 - bits));
        }

        public long nextLong() {
            return ((long) next(32) << 32) + next(32);
        }
    }

    private static long rotateLeft(long n, int d) {
        return (n << d) | (n >>> (64 - d));
    }

    private static long mixStafford13(long seed) {
        seed = (seed ^ (seed >>> 30)) * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ (seed >>> 27)) * 0x94D049BB133111EBL;
        return seed ^ (seed >>> 31);
    }

    private static class Xoroshiro128Impl {
        private long seedLo, seedHi;

        public Xoroshiro128Impl(long lo, long hi) {
            this.seedLo = lo;
            this.seedHi = hi;
        }

        public long next() {
            long l = seedLo;
            long m = seedHi;
            long n = rotateLeft(l + m, 17) + l;
            m ^= l;
            seedLo = rotateLeft(l, 49) ^ m ^ (m << 21);
            seedHi = rotateLeft(m, 28);
            return n;
        }
    }

    private static long unpackLongJava(byte[] b, int offset) {
        return ((long) b[offset] << 56) | ((long) (b[offset + 1] & 255) << 48) | ((long) (b[offset + 2] & 255) << 40) |
                ((long) (b[offset + 3] & 255) << 32) | ((long) (b[offset + 4] & 255) << 24) |
                ((long) (b[offset + 5] & 255) << 16) | ((long) (b[offset + 6] & 255) << 8)
                | (long) (b[offset + 7] & 255);
    }

    private static long javaHashCode(int x, int y, int z) {
        long posHash = (long) x * 3129871L ^ (long) z * 116129781L ^ (long) y;
        posHash = posHash * posHash * 42317861L + posHash * 11L;
        return posHash >> 16;
    }

    public static boolean isBedrock(int x, int y, int z, long worldSeed, World world, CrackerMode mode) {
        String dimType = world.getRegistryKey().getValue().getPath();

        if (dimType.endsWith("overworld")) {
            long sLo = worldSeed ^ 0x6A09E667F3BCC909L;
            long sHi = sLo + 0x9E3779B97F4A7C15L;
            Xoroshiro128Impl rootRng = new Xoroshiro128Impl(mixStafford13(sLo), mixStafford13(sHi));
            long rootLo = rootRng.next();
            long rootHi = rootRng.next();

            byte[] digest;
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                digest = md.digest("minecraft:bedrock_floor".getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }

            long idLo = unpackLongJava(digest, 0);
            long idHi = unpackLongJava(digest, 8);
            Xoroshiro128Impl idRng = new Xoroshiro128Impl(idLo ^ rootLo, idHi ^ rootHi);
            long finalLo = idRng.next();
            long finalHi = idRng.next();

            Xoroshiro128Impl finalRng = new Xoroshiro128Impl(javaHashCode(x, y, z) ^ finalLo, finalHi);
            float roll = (float) (finalRng.next() >>> 40) * 5.9604645E-8f;

            int minY = -64, maxY = -59;
            if (y <= minY)
                return true;
            if (y >= maxY)
                return false;
            float d = (float) (maxY - y) / (float) (maxY - minY);
            return roll < d;
        } else if (dimType.endsWith("the_nether")) {
            boolean isRoof = (y > 64);
            int hashY = y;
            if (mode == CrackerMode.PAPER1_18)
                hashY = isRoof ? 122 : 0;

            JavaRandom worldRng = new JavaRandom(worldSeed);
            long rand = worldRng.nextLong();
            long combinedSeed = rand ^ (isRoof ? ROOF_HASH : FLOOR_HASH);
            JavaRandom bedrockRng = new JavaRandom(combinedSeed);
            long finalBedrockSeed = bedrockRng.nextLong() & MASK48;

            long posHash = javaHashCode(x, hashY, z);
            long internalSeed = (finalBedrockSeed ^ posHash ^ LCG_MULTIPLIER) & MASK48;
            long lcgResult = (internalSeed * LCG_MULTIPLIER + LCG_ADDEND) & MASK48;

            double boundVal;
            if (isRoof) {
                int layer = y - 122;
                boundVal = (5.0 - (double) layer) / 5.0;
                return lcgResult >= (long) (boundVal * (double) MASK48);
            } else {
                boundVal = (5.0 - (double) y) / 5.0;
                return lcgResult < (long) (boundVal * (double) MASK48);
            }
        }
        return false;
    }
}
