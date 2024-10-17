package classic.packets;

import java.io.*;

public class LevelFinalizePacket extends Packet {
    private short xSize;
    private short ySize;
    private short zSize;

    public LevelFinalizePacket() {
        super(PacketType.LEVEL_FINALIZE);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeByte(type.getId());
        out.writeShort(xSize);
        out.writeShort(ySize);
        out.writeShort(zSize);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        xSize = in.readShort();
        ySize = in.readShort();
        zSize = in.readShort();
    }

    // Getters and setters
    public short getXSize() {
        return xSize;
    }

    public void setXSize(short xSize) {
        this.xSize = xSize;
    }

    public short getYSize() {
        return ySize;
    }

    public void setYSize(short ySize) {
        this.ySize = ySize;
    }

    public short getZSize() {
        return zSize;
    }

    public void setZSize(short zSize) {
        this.zSize = zSize;
    }
}