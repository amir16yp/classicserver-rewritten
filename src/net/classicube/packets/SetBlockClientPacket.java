package net.classicube.packets;

import net.classicube.api.BlockType;
import net.classicube.api.SetBlockMode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class SetBlockClientPacket extends Packet {
    private short x;
    private short y;
    private short z;
    private byte mode;
    private byte blockType;

    public SetBlockClientPacket() {
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
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        x = in.readShort();
        y = in.readShort();
        z = in.readShort();
        mode = in.readByte();
        blockType = in.readByte();
        //System.out.println("Received SET_BLOCK from client: x=" + x + ", y=" + y + ", z=" + z +
        //        ", mode=" + mode + ", blockType=" + blockType);
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

    public byte getBlockID() {
        return blockType;
    }

    public BlockType getBlockType() {
        return BlockType.getById(getBlockID());
    }

    public void setBlockType(byte blockType) {
        this.blockType = blockType;
    }

    public SetBlockMode getMode() {
        return SetBlockMode.getById(mode);
    }

    public void setMode(int mode) {
        this.mode = (byte) mode;
    }

    // Getters and setters
    // ... (include all getters and setters for x, y, z, mode, and blockType)
}