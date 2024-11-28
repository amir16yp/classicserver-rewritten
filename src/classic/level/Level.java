package classic.level;

import classic.api.BlockType;

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

    public void setBlock(int x, int y, int z, byte blockType) {
        if (x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth) {
            blocks[x][y][z] = blockType;
        }
    }

    public void setBlock(int x, int y, int z, BlockType blockType)
    {
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
}