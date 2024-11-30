package net.classicube.api.enums;

public enum SetBlockMode {
    DESTROY((byte) 0x00),  // Player destroying a block
    PLACE((byte) 0x01);    // Player placing a block

    private final byte modeId;

    SetBlockMode(byte modeId) {
        this.modeId = modeId;
    }

    public static SetBlockMode getById(byte id) {
        for (SetBlockMode mode : values()) {
            if (mode.modeId == id) {
                return mode;
            }
        }
        return null;
    }

    public byte getModeId() {
        return modeId;
    }

    public boolean isDestroy() {
        return this == DESTROY;
    }

    public boolean isPlace() {
        return this == PLACE;
    }
}