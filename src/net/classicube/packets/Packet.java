package net.classicube.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class Packet {
    protected final PacketType type;

    public Packet(PacketType type) {
        this.type = type;
    }

    protected static String readString(DataInputStream in) throws IOException {
        byte[] bytes = new byte[64];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    protected static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = new byte[64];
        byte[] stringBytes = s.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(stringBytes, 0, bytes, 0, Math.min(stringBytes.length, 64));
        out.write(bytes);
    }

    public PacketType getType() {
        return type;
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeByte(this.getType().getId());
    }

    public abstract void read(DataInputStream in) throws IOException;
}