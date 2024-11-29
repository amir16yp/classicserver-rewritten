package net.classicube.api;

import net.classicube.ClientHandler;
import net.classicube.packets.MessagePacket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.classicube.api.API.splitMessage;

public class Player implements CommandSender {
    private static final Map<ClientHandler, Player> playerCache = new ConcurrentHashMap<>();
    private final ClientHandler handle;

    private Player(ClientHandler handle) {
        this.handle = handle;
    }

    public static Player getInstance(ClientHandler handle) {
        return playerCache.computeIfAbsent(handle, Player::new);
    }

    // Called when a player disconnects to clean up the cache
    public static void removeFromCache(ClientHandler handle) {
        playerCache.remove(handle);
    }

    public String getIPAddress() {
        return handle.getSocket().getInetAddress().getHostAddress();
    }

    public String getUsername() {
        return handle.getUsername();
    }

    public void sendMessage(String message) {
        // Split message into chunks
        for (String chunk : splitMessage(message)) {
            MessagePacket packet = new MessagePacket();
            packet.setMessage(chunk);
            packet.setPlayerId((byte) -1);
            try {
                handle.sendPacket(packet);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public boolean isOP() {
        return API.getInstance().getServer().getOpList().contains(this.getUsername().toLowerCase());
    }

    public void setOP(boolean op) {
        if (op) {
            API.getInstance().getServer().getOpList().add(this.getUsername().toLowerCase());
        } else {
            API.getInstance().getServer().getOpList().remove(this.getUsername().toLowerCase());
        }
    }

    public void kick(String reason) {
        handle.disconnectPlayer(reason);
    }

    // Get the underlying ClientHandler
    public ClientHandler getHandle() {
        return handle;
    }
}