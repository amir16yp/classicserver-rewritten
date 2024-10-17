package classic.packets;

import java.io.*;

public class DisconnectPlayerPacket extends Packet {
    private String reason;

    public DisconnectPlayerPacket() {
        super(PacketType.DISCONNECT_PLAYER);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeByte(type.getId());
        Packet.writeString(out, reason);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        reason = Packet.readString(in);
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}