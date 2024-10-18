package classic.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ClientPositionPacket extends Packet {
    private byte playerId;
    private short x; // Fixed-point (5 bits integer, 5 bits fractional)
    private short y; // Fixed-point (5 bits integer, 5 bits fractional)
    private short z; // Fixed-point (5 bits integer, 5 bits fractional)
    private byte yaw;
    private byte pitch;

    public ClientPositionPacket() {
        super(PacketType.POSITION_ORIENTATION);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeByte(type.getId());
        out.writeByte(playerId);
        out.writeShort(x);
        out.writeShort(y);
        out.writeShort(z);
        out.writeByte(yaw);
        out.writeByte(pitch);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        playerId = in.readByte();
        x = in.readShort();
        y = in.readShort();
        z = in.readShort();
        yaw = in.readByte();
        pitch = in.readByte();
    }

    // Method to set player position using double values
    public void setPosition(double x, double y, double z) {
        this.x = (short) (x * 32); // Convert to fixed-point
        this.y = (short) (y * 32); // Convert to fixed-point
        this.z = (short) (z * 32); // Convert to fixed-point
    }

    // Method to get player position as double values
    public double getXPosition() {
        return x / 32.0; // Convert from fixed-point
    }

    public double getYPosition() {
        return y / 32.0; // Convert from fixed-point
    }

    public double getZPosition() {
        return z / 32.0; // Convert from fixed-point
    }

    // Getters and setters
    public byte getPlayerId() {
        return playerId;
    }

    public void setPlayerId(byte playerId) {
        this.playerId = playerId;
    }

    public short getX() {
        return x;
    }

    public void setX(short x) {
        this.x = x;
    }

    public short getY() {
        return y;
    }

    public void setY(short y) {
        this.y = y;
    }

    public short getZ() {
        return z;
    }

    public void setZ(short z) {
        this.z = z;
    }

    public byte getYaw() {
        return yaw;
    }

    public void setYaw(byte yaw) {
        this.yaw = yaw;
    }

    public byte getPitch() {
        return pitch;
    }

    public void setPitch(byte pitch) {
        this.pitch = pitch;
    }
}
