package classic.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class SetBlockServerPacket extends Packet {
    private short x;
    private short y;
    private short z;
    private byte blockType;

    public SetBlockServerPacket() {
        super(PacketType.SET_BLOCK_SERVER);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeByte(type.getId());
        out.writeShort(x);
        out.writeShort(y);
        out.writeShort(z);
        out.writeByte(blockType);
        //System.out.println("Sent SET_BLOCK to client: x=" + x + ", y=" + y + ", z=" + z +
         //       ", blockType=" + blockType);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        x = in.readShort();
        y = in.readShort();
        z = in.readShort();
        blockType = in.readByte();
    }

    public void setZ(short z) {
        this.z = z;
    }

    public void setY(short y) {
        this.y = y;
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

    public short getZ() {
        return z;
    }

    public byte getBlockType() {
        return blockType;
    }

    public void setBlockType(byte blockType) {
        this.blockType = blockType;
    }

    // Getters and setters
    // ... (include all getters and setters for x, y, z, and blockType)
}