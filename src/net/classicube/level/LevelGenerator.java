package net.classicube.level;

import java.util.Random;

public abstract class LevelGenerator {
    // Core fields
    protected short width = 256;
    protected short height = 64;
    protected short depth = 256;
    protected Random random;

    // Constructors
    protected LevelGenerator() {
        this((short) 256, (short) 64, (short) 256, new Random().nextLong());
    }

    protected LevelGenerator(short width, short height, short depth, long seed) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.random = new Random(seed);
    }

    // Abstract methods
    public abstract Level generateLevel();

    // Core generation
    protected Level generateBasicLevel() {
        Level level = new Level(width, height, depth);

        // First fill everything with air
        level.clearArea(0, 0, 0, width - 1, height - 1, depth - 1);

        return level;
    }

    // Getters
    public short getWidth() {
        return width;
    }

    public short getHeight() {
        return height;
    }

    public short getDepth() {
        return depth;
    }
}