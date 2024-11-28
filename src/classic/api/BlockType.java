package classic.api;

public enum BlockType {
    AIR(0),
    STONE(1),
    GRASS_BLOCK(2),
    DIRT(3),
    COBBLESTONE(4),
    PLANKS(5),
    SAPLING(6),
    BEDROCK(7),
    FLOWING_WATER(8),
    STATIONARY_WATER(9),
    FLOWING_LAVA(10),
    STATIONARY_LAVA(11),
    SAND(12),
    GRAVEL(13),
    GOLD_ORE(14),
    IRON_ORE(15),
    COAL_ORE(16),
    WOOD(17),
    LEAVES(18),
    SPONGE(19),
    GLASS(20),
    RED_CLOTH(21),
    ORANGE_CLOTH(22),
    YELLOW_CLOTH(23),
    CHARTREUSE_CLOTH(24),
    GREEN_CLOTH(25),
    SPRING_GREEN_CLOTH(26),
    CYAN_CLOTH(27),
    CAPRI_CLOTH(28),
    ULTRAMARINE_CLOTH(29),
    VIOLET_CLOTH(30),
    PURPLE_CLOTH(31),
    MAGENTA_CLOTH(32),
    ROSE_CLOTH(33),
    DARK_GRAY_CLOTH(34),
    LIGHT_GRAY_CLOTH(35),
    WHITE_CLOTH(36),
    FLOWER(37),
    ROSE(38),
    BROWN_MUSHROOM(39),
    RED_MUSHROOM(40),
    BLOCK_OF_GOLD(41),
    BLOCK_OF_IRON(42),
    DOUBLE_SLAB(43),
    SLAB(44),
    BRICKS(45),
    TNT(46),
    BOOKSHELF(47),
    MOSSY_COBBLESTONE(48),
    OBSIDIAN(49);

    private final byte id;

    BlockType(int id) {
        this.id = (byte) id;
    }

    public byte getId() {
        return id;
    }

    public static BlockType getById(byte id) {
        for (BlockType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return null; // return null, this is useful for checking if a block id is valid
    }

    public boolean isLiquid() {
        return this == FLOWING_WATER || this == STATIONARY_WATER ||
                this == FLOWING_LAVA || this == STATIONARY_LAVA;
    }

    public boolean isSolid() {
        return this != AIR && !isLiquid();
    }

    public boolean isCloth() {
        return (id >= RED_CLOTH.id && id <= WHITE_CLOTH.id);
    }
}