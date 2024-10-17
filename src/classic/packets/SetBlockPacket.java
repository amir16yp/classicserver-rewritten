package classic.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class SetBlockPacket extends Packet {
    private short x;
    private short y;
    private short z;
    private byte mode;
    private byte blockType;

    public SetBlockPacket() {
        super(PacketType.SET_BLOCK);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeByte(type.getId());
        out.writeShort(x);
        out.writeShort(y);
        out.writeShort(z);
        out.writeByte(mode);
        out.writeByte(blockType);
        System.out.println("Sent SET_BLOCK: x=" + x + ", y=" + y + ", z=" + z +
                ", mode=" + mode + ", blockType=" + blockType);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        x = in.readShort();
        y = in.readShort();
        z = in.readShort();
        mode = in.readByte();
        blockType = in.readByte();
        System.out.println("Received SET_BLOCK: x=" + x + ", y=" + y + ", z=" + z +
                ", mode=" + mode + ", blockType=" + blockType);
    }

    public void setZ(short z) {
        this.z = z;
    }

    public void setY(short y) {
        this.y = y;
    }

    public short getZ() {
        return z;
    }

    public void setX(short x) {
        this.x = x;
    }

    public short getY() {
        return y;
    }

    public short getX() {
        return x;
    }

    public void setMode(byte mode) {
        this.mode = mode;
    }

    public void setBlockType(byte blockType) {
        this.blockType = blockType;
    }

    public byte getMode() {
        return mode;
    }

    public byte getBlockType() {
        return blockType;
    }

    // Getters and setters...
}