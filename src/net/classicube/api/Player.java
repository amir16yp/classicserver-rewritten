package net.classicube.api;

import net.classicube.ClientHandler;
import net.classicube.level.Level;
import net.classicube.level.LevelManager;
import net.classicube.packets.MessagePacket;
import net.classicube.packets.ServerPositionPacket;
import net.classicube.packets.cpe.MakeSelectionPacket;
import net.classicube.packets.cpe.RemoveSelectionPacket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.classicube.api.API.splitMessage;

public class Player implements CommandSender {
    private static final Map<ClientHandler, Player> playerCache = new ConcurrentHashMap<>();
    private final ClientHandler handle;
    private final Map<Byte, BlockSelection> activeSelections = new ConcurrentHashMap<>();
    private byte nextSelectionID = 0;

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

    public void sendSelection(BlockSelection blockSelection) {
        if (activeSelections.size() >= 127) {
            removeOldestSelection();
        }

        blockSelection.setSelectionId(nextSelectionID);
        activeSelections.put(nextSelectionID, blockSelection);

        MakeSelectionPacket packet = new MakeSelectionPacket(blockSelection);
        try {
            handle.sendPacket(packet);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send selection packet", e);
        }

        nextSelectionID = (byte) ((nextSelectionID + 1) % 127);
    }

    public void removeSelection(byte selectionId) {
        if (activeSelections.remove(selectionId) != null) {
            RemoveSelectionPacket packet = new RemoveSelectionPacket(selectionId);
            try {
                handle.sendPacket(packet);
            } catch (IOException e) {
                throw new RuntimeException("Failed to send remove selection packet", e);
            }
        }
    }

    private void removeOldestSelection() {
        byte oldestID = activeSelections.keySet().iterator().next();
        removeSelection(oldestID);
    }


    public void teleport(Location location) {
        ServerPositionPacket positionPacket = new ServerPositionPacket();
        positionPacket.setPlayerId((byte) -1);
        positionPacket.setX(location.getRawX());
        positionPacket.setY(location.getRawY());
        positionPacket.setZ(location.getRawZ());
        positionPacket.setYaw(location.getYaw());
        positionPacket.setPitch(location.getPitch());
        ClientHandler.broadcastPacket(positionPacket);
    }

    public Location getLocation() {
        return new Location(this.handle.getX(), this.handle.getY(), this.handle.getZ(), this.handle.getYaw(), this.handle.getPitch());
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

    public Level getLevel()
    {
        LevelManager levelManager = API.getInstance().getServer().getLevelManager();;
        return levelManager.getLevel(levelManager.getPlayerLevel(this));
    }

    // Get the underlying ClientHandler
    public ClientHandler getHandle() {
        return handle;
    }
}