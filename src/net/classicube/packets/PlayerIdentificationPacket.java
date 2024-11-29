package net.classicube.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PlayerIdentificationPacket extends Packet {
    private byte protocolVersion;
    private String username;
    private String verificationKey;
    private byte unused;

    public PlayerIdentificationPacket() {
        super(PacketType.PLAYER_IDENTIFICATION);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeByte(type.getId());
        out.writeByte(protocolVersion);
        Packet.writeString(out, username);
        Packet.writeString(out, verificationKey);
        out.writeByte(unused);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        protocolVersion = in.readByte();
        username = Packet.readString(in);
        verificationKey = Packet.readString(in);
        unused = in.readByte();
    }

    // Getters and setters
    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getVerificationKey() {
        return verificationKey;
    }

    public void setVerificationKey(String verificationKey) {
        this.verificationKey = verificationKey;
    }

    public byte getUnused() {
        return unused;
    }

    public void setUnused(byte unused) {
        this.unused = unused;
    }
}