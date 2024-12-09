package net.classicube.level;

import net.classicube.api.enums.BlockType;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Level {
    private final byte[][][] blocks;
    private final int width;
    private final int height;
    private final int depth;

    public Level(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.blocks = new byte[width][height][depth];
    }

    public static Level loadFromFile(String filename) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new GZIPInputStream(
                        new BufferedInputStream(
                                new FileInputStream(filename))))) {

            // Read dimensions
            int width = dis.readShort() & 0xFFFF;  // Convert to unsigned
            int height = dis.readShort() & 0xFFFF;
            int depth = dis.readShort() & 0xFFFF;

            // Create new level
            Level level = new Level(width, height, depth);

            // Read block data
            byte[] data = new byte[width * height * depth];
            dis.readFully(data);

            level.setBlockData(data);
            return level;
        }
    }

    public void setBlock(int x, int y, int z, byte blockType) {
        if (x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth) {
            blocks[x][y][z] = blockType;
        }
    }

    public void setBlock(int x, int y, int z, BlockType blockType) {
        setBlock(x, y, z, blockType.getId());
    }

    public byte getBlock(short x, short y, short z) {
        if (x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth) {
            return blocks[x][y][z];
        }
        return 0;
    }

    public short getWidth() {
        return (short) width;
    }

    public short getHeight() {
        return (short) height;
    }

    public short getDepth() {
        return (short) depth;
    }

    public byte[] getBlockData() {
        byte[] flattenedBlocks = new byte[width * height * depth];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    flattenedBlocks[index++] = blocks[x][y][z];
                }
            }
        }
        return flattenedBlocks;
    }

    public void setBlockData(byte[] data) {
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    blocks[x][y][z] = data[index++];
                }
            }
        }
    }

    public void saveToFile(String filename) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new GZIPOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(filename))))) {

            // Write dimensions
            dos.writeShort(width);
            dos.writeShort(height);
            dos.writeShort(depth);

            // Write block data
            byte[] data = getBlockData();
            dos.write(data);
        }
    }

    @Override
    public String toString() {
        // Count different block types
        int[] blockCounts = new int[256];  // 256 possible block types
        int totalBlocks = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    byte blockType = blocks[x][y][z];
                    blockCounts[blockType & 0xFF]++;
                    if (blockType != 0) {  // Don't count air blocks
                        totalBlocks++;
                    }
                }
            }
        }

        StringBuilder stats = new StringBuilder();
        stats.append("Level Statistics:\n");
        stats.append(String.format("Dimensions: %dx%dx%d\n", width, height, depth));
        stats.append(String.format("Total volume: %d blocks\n", width * height * depth));
        stats.append(String.format("Blocks placed: %d (%.1f%%)\n",
                totalBlocks,
                (totalBlocks * 100.0f) / (width * height * depth)));

        // Show top 5 most common blocks (excluding air)
        stats.append("Most common blocks:\n");
        for (int i = 0; i < 5; i++) {
            int maxCount = 0;
            int maxType = 0;
            for (int type = 1; type < blockCounts.length; type++) {  // Start at 1 to skip air
                if (blockCounts[type] > maxCount) {
                    maxCount = blockCounts[type];
                    maxType = type;
                }
            }
            if (maxCount > 0) {
                BlockType blockType = BlockType.getById((byte) maxType);
                String blockName = blockType != null ? blockType.name() : "UNKNOWN";
                stats.append(String.format("  %s: %d blocks (%.1f%%)\n",
                        blockName,
                        maxCount,
                        (maxCount * 100.0f) / (width * height * depth)));
                blockCounts[maxType] = 0;  // Reset count so we find next most common
            }
        }

        return stats.toString();
    }
}