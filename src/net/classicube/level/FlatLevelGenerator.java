package net.classicube.level;

import net.classicube.api.enums.BlockType;

public class FlatLevelGenerator extends LevelGenerator {

    public FlatLevelGenerator() {
        super();
    }

    public FlatLevelGenerator(short width, short height, short depth) {
        super(width, height, depth, 0);
    }

    @Override
    public Level generateLevel() {
        Level level = this.generateBasicLevel();
        // Create just two layers
        level.fillCuboid(0, 0, 0, width - 1, 0, depth - 1, BlockType.BEDROCK);  // Bottom layer: Bedrock
        level.fillCuboid(0, 1, 0, width - 1, 1, depth - 1, BlockType.GRASS_BLOCK);    // Second layer: Grass
        int[] center = level.getCenter();
        level.createHollowSphere(center[0], center[1], center[2], 16, BlockType.GLASS);
        //level.createHollowSphere(center[0], center[1], center[2], 15, BlockType.STATIONARY_LAVA);
        //level.createHollowSphere(center[0], center[1], center[2], 14, BlockType.GLASS);
        return level;
    }

}