package net.classicube.packets.cpe;

import net.classicube.api.BlockSelection;
import net.classicube.api.Location;
import net.classicube.packets.PacketType;

import java.awt.*;
import java.io.DataOutputStream;
import java.io.IOException;

public class MakeSelectionPacket extends CPEPacket {
    private final BlockSelection blockSelection;
    private static byte selectionId;
    public MakeSelectionPacket(BlockSelection blockSelection) {
        super(PacketType.CPE_MAKE_SELECTION);
        this.blockSelection = blockSelection;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        blockSelection.setSelectionId(selectionId);
        out.writeByte(selectionId);
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
        selectionId++;
        if (selectionId > 127)
        {
            selectionId = 0;
        }
    }

}
