package net.classicube.level;

import net.classicube.api.enums.BlockType;

public class BackroomsGenerator extends LevelGenerator {
    private static final int MIN_FLOOR_HEIGHT = 4;    // Minimum height between floors
    private static final int MAX_FLOOR_HEIGHT = 7;    // Maximum height between floors
    private static final int NUM_FLOORS = 4;          // Number of vertical floors
    private static final int MIN_ROOM_SIZE = 6;       // Minimum room dimension
    private static final int MAX_ROOM_SIZE = 15;      // Maximum room dimension
    private static final int MIN_DOOR_HEIGHT = 3;     // Minimum door height
    private static final int VOID_RADIUS = 16;        // Radius of central void

    private int currentFloorHeight;

    public BackroomsGenerator() {
        super();
    }

    public BackroomsGenerator(short width, short height, short depth) {
        super(width, height, depth, 0);
    }

    @Override
    public Level generateLevel() {
        Level level = generateBasicLevel();
        int[] center = level.getCenter();
        int currentY = 0;

        // Create each floor
        for (int floor = 0; floor < NUM_FLOORS; floor++) {
            // Randomize this floor's height
            currentFloorHeight = MIN_FLOOR_HEIGHT + random.nextInt(MAX_FLOOR_HEIGHT - MIN_FLOOR_HEIGHT + 1);

            // Create floor base
            level.fillCuboid(0, currentY, 0, width-1, currentY, depth-1,
                    floor == 0 ? BlockType.BEDROCK : BlockType.YELLOW_CLOTH);

            // Create ceiling
            level.fillCuboid(0, currentY + currentFloorHeight - 1, 0,
                    width-1, currentY + currentFloorHeight - 1, depth-1,
                    BlockType.WHITE_CLOTH);

            // Generate random rooms and corridors
            generateMaze(level, currentY, center[0], center[2]);

            // Add lights to ceiling
            addLights(level, currentY + currentFloorHeight - 1);

            // Move to next floor
            currentY += currentFloorHeight;
        }

        // Create central void
        createVoid(level, center[0], center[2]);

        // Add stairs or other vertical connections
        createVerticalConnections(level, center[0], center[2]);

        return level;
    }

    private void generateMaze(Level level, int baseY, int centerX, int centerZ) {
        boolean[][] processed = new boolean[width][depth];

        // Start with some random rooms
        for (int attempt = 0; attempt < 50; attempt++) {
            int roomWidth = MIN_ROOM_SIZE + random.nextInt(MAX_ROOM_SIZE - MIN_ROOM_SIZE + 1);
            int roomDepth = MIN_ROOM_SIZE + random.nextInt(MAX_ROOM_SIZE - MIN_ROOM_SIZE + 1);
            int x = random.nextInt(width - roomWidth);
            int z = random.nextInt(depth - roomDepth);

            // Check if room would intersect with void
            if (isInVoid(x, z, centerX, centerZ) ||
                    isInVoid(x + roomWidth, z + roomDepth, centerX, centerZ)) {
                continue;
            }

            // Create room
            createRoom(level, x, z, x + roomWidth, z + roomDepth, baseY);

            // Mark area as processed
            for (int rx = x; rx < x + roomWidth; rx++) {
                for (int rz = z; rz < z + roomDepth; rz++) {
                    if (rx < width && rz < depth) {
                        processed[rx][rz] = true;
                    }
                }
            }
        }

        // Connect rooms with corridors
        connectRooms(level, processed, baseY);
    }

    private void createRoom(Level level, int x1, int z1, int x2, int z2, int baseY) {
        // Create walls from floor to ceiling
        for (int y = baseY + 1; y < baseY + currentFloorHeight - 1; y++) {
            // North and South walls
            for (int x = x1; x <= x2; x++) {
                level.setBlock(x, y, z1, BlockType.YELLOW_CLOTH);
                level.setBlock(x, y, z2, BlockType.YELLOW_CLOTH);
            }
            // East and West walls
            for (int z = z1; z <= z2; z++) {
                level.setBlock(x1, y, z, BlockType.YELLOW_CLOTH);
                level.setBlock(x2, y, z, BlockType.YELLOW_CLOTH);
            }
        }

        // Create random doorways (at least one per wall)
        int doorHeight = MIN_DOOR_HEIGHT + random.nextInt(2); // 3-4 blocks high

        // North wall doorway
        int doorX = x1 + 1 + random.nextInt(Math.max(1, x2 - x1 - 2));
        for (int y = baseY + 1; y <= baseY + doorHeight; y++) {
            level.setBlock(doorX, y, z1, BlockType.AIR);
        }

        // South wall doorway
        doorX = x1 + 1 + random.nextInt(Math.max(1, x2 - x1 - 2));
        for (int y = baseY + 1; y <= baseY + doorHeight; y++) {
            level.setBlock(doorX, y, z2, BlockType.AIR);
        }

        // East wall doorway
        int doorZ = z1 + 1 + random.nextInt(Math.max(1, z2 - z1 - 2));
        for (int y = baseY + 1; y <= baseY + doorHeight; y++) {
            level.setBlock(x2, y, doorZ, BlockType.AIR);
        }

        // West wall doorway
        doorZ = z1 + 1 + random.nextInt(Math.max(1, z2 - z1 - 2));
        for (int y = baseY + 1; y <= baseY + doorHeight; y++) {
            level.setBlock(x1, y, doorZ, BlockType.AIR);
        }
    }

