package classic.packets;

import java.io.*;

public class MessagePacket extends Packet {
    private byte playerId;
    private String message;

    public MessagePacket() {
        super(PacketType.MESSAGE);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeByte(type.getId());
        out.writeByte(playerId);
        Packet.writeString(out, message);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        playerId = in.readByte();
        message = Packet.readString(in);
    }

    // Getters and setters
    public byte getPlayerId() {
        return playerId;
    }

    public void setPlayerId(byte playerId) {
        this.playerId = playerId;
    }

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = sanitizeMessage(message);
    }

    private String sanitizeMessage(String input) {
        // Remove trailing ampersands
        while (input.endsWith("&")) {
            input = input.substring(0, input.length() - 1);
        }

        // Ensure the message doesn't exceed 64 characters
        if (input.length() > 64) {
            input = input.substring(0, 64);
        }

        // Replace any invalid characters with spaces
        return input.replaceAll("[^\\x20-\\x7E]", " ");
    }
}