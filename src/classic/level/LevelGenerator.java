package classic.level;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class LevelGenerator {
    protected short width = 256;
    protected short height = 64;
    protected short depth = 256;

    public LevelGenerator() {
        // Default constructor
    }

    public LevelGenerator(short width, short height, short depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public Level generateFlatWorld() {
        Level level = new Level(width, height, depth);

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    level.setBlock(x, y, z, getBlockType(x, y, z));
                }
            }
        }

        return level;
    }

    protected byte getBlockType(int x, int y, int z) {
        if (y == 0) {
            return 7; // Bedrock
        } else if (y < height / 2) {
            return 3; // Dirt
        } else if (y == height / 2) {
            return 2; // Grass
        } else {
            return 0; // Air
        }
    }

    public byte[] compressLevelData(Level level) throws IOException {
        byte[] rawData = level.getBlockData();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(rawData.length);
        dos.write(rawData);
        dos.close();

        byte[] uncompressed = baos.toByteArray();
        ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(compressedBaos);
        gzipOut.write(uncompressed);
        gzipOut.close();

        return compressedBaos.toByteArray();
    }

    public short getWidth() {
        return width;
    }

    public short getHeight() {
        return height;
    }

    public short getDepth() {
        return depth;
    }

    public void setWidth(short width) {
        this.width = width;
    }

    public void setHeight(short height) {
        this.height = height;
    }

    public void setDepth(short depth) {
        this.depth = depth;
    }
}