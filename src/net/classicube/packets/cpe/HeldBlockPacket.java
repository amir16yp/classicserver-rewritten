package net.classicube.packets.cpe;

import net.classicube.api.enums.BlockType;
import net.classicube.packets.PacketType;

import java.io.DataOutputStream;
import java.io.IOException;

public class HeldBlockPacket extends CPEPacket {

    private final BlockType blockType;
    private final boolean force;

    public HeldBlockPacket(BlockType blockType, boolean force) {
        super(PacketType.CPE_HELD_BLOCK);
        this.blockType = blockType;
        this.force = force;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        out.writeByte(blockType.getId());
        out.writeByte((byte) (force ? 1 : 0));
    }
}
