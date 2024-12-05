package net.classicube.packets.cpe;

import net.classicube.packets.Packet;
import net.classicube.packets.PacketType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class CPEPacket extends Packet {
    public CPEPacket(PacketType cpePacketType) {
        super(cpePacketType);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {

    }

    @Override
    public void read(DataInputStream in) throws IOException {

    }
}
