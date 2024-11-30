package net.classicube.packets.cpe;

import net.classicube.packets.PacketType;

import java.io.DataOutputStream;
import java.io.IOException;

public class ExtEntryPacket extends CPEPacket
{
    private String ExtensionName;
    private int version;

    public ExtEntryPacket(String extensionName, int version) {
        super(PacketType.CPE_EXTENTRY);
        ExtensionName = extensionName;
        this.version = version;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        CPEPacket.writeString(out, this.ExtensionName);
        out.writeByte(version);
    }
}
