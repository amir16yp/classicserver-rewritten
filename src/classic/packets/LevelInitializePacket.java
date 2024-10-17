package classic.packets;

import java.io.*;

public class LevelInitializePacket extends Packet {
    public LevelInitializePacket() {
        super(PacketType.LEVEL_INITIALIZE);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeByte(type.getId());
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        // This packet has no data to read
    }
}
