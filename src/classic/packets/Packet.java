package classic.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class Packet {
    protected final PacketType type;

    public Packet(PacketType type) {
        this.type = type;
    }

    public PacketType getType() {
        return type;
    }

    public abstract void write(DataOutputStream out) throws IOException;
    public abstract void read(DataInputStream in) throws IOException;

    protected static String readString(DataInputStream in) throws IOException {
        byte[] bytes = new byte[64];
        in.readFully(bytes);
        return new String(bytes, "UTF-8").trim();
    }

    protected static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = new byte[64];
        byte[] stringBytes = s.getBytes("UTF-8");
        System.arraycopy(stringBytes, 0, bytes, 0, Math.min(stringBytes.length, 64));
        out.write(bytes);
    }
}