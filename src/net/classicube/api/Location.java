package net.classicube.api;

import net.classicube.api.enums.BlockType;

public class Location {
    private final short x, y, z;
    private final short rawX;
    private final short rawY;
    private final short rawZ;
    private final byte yaw, pitch;

    public Location(short rawX, short rawY, short rawZ, byte yaw, byte pitch) {
        this.rawX = rawX;
        this.rawY = rawY;
        this.rawZ = rawZ;
        this.x = (short) (rawX >> 5);
        this.y = (short) (rawY >> 5);
        this.z = (short) (rawZ >> 5);
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static Location fromBlockCoordinates(short x, short y, short z, byte yaw, byte pitch) {
        return new Location((short) (x << 5), (short) (y << 5), (short) (z << 5), yaw, pitch);
    }

    public static Location fromBlockCoordinates(short x, short y, short z) {
        return fromBlockCoordinates(x, y, z, (byte) 0, (byte) 0);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public byte getYaw() {
        return yaw;
    }

    public byte getPitch() {
        return pitch;
    }

    public short getRawX() {
        return rawX;
    }

    public short getRawY() {
        return rawY;
    }

    public short getRawZ() {
        return rawZ;
    }

    public BlockType getBlockType() {
        return API.getInstance().getServer().getBlock(x, y, z);
    }

    public void setBlockType(BlockType newBlockType) {
        API.getInstance().getServer().setBlock(x, y, z, newBlockType);
    }

    @Override
    public String toString() {
        return "Location{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", block=" + getBlockType().name() +
                '}';
    }

    public Location add(short dx, short dy, short dz) {
        return new Location((short) ((this.x << 5) + (dx << 5)), (short) ((this.y << 5) + (dy << 5)), (short) ((this.z << 5) + (dz << 5)), yaw, pitch);
    }

    public double distance(Location other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        int dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public Location clone() {
        return new Location(rawX, rawY, rawZ, yaw, pitch);
    }
}