    private void createDoorway(Level level, int x, int z, int baseY) {
        int doorHeight = MIN_DOOR_HEIGHT + random.nextInt(2); // 3 or 4 blocks high
        for (int y = baseY + 1; y <= baseY + doorHeight; y++) {
            level.setBlock(x, y, z, BlockType.AIR);
        }
    }

    private void connectRooms(Level level, boolean[][] processed, int baseY) {
        // Create random corridors between processed areas
        for (int x = 2; x < width - 2; x++) {
            for (int z = 2; z < depth - 2; z++) {
                if (processed[x][z] && random.nextFloat() < 0.3) {
                    int corridorLength = 3 + random.nextInt(5);
                    int direction = random.nextInt(4);
                    createCorridor(level, x, z, corridorLength, direction, baseY);
                }
            }
        }
    }

    private void createCorridor(Level level, int startX, int startZ, int length, int direction, int baseY) {
        int dx = (direction == 0) ? 1 : (direction == 1) ? -1 : 0;
        int dz = (direction == 2) ? 1 : (direction == 3) ? -1 : 0;

        int corridorWidth = 2 + random.nextInt(2); // 2-3 blocks wide

        for (int i = 0; i < length; i++) {
            int x = startX + (dx * i);
            int z = startZ + (dz * i);

            if (!level.isInBounds(x, baseY, z)) continue;

            // Create corridor space
            for (int w = 0; w < corridorWidth; w++) {
                int wx = x + (dz * w); // perpendicular to corridor direction
                int wz = z + (dx * w);

                if (!level.isInBounds(wx, baseY, wz)) continue;

                // Clear space for corridor
                for (int y = baseY + 1; y < baseY + MIN_DOOR_HEIGHT + 1; y++) {
                    level.setBlock(wx, y, wz, BlockType.AIR);
                }

                // Add walls
                for (int y = baseY + MIN_DOOR_HEIGHT + 1; y < baseY + currentFloorHeight - 1; y++) {
                    level.setBlock(wx, y, wz, BlockType.YELLOW_CLOTH);
                }
            }
        }
    }

    private boolean isInVoid(int x, int z, int centerX, int centerZ) {
        int dx = x - centerX;
        int dz = z - centerZ;
        return dx * dx + dz * dz < (VOID_RADIUS + 5) * (VOID_RADIUS + 5); // Add buffer around void
    }

    private void createVoid(Level level, int centerX, int centerZ) {
        for (int y = 0; y < height; y++) {
            for (int x = centerX - VOID_RADIUS; x <= centerX + VOID_RADIUS; x++) {
                for (int z = centerZ - VOID_RADIUS; z <= centerZ + VOID_RADIUS; z++) {
                    if ((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ) <= VOID_RADIUS * VOID_RADIUS) {
                        level.setBlock(x, y, z, BlockType.AIR);
                    }
                }
            }
        }

        // Add glass barrier around void
        for (int y = 0; y < height; y++) {
            for (int angle = 0; angle < 360; angle++) {
                int x = centerX + (int)(VOID_RADIUS * Math.cos(Math.toRadians(angle)));
                int z = centerZ + (int)(VOID_RADIUS * Math.sin(Math.toRadians(angle)));
                if (level.isInBounds(x, y, z)) {
                    level.setBlock(x, y, z, BlockType.GLASS);
                }
            }
        }
    }

    private void createVerticalConnections(Level level, int centerX, int centerZ) {
        // Create stairs around the void
        int[] directions = {-1, 1};
        for (int xDir : directions) {
            for (int zDir : directions) {
                int startX = centerX + (VOID_RADIUS + 2) * xDir;
                int startZ = centerZ + (VOID_RADIUS + 2) * zDir;

                int currentY = 0;
                for (int floor = 0; floor < NUM_FLOORS - 1; floor++) {
                    createStaircase(level, startX, currentY, startZ, xDir, zDir);
                    currentY += currentFloorHeight;
                }
            }
        }
    }

    private void createStaircase(Level level, int startX, int startY, int startZ, int xDir, int zDir) {
        int stairLength = currentFloorHeight * 2;
        for (int i = 0; i < stairLength; i++) {
            int x = startX + (i * xDir);
            int y = startY + (i / 2);
            int z = startZ + (i * zDir);

            if (!level.isInBounds(x, y, z)) continue;

            // Create two wide stairs
            level.setBlock(x, y, z, BlockType.STONE);
            level.setBlock(x - zDir, y, z + xDir, BlockType.STONE);

            // Clear space above stairs
            for (int clearY = y + 1; clearY < y + 3; clearY++) {
                if (clearY >= height) continue;
                level.setBlock(x, clearY, z, BlockType.AIR);
                level.setBlock(x - zDir, clearY, z + xDir, BlockType.AIR);
            }
        }
    }

    private void addLights(Level level, int ceilingY) {
        for(int x = 4; x < width-4; x += 6 + random.nextInt(4)) {
            for(int z = 4; z < depth-4; z += 6 + random.nextInt(4)) {
                if (!isInVoid(x, z, width/2, depth/2)) {
                    // Random light sizes
                    int lightSize = 1 + random.nextInt(2);
                    for(int lx = 0; lx < lightSize; lx++) {
                        for(int lz = 0; lz < lightSize; lz++) {
                            if (level.isInBounds(x + lx, ceilingY, z + lz)) {
                                level.setBlock(x + lx, ceilingY, z + lz, BlockType.GLASS);
                            }
                        }
                    }
                }
            }
        }
    }
}