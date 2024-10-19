package classic.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class DespawnPlayerPacket extends Packet {
    private byte playerId;

    public DespawnPlayerPacket() {
        super(PacketType.DESPAWN_PLAYER);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeByte(type.getId());
        out.writeByte(playerId);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        playerId = in.readByte();
    }

    // Getter and setter
    public byte getPlayerId() {
        return playerId;
    }

    public void setPlayerId(byte playerId) {
        this.playerId = playerId;
    }
}
