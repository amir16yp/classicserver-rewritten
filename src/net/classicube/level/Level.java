package net.classicube.level;

import net.classicube.ClientHandler;
import net.classicube.api.API;
import net.classicube.api.enums.BlockType;
import net.classicube.packets.SetBlockServerPacket;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Level {
    private final byte[][][] blocks;
    private final int width;
    private final int height;
    private final int depth;
    private String name;

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

            int width = dis.readShort() & 0xFFFF;
            int height = dis.readShort() & 0xFFFF;
            int depth = dis.readShort() & 0xFFFF;

            Level level = new Level(width, height, depth);
            byte[] data = new byte[width * height * depth];
            dis.readFully(data);
            level.setBlockData(data);
            return level;
        }
    }

    public String getName() {
        return name;
    }

    // ===== File Operations =====

    public void setName(String name) {
        this.name = name;
    }

    public void saveToFile(String filename) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new GZIPOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(filename))))) {
            dos.writeShort(width);
            dos.writeShort(height);
            dos.writeShort(depth);
            dos.write(getBlockData());
        }
    }

    // ===== Block Operations =====

    public void setBlock(int x, int y, int z, byte blockType) {
        if (isInBounds(x, y, z)) {
            blocks[x][y][z] = blockType;
        }
    }

    public void setBlock(int x, int y, int z, BlockType blockType) {
        setBlock(x, y, z, blockType.getId());
    }

    public byte getBlock(short x, short y, short z) {
        if (isInBounds(x, y, z)) {
            return blocks[x][y][z];
        }
        return 0;
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

    // ===== Broadcasting =====

    public void broadcastBlockChange(int x, int y, int z, BlockType blockType) {
        if (!API.initialized) {
            return;
        }
        if (!API.getInstance().getServer().getLevelManager().levelExists(this.getName())) return;
        if (!isInBounds(x, y, z)) return;
        SetBlockServerPacket packet = new SetBlockServerPacket();
        packet.setX((short) x);
        packet.setY((short) y);
        packet.setZ((short) z);
        packet.setBlockType(blockType.getId());

        ClientHandler.broadcastPacketToLevel(packet, getName());
    }

    // ===== Structure Generation =====

    public void fillCuboid(int x1, int y1, int z1, int x2, int y2, int z2, BlockType block) {
        int minX = Math.max(0, Math.min(x1, x2));
        int maxX = Math.min(width - 1, Math.max(x1, x2));
        int minY = Math.max(0, Math.min(y1, y2));
        int maxY = Math.min(height - 1, Math.max(y1, y2));
        int minZ = Math.max(0, Math.min(z1, z2));
        int maxZ = Math.min(depth - 1, Math.max(z1, z2));

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    setBlock(x, y, z, block);
                    broadcastBlockChange(x, y, z, block);
                }
            }
        }
    }

    public void createWalls(int x1, int y1, int z1, int x2, int y2, int z2, BlockType block) {
        int minX = Math.max(0, Math.min(x1, x2));
        int maxX = Math.min(width - 1, Math.max(x1, x2));
        int minY = Math.max(0, Math.min(y1, y2));
        int maxY = Math.min(height - 1, Math.max(y1, y2));
        int minZ = Math.max(0, Math.min(z1, z2));
        int maxZ = Math.min(depth - 1, Math.max(z1, z2));

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                setBlock(x, y, minZ, block);
                broadcastBlockChange(x, y, minZ, block);
                setBlock(x, y, maxZ, block);
                broadcastBlockChange(x, y, maxZ, block);
            }
        }

        for (int z = minZ + 1; z < maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                setBlock(minX, y, z, block);
                broadcastBlockChange(minX, y, z, block);
                setBlock(maxX, y, z, block);
                broadcastBlockChange(maxX, y, z, block);
            }
        }
    }

    public void fillCircle(int centerX, int centerZ, int y, int radius, BlockType block) {
        int rSquared = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= rSquared) {
                    int worldX = centerX + x;
                    int worldZ = centerZ + z;
                    if (isInBounds(worldX, y, worldZ)) {
                        setBlock(worldX, y, worldZ, block);
                        broadcastBlockChange(worldX, y, worldZ, block);
                    }
                }
            }
        }
    }

    public void fillSphere(int centerX, int centerY, int centerZ, int radius, BlockType block) {
        int rSquared = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= rSquared) {
                        int worldX = centerX + x;
                        int worldY = centerY + y;
                        int worldZ = centerZ + z;
                        if (isInBounds(worldX, worldY, worldZ)) {
                            setBlock(worldX, worldY, worldZ, block);
                            broadcastBlockChange(worldX, worldY, worldZ, block);
                        }
                    }
                }
            }
        }
    }

    public void createHollowSphere(int centerX, int centerY, int centerZ, int radius, BlockType block) {
        int rSquared = radius * radius;
        int rSquaredMin = (radius - 1) * (radius - 1);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int distSquared = x * x + y * y + z * z;
                    if (distSquared <= rSquared && distSquared > rSquaredMin) {
                        int worldX = centerX + x;
                        int worldY = centerY + y;
                        int worldZ = centerZ + z;
                        if (isInBounds(worldX, worldY, worldZ)) {
                            setBlock(worldX, worldY, worldZ, block);
                            broadcastBlockChange(worldX, worldY, worldZ, block);
                        }
                    }
                }
            }
        }
    }

    public void clearArea(int x1, int y1, int z1, int x2, int y2, int z2) {
        fillCuboid(x1, y1, z1, x2, y2, z2, BlockType.AIR);
    }

    // ===== Utility Methods =====

    public boolean isInBounds(int x, int y, int z) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth;
    }

    public int[] getCenter() {
        return new int[]{width / 2, height / 2, depth / 2};
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

    @Override
    public String toString() {
        int[] blockCounts = new int[256];
        int totalBlocks = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    byte blockType = blocks[x][y][z];
                    blockCounts[blockType & 0xFF]++;
                    if (blockType != 0) {
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

        stats.append("Most common blocks:\n");
        for (int i = 0; i < 5; i++) {
            int maxCount = 0;
            int maxType = 0;
            for (int type = 1; type < blockCounts.length; type++) {
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
                blockCounts[maxType] = 0;
            }
        }

        return stats.toString();
    }
}