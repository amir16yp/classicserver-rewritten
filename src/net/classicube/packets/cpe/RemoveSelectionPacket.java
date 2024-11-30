package net.classicube.packets.cpe;

import net.classicube.packets.PacketType;

import java.io.DataOutputStream;
import java.io.IOException;

public class RemoveSelectionPacket extends CPEPacket {
    private final byte toRemoveID;

    public RemoveSelectionPacket(byte toRemoveID) {
        super(PacketType.CPE_REMOVE_SELECTION);
        this.toRemoveID = toRemoveID;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        out.writeByte(toRemoveID);
    }
}
