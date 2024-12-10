package net.classicube.packets.cpe;

import net.classicube.api.BlockSelection;
import net.classicube.packets.PacketType;

import java.io.DataOutputStream;
import java.io.IOException;

public class MakeSelectionPacket extends CPEPacket {
    private final BlockSelection blockSelection;

    public MakeSelectionPacket(BlockSelection blockSelection) {
        super(PacketType.CPE_MAKE_SELECTION);
        this.blockSelection = blockSelection;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        out.writeByte(blockSelection.getSelectionId());
        CPEPacket.writeString(out, blockSelection.getLabel());
        out.writeShort(blockSelection.getPoint1().getX());
        out.writeShort(blockSelection.getPoint1().getY());
        out.writeShort(blockSelection.getPoint1().getZ());
        out.writeShort(blockSelection.getPoint2().getX());
        out.writeShort(blockSelection.getPoint2().getY());
        out.writeShort(blockSelection.getPoint2().getZ());
        out.writeShort(blockSelection.getOutlineColor().getRed());
        out.writeShort(blockSelection.getOutlineColor().getGreen());
        out.writeShort(blockSelection.getOutlineColor().getBlue());
        out.writeShort(blockSelection.getOutlineColor().getAlpha());
    }

}
