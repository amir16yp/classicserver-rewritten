package net.classicube.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ServerIdentificationPacket extends Packet {
    private byte protocolVersion;
    private String serverName;
    private String serverMOTD;
    private byte userType;

    public ServerIdentificationPacket() {
        super(PacketType.SERVER_IDENTIFICATION);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        out.writeByte(protocolVersion);
        Packet.writeString(out, serverName);
        Packet.writeString(out, serverMOTD);
        out.writeByte(userType);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        protocolVersion = in.readByte();
        serverName = Packet.readString(in);
        serverMOTD = Packet.readString(in);
        userType = in.readByte();
    }

    // Getters and setters
    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerMOTD() {
        return serverMOTD;
    }

    public void setServerMOTD(String serverMOTD) {
        this.serverMOTD = serverMOTD;
    }

    public byte getUserType() {
        return userType;
    }

    public void setUserType(byte userType) {
        this.userType = userType;
    }
}