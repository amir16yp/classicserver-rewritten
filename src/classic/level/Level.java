package classic.level;

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

    public byte getBlock(short x, short y, short z) {
        // Implement this method to return the block type at the given coordinates
        // Make sure to handle out-of-bounds coordinates
        if (x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth) {
            return blocks[x][y][z];
        }
        return 0; // Return air for out-of-bounds coordinates
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
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
}