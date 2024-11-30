package net.classicube.packets.cpe;

import net.classicube.MinecraftClassicServer;
import net.classicube.packets.PacketType;

import java.io.DataOutputStream;
import java.io.IOException;

public class ExtInfoPacket extends CPEPacket {

    private static final String appName = "JavaClassicServer";

    public ExtInfoPacket() {
        super(PacketType.CPE_EXTINFO);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        ExtInfoPacket.writeString(out, MinecraftClassicServer.APP_NAME);
        out.writeShort(1);
    }
}
