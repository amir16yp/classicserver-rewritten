package classic.level;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class LevelGenerator {
    public static final short WIDTH = 256;
    public static final short HEIGHT = 64;
    public static final short DEPTH = 256;

    public static Level generateFlatWorld() {
        Level level = new Level(WIDTH, HEIGHT, DEPTH);

        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < DEPTH; z++) {
                for (int x = 0; x < WIDTH; x++) {
                    if (y == 0) {
                        level.setBlock(x, y, z, (byte) 7); // Bedrock
                    } else if (y < 32) {
                        level.setBlock(x, y, z, (byte) 3); // Dirt
                    } else if (y == 32) {
                        level.setBlock(x, y, z, (byte) 2); // Grass
                    } else {
                        level.setBlock(x, y, z, (byte) 0); // Air
                    }
                }
            }
        }

        return level;
    }

    public static byte[] compressLevelData(Level level) throws IOException {
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

    public static short getWidth()
    {
        return WIDTH;
    }

    public static short getHeight() {
        return HEIGHT;
    }

    public static short getDepth() {
        return DEPTH;
    }
}