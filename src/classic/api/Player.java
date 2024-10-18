package classic.api;

import classic.ClientHandler;
import classic.MinecraftClassicServer;
import classic.packets.MessagePacket;
import classic.packets.SetBlockServerPacket;
import classic.level.LevelGenerator;

import java.io.IOException;

public class Player {
    private final ClientHandler handler;

    public Player(ClientHandler handler) {
        this.handler = handler;
    }

    public String getName() {
        return handler.getUsername();
    }

    public byte getEntityId() {
        return handler.getPlayerId();
    }

    public Location getLocation() {
        return new Location(handler.getX(), handler.getY(), handler.getZ(), handler.getYaw(), handler.getPitch());
    }

    public void sendMessage(String message) {
        try {
            MessagePacket packet = new MessagePacket();
            packet.setPlayerId((byte) -1); // Server message
            packet.setMessage(message);
            packet.write(handler.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setBlock(int x, int y, int z, byte blockType) {
        if (isValidBlockPosition(x, y, z) && isValidBlockType(blockType)) {
            MinecraftClassicServer.setBlock((short) x, (short) y, (short) z, blockType);
            handler.broadcastBlockChange((short) x, (short) y, (short) z, blockType);
        }
    }

    public byte getBlock(int x, int y, int z) {
        if (isValidBlockPosition(x, y, z)) {
            return MinecraftClassicServer.getBlock((short) x, (short) y, (short) z);
        }
        return 0; // Air block or invalid position
    }

    public void kick(String reason) {
        handler.disconnectPlayer(reason);
    }

    private boolean isValidBlockPosition(int x, int y, int z) {
        return x >= 0 && x < LevelGenerator.getWidth() &&
                y >= 0 && y < LevelGenerator.getHeight() &&
                z >= 0 && z < LevelGenerator.getDepth();
    }

    private boolean isValidBlockType(byte blockType) {
        return blockType >= 0 && blockType <= 49;
    }

}