package net.classicube.packets.cpe;

import net.classicube.packets.PacketType;

import java.io.DataOutputStream;
import java.io.IOException;

public class ExtRemovePlayerNamePacket extends CPEPacket {
    private final byte playerID;

    public ExtRemovePlayerNamePacket(byte playerID) {
        super(PacketType.CPE_EXT_REMOVE_PLAYER_NAME);
        this.playerID = playerID;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        out.writeByte(playerID);
    }
}
