package net.classicube.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class LevelDataChunkPacket extends Packet {
    private static final int CHUNK_SIZE = 1024;
    private short chunkLength;
    private byte[] chunkData;
    private byte percentComplete;

    public LevelDataChunkPacket() {
        super(PacketType.LEVEL_DATA_CHUNK);
        this.chunkData = new byte[CHUNK_SIZE];
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeByte(type.getId());
        out.writeShort(chunkLength);
        out.write(chunkData, 0, CHUNK_SIZE); // Always write 1024 bytes
        out.writeByte(percentComplete);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        chunkLength = in.readShort();
        chunkData = new byte[CHUNK_SIZE];
        in.readFully(chunkData);
        percentComplete = in.readByte();
    }

    // Getters and setters
    public short getChunkLength() {
        return chunkLength;
    }

    public void setChunkLength(short chunkLength) {
        this.chunkLength = chunkLength;
    }

    public byte[] getChunkData() {
        return Arrays.copyOf(chunkData, chunkLength);
    }

    public void setChunkData(byte[] data) {
        Arrays.fill(this.chunkData, (byte) 0); // Clear existing data
        System.arraycopy(data, 0, this.chunkData, 0, Math.min(data.length, CHUNK_SIZE));
        this.chunkLength = (short) Math.min(data.length, CHUNK_SIZE);
    }

    public byte getPercentComplete() {
        return percentComplete;
    }

    public void setPercentComplete(byte percentComplete) {
        this.percentComplete = percentComplete;
    }
}