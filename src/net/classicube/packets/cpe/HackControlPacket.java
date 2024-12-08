package net.classicube.packets.cpe;

import net.classicube.packets.Packet;
import net.classicube.packets.PacketType;

import java.io.DataOutputStream;
import java.io.IOException;

public class HackControlPacket extends CPEPacket {
    private final boolean flying;
    private final boolean noclip;
    private final boolean speeding;
    private final boolean spawnControl;
    private final boolean thirdPerson;
    private final short jumpHeight;

    public HackControlPacket(boolean flying, boolean noclip, boolean speeding,boolean spawnControl, boolean thirdPerson, short jumpHeight)
    {
        super(PacketType.CPE_HACK_CONTROL);
        this.flying = flying;
        this.noclip = noclip;
        this.speeding = speeding;
        this.spawnControl = spawnControl;
        this.thirdPerson = thirdPerson;
        this.jumpHeight = jumpHeight;
    }

    public static byte boolToByte(boolean value) {
        return (byte) (value ? 1 : 0);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        out.writeByte(boolToByte(flying));
        out.writeByte(boolToByte(noclip));
        out.writeByte(boolToByte(speeding));
        out.writeByte(boolToByte(spawnControl));
        out.writeByte(boolToByte(thirdPerson));
        out.writeShort(jumpHeight);
    }
}
