package net.classicube;

import net.classicube.api.API;
import net.classicube.api.Location;
import net.classicube.api.Player;
import net.classicube.api.enums.BlockType;
import net.classicube.api.event.EventRegistry;
import net.classicube.api.event.PlayerBreakBlockEvent;
import net.classicube.api.event.PlayerJoinEvent;
import net.classicube.api.event.PlayerPlaceBlockEvent;
import net.classicube.level.Level;
import net.classicube.packets.*;
import net.classicube.packets.cpe.CPEPacket;
import net.classicube.packets.cpe.ExtAddPlayerNamePacket;
import net.classicube.packets.cpe.ExtRemovePlayerNamePacket;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

public class ClientHandler implements Runnable {
    private static final ConcurrentHashMap<Byte, ClientHandler> clients = new ConcurrentHashMap<>();
    private static byte nextPlayerId = 0;
    private final Socket socket;
    private final MinecraftClassicServer server;
    private final Object writeLock = new Object();
    private final Object readLock = new Object();
    private final byte playerId;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;
    private short x, y, z;
    private byte yaw, pitch;
    private boolean supportsCPE;

    public ClientHandler(Socket socket, MinecraftClassicServer server) {
        this.socket = socket;
        this.server = server;
        this.playerId = getNextPlayerId();
        System.out.println("New client connected. Assigned player ID: " + playerId);
    }

