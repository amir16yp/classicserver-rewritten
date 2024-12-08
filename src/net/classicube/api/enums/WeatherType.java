package net.classicube.api.enums;

public enum WeatherType {
    SUNNY((byte) 0),  // Player destroying a block
    RAINING((byte) 1),
    SNOWING((byte) 2);// Player placing a block

    private final byte id;

    WeatherType(byte id) {
        this.id = id;
    }

    public static WeatherType getById(byte id) {
        for (WeatherType mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return null;
    }

    public byte getId() {
        return id;
    }
}