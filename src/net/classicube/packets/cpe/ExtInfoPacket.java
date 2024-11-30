package net.classicube.packets.cpe;

import net.classicube.MinecraftClassicServer;
import net.classicube.packets.PacketType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class    ExtInfoPacket extends CPEPacket{

    public ExtInfoPacket() {
        super(PacketType.CPE_EXTINFO);
    }
    private static final String appName = "JavaClassicServer";

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        ExtInfoPacket.writeString(out, MinecraftClassicServer.APP_NAME);
        out.writeShort(1);
    }
}
