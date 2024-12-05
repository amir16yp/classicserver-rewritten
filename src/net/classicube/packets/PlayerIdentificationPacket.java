package net.classicube.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PlayerIdentificationPacket extends Packet {
    private byte protocolVersion;
    private String username;
    private String verificationKey;
    private byte paddingByte;

    public PlayerIdentificationPacket() {
        super(PacketType.PLAYER_IDENTIFICATION);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        out.writeByte(protocolVersion);
        Packet.writeString(out, username);
        Packet.writeString(out, verificationKey);
        out.writeByte(paddingByte);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        protocolVersion = in.readByte();
        username = Packet.readString(in);
        verificationKey = Packet.readString(in);
        paddingByte = in.readByte();
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

    public byte getPaddingByte() {
        return paddingByte;
    }

    public void setPaddingByte(byte paddingByte) {
        this.paddingByte = paddingByte;
    }
}