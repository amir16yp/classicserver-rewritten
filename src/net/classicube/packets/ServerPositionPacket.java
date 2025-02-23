package net.classicube.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ServerPositionPacket extends Packet {
    private byte playerId;
    private short x;
    private short y;
    private short z;
    private byte yaw;
    private byte pitch;

    public ServerPositionPacket() {
        super(PacketType.SET_POSITION_ORIENTATION);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
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