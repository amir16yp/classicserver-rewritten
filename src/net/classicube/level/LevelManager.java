// LevelManager.java
package net.classicube.level;

import net.classicube.ClientHandler;
import net.classicube.api.Player;
import net.classicube.packets.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

import static net.classicube.ClientHandler.broadcastPacketToLevelExcept;

public class LevelManager {
    private static final String LEVELS_DIRECTORY = "levels";
    private final Map<String, Level> levels = new ConcurrentHashMap<>();
    private final Map<Player, String> playerLevels = new ConcurrentHashMap<>();

    public LevelManager() {
        initializeDirectory();
    }

    public void setPlayerLevel(Player player, String levelName) {
        playerLevels.put(player, levelName);
    }

    private void initializeDirectory() {
        try {
            Files.createDirectories(Paths.get(LEVELS_DIRECTORY));
        } catch (IOException e) {
            System.err.println("Failed to create levels directory: " + e.getMessage());
        }
    }

    public void loadLevel(String name) throws IOException {
        Path levelPath = Paths.get(LEVELS_DIRECTORY, name + ".dat");
        if (Files.exists(levelPath)) {
            Level level = Level.loadFromFile(levelPath.toString());
            levels.put(name, level);
            System.out.println("Loaded level: " + name);
        } else {
            throw new IOException("Level file not found: " + levelPath);
        }
    }

    public void createLevel(String name, short width, short height, short depth) throws IOException {
        // Check if level already exists
        if (levels.containsKey(name)) {
            throw new IOException("Level already exists: " + name);
        }

        // Generate new level
        Level level = new LevelGenerator(width, height, depth).generateFlatWorld();
        levels.put(name, level);

        // Save it immediately
        saveLevel(name);
        System.out.println("Created new level: " + name);
    }

    public void saveLevel(String name) throws IOException {
        Level level = levels.get(name);
        if (level == null) {
            throw new IOException("Level not found: " + name);
        }

        Path levelPath = Paths.get(LEVELS_DIRECTORY, name + ".dat");
        level.saveToFile(levelPath.toString());
        System.out.println("Saved level: " + name);
    }

    public void saveAllLevels() {
        for (String levelName : levels.keySet()) {
            try {
                saveLevel(levelName);
            } catch (IOException e) {
                System.err.println("Failed to save level " + levelName + ": " + e.getMessage());
            }
        }
    }

    public boolean switchPlayerLevel(Player player, String levelName) {
        Level level = levels.get(levelName);
        if (level == null) {
            return false;
        }

        ClientHandler handler = player.getHandle();
        try {
            // First despawn from old level if player was in one
            String oldLevel = playerLevels.get(player);
            if (oldLevel != null) {
                // Broadcast despawn to players in old level
                DespawnPlayerPacket despawnPacket = new DespawnPlayerPacket();
                despawnPacket.setPlayerId(handler.getPlayerId());
                broadcastPacketToLevelExcept(despawnPacket, oldLevel, handler);
            }

            // Send level switch packets
            sendLevelToPlayer(handler, level);

            // Update player level tracking
            playerLevels.put(player, levelName);

            // Send spawns of existing players in this level to the joining player
            for (ClientHandler existingClient : ClientHandler.getClientsInLevel(levelName)) {
                if (existingClient != handler) {
                    SpawnPlayerPacket spawnExisting = new SpawnPlayerPacket();
                    spawnExisting.setPlayerId(existingClient.getPlayerId());
                    spawnExisting.setPlayerName(existingClient.getUsername());
                    spawnExisting.setX(existingClient.getX());
                    spawnExisting.setY(existingClient.getY());
                    spawnExisting.setZ(existingClient.getZ());
                    spawnExisting.setYaw(existingClient.getYaw());
                    spawnExisting.setPitch(existingClient.getPitch());
                    handler.sendPacket(spawnExisting);
                }
            }

            // Broadcast spawn of joining player to everyone else in the level
            SpawnPlayerPacket spawnJoining = new SpawnPlayerPacket();
            spawnJoining.setPlayerId(handler.getPlayerId());
            spawnJoining.setPlayerName(handler.getUsername());
            spawnJoining.setX(handler.getX());
            spawnJoining.setY(handler.getY());
            spawnJoining.setZ(handler.getZ());
            spawnJoining.setYaw(handler.getYaw());
            spawnJoining.setPitch(handler.getPitch());
            broadcastPacketToLevelExcept(spawnJoining, levelName, handler);

            return true;
        } catch (IOException e) {
            System.err.println("Failed to switch level for " + player.getUsername() + ": " + e.getMessage());
            return false;
        }
    }

