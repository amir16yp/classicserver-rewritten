package classic.packets;

public enum PacketType {
    // Client → Server packets
    PLAYER_IDENTIFICATION(0x00),
    SET_BLOCK(0x05),
    POSITION_ORIENTATION(0x08),
    MESSAGE(0x0d),

    // Server → Client packets
    SERVER_IDENTIFICATION(0x00),
    PING(0x01),
    LEVEL_INITIALIZE(0x02),
    LEVEL_DATA_CHUNK(0x03),
    LEVEL_FINALIZE(0x04),
    SET_BLOCK_SERVER(0x06),
    SPAWN_PLAYER(0x07),
    SET_POSITION_ORIENTATION(0x08),
    POSITION_ORIENTATION_UPDATE(0x09),
    POSITION_UPDATE(0x0a),
    ORIENTATION_UPDATE(0x0b),
    DESPAWN_PLAYER(0x0c),
    MESSAGE_SERVER(0x0d),
    DISCONNECT_PLAYER(0x0e),
    UPDATE_USER_TYPE(0x0f),

    // Special type for unknown packets
    UNKNOWN(-1);

    private final byte id;

    PacketType(int id) {
        this.id = (byte) id;
    }

    public byte getId() {
        return id;
    }

    public static PacketType fromId(byte id) {
        for (PacketType type : values()) {
            if (type.getId() == id) {
                return type;
            }
        }
        return UNKNOWN;
    }
}