    public static void broadcastPacket(Packet packet) {
        for (ClientHandler client : getClients()) {
            if (client.socket.isConnected()) {
                try {
                    client.sendPacket(packet);
                } catch (Exception e) {
                    System.out.println("ERROR SENDING PACKET TO " + client + " " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public static void broadcastPacketExcept(Packet packet, ClientHandler except) {
        for (ClientHandler client : getClients()) {
            if (client.socket.isConnected() && client != except) {
                try {
                    client.sendPacket(packet);
                } catch (Exception e) {
                    System.out.println("ERROR SENDING PACKET TO " + client + " " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public static Collection<ClientHandler> getClientsInLevel(String levelName) {
        ArrayList<ClientHandler> clientsInLevel = new ArrayList<>();
        for (ClientHandler client : getClients()) {
            String clientLevel = client.server.getLevelManager().getPlayerLevel(Player.getInstance(client));
            if (levelName.equals(clientLevel)) {
                clientsInLevel.add(client);
            }
        }
        return clientsInLevel;
    }

    public static void broadcastPacketToLevel(Packet packet, String levelName) {
        for (ClientHandler client : getClientsInLevel(levelName)) {
            if (client.socket.isConnected()) {
                try {
                    client.sendPacket(packet);
                } catch (Exception e) {
                    System.out.println("ERROR SENDING PACKET TO " + client + " " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public static void broadcastPacketToLevelExcept(Packet packet, String levelName, ClientHandler except) {
        for (ClientHandler client : getClientsInLevel(levelName)) {
            if (client.socket.isConnected() && client != except) {
                try {
                    client.sendPacket(packet);
                } catch (Exception e) {
                    System.out.println("ERROR SENDING PACKET TO " + client + " " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public static ClientHandler getByName(String username) {
        for (ClientHandler clientHandler : ClientHandler.getClients()) {
            if (username.equals(clientHandler.getUsername())) {
                return clientHandler;
            }
        }
        return null;
    }

    public static ClientHandler getByNameCaseInsensitive(String username) {
        for (ClientHandler clientHandler : ClientHandler.getClients()) {
            if (username.equalsIgnoreCase(clientHandler.getUsername())) {
                return clientHandler;
            }
        }
        return null;
    }

    public static int getClientCount() {
        return clients.size();
    }

    public static Collection<ClientHandler> getClients() {
        return clients.values();
    }

    // Utility methods
    private static synchronized byte getNextPlayerId() {
        byte id = nextPlayerId++;
        if (nextPlayerId < 0) nextPlayerId = 0;
        return id;
    }

    @Override
    public String toString() {
        return "<ID: " + playerId + " NAME: " + username + " IP: " + this.socket.getInetAddress().getHostAddress() + ">";
    }

    public Socket getSocket() {
        return socket;
    }

    public void sendPacket(Packet packet) throws IOException {
        synchronized (writeLock) {
            if (packet instanceof CPEPacket && !this.supportsCPE) {
                return;
            }
            packet.write(out);
            out.flush();
            //System.out.println("SENT " + packet.getType().name() + " TO " + username);
        }
    }

    public void readPacket(Packet packet) throws IOException {
        synchronized (readLock) {
            packet.read(in);
        }
    }

    public byte readPacketId() throws IOException {
        synchronized (readLock) {
            return in.readByte();
        }
    }

    @Override
    public void run() {
        try {
            setupStreams();
            if (handlePlayerIdentification()) {
                sendLevelData();
                spawnPlayer();
                broadcastSpawn();
                clients.put(playerId, this);
                EventRegistry.callEvent(new PlayerJoinEvent(Player.getInstance(this)));
                gameLoop();
            }
        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
            e.printStackTrace();
        } finally {
            disconnectPlayer("Connection closed");
        }
    }

    private void setupStreams() throws IOException {
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    private boolean handlePlayerIdentification() throws IOException {
        byte firstPacketId = readPacketId();
        if (firstPacketId != PacketType.PLAYER_IDENTIFICATION.getId()) {
            disconnectPlayer("Invalid initial packet");
            return false;
        }

        PlayerIdentificationPacket packet = new PlayerIdentificationPacket();
        readPacket(packet);
        this.supportsCPE = packet.getPaddingByte() == 0x42;

        if (packet.getProtocolVersion() != server.getProtocolVersion()) {
            disconnectPlayer("Incompatible protocol version");
            return false;
        }

        username = packet.getUsername();

        // Check for existing player with same name
        if (getByNameCaseInsensitive(username) != null) {
            disconnectPlayer("A player with that name is already online!");
            return false;
        }

        if (this.server.isVerifyPlayers()) {
            if (!this.server.verifyPlayer(username, packet.getVerificationKey())) {
                disconnectPlayer("Name verification failed!");
                return false;
            }
        }
        if (this.server.getBanList().contains(username)) {
            disconnectPlayer("You are banned!");
            return false;
        }
        sendServerIdentification();
        ExtAddPlayerNamePacket playerNamePacket = new ExtAddPlayerNamePacket();
        playerNamePacket.setGroupName("players");
        playerNamePacket.setNameID(this.playerId);
        playerNamePacket.setAutocompletePlayerName(username);
        playerNamePacket.setListPlayerName(username);
        playerNamePacket.setGroupRank((byte) 0);

        broadcastPacket(playerNamePacket);
        return true;
    }

    private void sendServerIdentification() throws IOException {
        ServerIdentificationPacket response = new ServerIdentificationPacket();
        response.setProtocolVersion(server.getProtocolVersion());
        response.setServerName(server.getServerName());
        response.setServerMOTD(server.getServerMotd());
        if (Player.getInstance(this).isOP()) {
            response.setUserType((byte) 0x64);
        } else {
            response.setUserType((byte) 0x00);
        }
        sendPacket(response);
    }

    private void sendLevelData() throws IOException {
        Level level = getCurrentLevel();
        sendLevelInitialize();
        sendCompressedLevelData(level);
        sendLevelFinalize(level);
    }


    private Level getCurrentLevel() {
        // First check if player has a specific level assigned
        Player player = Player.getInstance(this);
        String levelName = server.getLevelManager().getPlayerLevel(player);

        // If no specific level, use the "main" level
        if (levelName == null) {
            levelName = "main";
            // Set the player's initial level
            server.getLevelManager().setPlayerLevel(player, levelName);
        }

        return server.getLevelManager().getLevel(levelName);
    }


    private void sendLevelInitialize() throws IOException {
        sendPacket(new LevelInitializePacket());
    }

    private void sendCompressedLevelData(Level level) throws IOException {
        try {
            byte[] levelData = level.getBlockData();
            byte[] compressedData = compressLevelData(levelData);
            int chunkSize = 1024;
            int totalChunks = (compressedData.length + chunkSize - 1) / chunkSize;

            for (int i = 0, chunkIndex = 0; i < compressedData.length; i += chunkSize, chunkIndex++) {
                try {
                    LevelDataChunkPacket chunkPacket = new LevelDataChunkPacket();
                    int remainingBytes = Math.min(chunkSize, compressedData.length - i);
                    chunkPacket.setChunkLength((short) remainingBytes);

                    byte[] chunkData = new byte[chunkSize];
                    System.arraycopy(compressedData, i, chunkData, 0, remainingBytes);

                    chunkPacket.setChunkData(chunkData);
                    chunkPacket.setPercentComplete((byte) ((chunkIndex + 1) * 100 / totalChunks));

                    sendPacket(chunkPacket);
                } catch (SocketException e) {
                    System.err.println("Socket error during level transmission for " + username + ": " + e.getMessage());
                    throw e;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to send level data to " + username + ": " + e.getMessage());
            throw e;
        }
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

    private void sendLevelFinalize(Level level) throws IOException {
        LevelFinalizePacket finalizePacket = new LevelFinalizePacket();
        finalizePacket.setXSize(level.getWidth());
        finalizePacket.setYSize(level.getHeight());
        finalizePacket.setZSize(level.getDepth());
        try {
            sendPacket(finalizePacket);
            System.out.println("Level data sent successfully to " + username + ". Dimensions: " +
                    level.getWidth() + "x" + level.getHeight() + "x" + level.getDepth());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Player spawning methods
    private void spawnPlayer() throws IOException {
        Level level = getCurrentLevel();
        x = (short) (level.getWidth() / 2 * 32);
        y = (short) ((level.getHeight() / 2 * 32) + 51);
        z = (short) (level.getDepth() / 2 * 32);
        yaw = 0;
        pitch = 0;

        SpawnPlayerPacket spawnPacket = new SpawnPlayerPacket();
        spawnPacket.setPlayerId((byte) -1);
        spawnPacket.setPlayerName(username);
        spawnPacket.setX(x);
        spawnPacket.setY(y);
        spawnPacket.setZ(z);
        spawnPacket.setYaw(yaw);
        spawnPacket.setPitch(pitch);
        sendPacket(spawnPacket);

        ServerPositionPacket positionPacket = new ServerPositionPacket();
        positionPacket.setPlayerId((byte) -1);
        positionPacket.setX(x);
        positionPacket.setY(y);
        positionPacket.setZ(z);
        positionPacket.setYaw(yaw);
        positionPacket.setPitch(pitch);
        sendPacket(positionPacket);
    }

    private void broadcastSpawn() throws IOException {
        String myLevel = server.getLevelManager().getPlayerLevel(Player.getInstance(this));

        // Only broadcast to and receive broadcasts from players in the same level
        for (ClientHandler client : getClientsInLevel(myLevel)) {
            if (client != this && client.socket.isConnected()) {
                sendSpawnPacket(client, this);
                sendSpawnPacket(this, client);
            }
        }
    }


    private void sendSpawnPacket(ClientHandler receiver, ClientHandler playerToSpawn) throws IOException {
        SpawnPlayerPacket spawnPacket = new SpawnPlayerPacket();
        spawnPacket.setPlayerId(playerToSpawn.playerId);
        spawnPacket.setPlayerName(playerToSpawn.username);
        spawnPacket.setX(playerToSpawn.x);
        spawnPacket.setY(playerToSpawn.y);
        spawnPacket.setZ(playerToSpawn.z);
        spawnPacket.setYaw(playerToSpawn.yaw);
        spawnPacket.setPitch(playerToSpawn.pitch);

        // Send the packet to the receiver
        receiver.sendPacket(spawnPacket);
    }

    private void gameLoop() {
        System.out.println("Entering game loop for player: " + username);
        try {
            while (socket.isConnected()) {
                byte packetId = readPacketId();
                PacketType packetType = PacketType.fromId(packetId);

                switch (packetType) {
                    case POSITION_ORIENTATION:
                        handleClientPosition();
                        break;
                    case SET_BLOCK:
                        handleSetBlock();
                        break;
                    case MESSAGE:
                        handleMessage();
                        break;
                    default:
                        System.out.println("Unhandled packet type from " + username + ": " + packetType);
                }
            }
        } catch (EOFException e) {
            System.out.println("Client disconnected: " + username);
        } catch (IOException e) {
            System.out.println("IO error in game loop for " + username + ": " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Unexpected error in game loop for " + username + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("Exiting game loop for player: " + username);
        }
    }

    private void handleClientPosition() throws IOException {
        ClientPositionPacket packet = new ClientPositionPacket();
        readPacket(packet);

        if (isValidPosition(packet.getX(), packet.getY(), packet.getZ())) {
            updatePlayerPosition(packet);
            broadcastPositionUpdate();
        } else {
            sendPositionCorrection();
        }
    }

    private void updatePlayerPosition(ClientPositionPacket packet) {
        x = packet.getX();
        y = packet.getY();
        z = packet.getZ();
        yaw = packet.getYaw();
        pitch = packet.getPitch();
    }

    private void broadcastPositionUpdate() throws IOException {
        String levelName = server.getLevelManager().getPlayerLevel(Player.getInstance(this));
        ServerPositionPacket updatePacket = new ServerPositionPacket();
        updatePacket.setPlayerId(playerId);
        updatePacket.setX(x);
        updatePacket.setY(y);
        updatePacket.setZ(z);
        updatePacket.setYaw(yaw);
        updatePacket.setPitch(pitch);

        broadcastPacketToLevelExcept(updatePacket, levelName, this);
    }


    private void sendPositionCorrection() throws IOException {
        ServerPositionPacket correctPacket = new ServerPositionPacket();
        correctPacket.setPlayerId(playerId);
        correctPacket.setX(x);
        correctPacket.setY(y);
        correctPacket.setZ(z);
        correctPacket.setYaw(yaw);
        correctPacket.setPitch(pitch);
        sendPacket(correctPacket);
        //System.out.println("Sent correction packet to " + username);
    }

    private void handleMessage() throws IOException {
        MessagePacket packet = new MessagePacket();
        readPacket(packet);

        if (packet.getMessage().startsWith("/")) {
            API.getInstance().getCommandRegistry().executeCommand(Player.getInstance(this), packet.getMessage().substring(1));
            return;
        }

        String levelName = server.getLevelManager().getPlayerLevel(Player.getInstance(this));
        MessagePacket broadcastPacket = new MessagePacket();
        broadcastPacket.setPlayerId(playerId);
        broadcastPacket.setMessage(username + ":" + packet.getMessage());

        // Only broadcast chat messages to players in the same level
        broadcastPacketToLevel(broadcastPacket, levelName);
    }

    private boolean isValidPosition(short newX, short newY, short newZ) {
        Level level = getCurrentLevel();
        int blockX = newX / 32;
        int blockY = newY / 32;
        int blockZ = newZ / 32;

        return blockX >= 0 && blockX < level.getWidth() &&
                blockY >= 0 && blockY < level.getHeight() &&
                blockZ >= 0 && blockZ < level.getDepth();
    }


    private boolean isValidBlockChange(SetBlockClientPacket packet) {
        Level level = getCurrentLevel();
        if (packet.getX() < 0 || packet.getX() >= level.getWidth() ||
                packet.getY() < 0 || packet.getY() >= level.getHeight() ||
                packet.getZ() < 0 || packet.getZ() >= level.getDepth()) {
            System.out.println("Invalid block coordinates: " + packet.getX() + ", " + packet.getY() + ", " + packet.getZ());
            return false;
        }

        if (packet.getMode() == null) {
            return false;
        }

        BlockType blockType = packet.getBlockType();
        return blockType != null;
    }

    // Disconnection and cleanup
    public void disconnectPlayer(String reason) {
        System.out.println("Disconnecting player: " + (username != null ? username : "unknown") + " (Reason: " + reason + ")");
        try {
            if (socket != null && !socket.isClosed()) {
                DisconnectPlayerPacket disconnectPacket = new DisconnectPlayerPacket();
                disconnectPacket.setReason(reason);
                sendPacket(disconnectPacket);
                broadcastPacket(new ExtRemovePlayerNamePacket(this.playerId));
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error while disconnecting player: " + e.getMessage());
        } finally {
            if (playerId != -1) {
                clients.remove(playerId);
                try {
                    broadcastDespawn();
                } catch (IOException e) {
                    System.out.println("Error while broadcasting despawn: " + e.getMessage());
                }
                Player.removeFromCache(this);
            }
        }
    }

    private void broadcastDespawn() throws IOException {
        String levelName = server.getLevelManager().getPlayerLevel(Player.getInstance(this));
        DespawnPlayerPacket despawnPacket = new DespawnPlayerPacket();
        despawnPacket.setPlayerId(playerId);

        broadcastPacketToLevelExcept(despawnPacket, levelName, this);
    }

    private void handleSetBlock() {
        try {
            SetBlockClientPacket packet = new SetBlockClientPacket();
            readPacket(packet);
            Player player = Player.getInstance(this);
            Level currentLevel = getCurrentLevel();
            Location blockLocation = Location.fromBlockCoordinates(packet.getX(), packet.getY(), packet.getZ());

            if (isValidBlockChange(packet)) {
                BlockType currentBlockType = BlockType.getById(currentLevel.getBlock(packet.getX(), packet.getY(), packet.getZ()));

                if (packet.getMode().isDestroy()) {  // Destroy block
                    PlayerBreakBlockEvent breakEvent = new PlayerBreakBlockEvent(player, blockLocation, currentBlockType);
                    EventRegistry.callEvent(breakEvent);

                    if (!breakEvent.isCancelled()) {
                        currentLevel.setBlock(packet.getX(), packet.getY(), packet.getZ(), BlockType.AIR);
                        broadcastBlockChange(packet.getX(), packet.getY(), packet.getZ(), BlockType.AIR);
                    }
                } else if (packet.getMode().isPlace()) {  // Create block
                    PlayerPlaceBlockEvent placeEvent = new PlayerPlaceBlockEvent(player, blockLocation, packet.getBlockType());
                    EventRegistry.callEvent(placeEvent);

                    if (!placeEvent.isCancelled()) {
                        currentLevel.setBlock(packet.getX(), packet.getY(), packet.getZ(), placeEvent.getBlockType());
                        broadcastBlockChange(packet.getX(), packet.getY(), packet.getZ(), placeEvent.getBlockType());
                    }
                }
            } else {
                BlockType currentBlockType = BlockType.getById(currentLevel.getBlock(packet.getX(), packet.getY(), packet.getZ()));
                sendBlockCorrection(packet.getX(), packet.getY(), packet.getZ(), currentBlockType);
            }
        } catch (IOException e) {
            System.out.println("Error handling SET_BLOCK from " + username + ": " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Unexpected error handling SET_BLOCK from " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void broadcastBlockChange(short x, short y, short z, BlockType blockType) {
        String levelName = server.getLevelManager().getPlayerLevel(Player.getInstance(this));
        SetBlockServerPacket broadcastPacket = new SetBlockServerPacket();
        broadcastPacket.setX(x);
        broadcastPacket.setY(y);
        broadcastPacket.setZ(z);
        broadcastPacket.setBlockType(blockType.getId());

        broadcastPacketToLevel(broadcastPacket, levelName);
    }


    private void sendBlockCorrection(short x, short y, short z, BlockType blockType) {
        try {
            SetBlockServerPacket correctionPacket = new SetBlockServerPacket();
            correctionPacket.setX(x);
            correctionPacket.setY(y);
            correctionPacket.setZ(z);
            correctionPacket.setBlockType(blockType.getId());
            sendPacket(correctionPacket);
        } catch (IOException e) {
            System.out.println("Error sending block correction to " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Getters (if needed for external access)
    public String getUsername() {
        return username;
    }

    public byte getPlayerId() {
        return playerId;
    }

    public short getX() {
        return x;
    }

    public short getY() {
        return y;
    }

    public short getZ() {
        return z;
    }

    public byte getYaw() {
        return yaw;
    }

    public byte getPitch() {
        return pitch;
    }
}