    private void sendLevelToPlayer(ClientHandler handler, Level level) throws IOException {
        // 1. Initialize level transfer
        handler.sendPacket(new LevelInitializePacket());

        // 2. Send compressed level data
        byte[] levelData = level.getBlockData();
        byte[] compressedData = compressLevelData(levelData);
        sendCompressedData(handler, compressedData);

        // 3. Finalize level transfer
        LevelFinalizePacket finalizePacket = new LevelFinalizePacket();
        finalizePacket.setXSize(level.getWidth());
        finalizePacket.setYSize(level.getHeight());
        finalizePacket.setZSize(level.getDepth());
        handler.sendPacket(finalizePacket);

        // 4. Position player in middle of level
        teleportToSpawn(handler, level);
    }

    private byte[] compressLevelData(byte[] levelData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(levelData.length);
        dos.write(levelData);
        dos.flush();

        ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(compressedBaos)) {
            gzipOut.write(baos.toByteArray());
        }
        return compressedBaos.toByteArray();
    }

    private void sendCompressedData(ClientHandler handler, byte[] compressedData) throws IOException {
        int chunkSize = 1024;
        int totalChunks = (compressedData.length + chunkSize - 1) / chunkSize;

        for (int i = 0, chunkIndex = 0; i < compressedData.length; i += chunkSize, chunkIndex++) {
            LevelDataChunkPacket chunkPacket = new LevelDataChunkPacket();
            int remainingBytes = Math.min(chunkSize, compressedData.length - i);
            chunkPacket.setChunkLength((short) remainingBytes);

            byte[] chunkData = new byte[chunkSize];
            System.arraycopy(compressedData, i, chunkData, 0, remainingBytes);

            chunkPacket.setChunkData(chunkData);
            chunkPacket.setPercentComplete((byte) ((chunkIndex + 1) * 100 / totalChunks));

            handler.sendPacket(chunkPacket);
        }
    }

    private void teleportToSpawn(ClientHandler handler, Level level) throws IOException {
        ServerPositionPacket posPacket = new ServerPositionPacket();
        posPacket.setPlayerId((byte) -1);
        posPacket.setX((short) (level.getWidth() * 32 / 2));
        posPacket.setY((short) (level.getHeight() * 32 / 2));
        posPacket.setZ((short) (level.getDepth() * 32 / 2));
        posPacket.setYaw((byte) 0);
        posPacket.setPitch((byte) 0);
        handler.sendPacket(posPacket);
    }

    public Level getLevel(String name) {
        return levels.get(name);
    }

    public String getPlayerLevel(Player player) {
        return playerLevels.get(player);
    }

    public Set<String> getLevelNames() {
        return levels.keySet();
    }

    public boolean levelExists(String name) {
        return levels.containsKey(name);
    }

    public void deleteLevel(String name) throws IOException {
        // Don't delete the level if players are in it
        if (playerLevels.containsValue(name)) {
            throw new IOException("Cannot delete level while players are in it");
        }

        // Remove from memory
        levels.remove(name);

        // Delete file
        Path levelPath = Paths.get(LEVELS_DIRECTORY, name + ".dat");
        Files.deleteIfExists(levelPath);
    }
}