package classic.api;

public class Location {
    private final short x, y, z;
    private final byte yaw, pitch;

    public Location(short x, short y, short z, byte yaw, byte pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public short getX() { return x; }
    public short getY() { return y; }
    public short getZ() { return z; }
    public byte getYaw() { return yaw; }
    public byte getPitch() { return pitch; }
}