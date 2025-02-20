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
        return level;
    }

}