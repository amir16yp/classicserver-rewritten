package net.classicube.api.enums;

public enum EnvColorType {
    SKY_COLOR((byte) 0),
    CLOUD_COLOR((byte) 1),
    FOG_COLOR((byte) 2),
    AMBIENT_LIGHT_COLOR((byte) 3),
    DIFFUSE_LIGHT_COLOR((byte) 4),
    SKYBOX_COLOR((byte) 5);

    private final byte value;

    EnvColorType(byte value) {
        this.value = value;
    }

    public static EnvColorType fromValue(byte value) {
        for (EnvColorType colorType : values()) {
            if (value == colorType.getValue()) {
                return colorType;
            }
        }
        return null;
    }

    public byte getValue() {
        return value;
    }
}
