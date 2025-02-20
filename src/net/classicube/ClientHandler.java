package net.classicube;

import net.classicube.api.API;
import net.classicube.api.Location;
import net.classicube.api.Player;
import net.classicube.api.enums.BlockType;
import net.classicube.api.event.*;
import net.classicube.level.Level;
import net.classicube.packets.*;
import net.classicube.packets.cpe.CPEPacket;
import net.classicube.packets.cpe.ExtAddPlayerNamePacket;
import net.classicube.packets.cpe.ExtRemovePlayerNamePacket;

import javax.swing.text.AbstractDocument;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPOutputStream;

public class ClientHandler implements Runnable, AutoCloseable {
    private static final int PACKET_BUFFER_SIZE = 8192;
    private static final int MAX_MESSAGE_LENGTH = 64;
    public static final ConcurrentHashMap<Byte, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final PlayerIDManager idManager = new PlayerIDManager();

    private enum ClientState {
        CONNECTING, IDENTIFYING, ACTIVE, DISCONNECTING, DISCONNECTED
    }

    protected final Socket socket;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final ReentrantLock readLock = new ReentrantLock();
    private final MinecraftClassicServer server;
    private final byte playerId;
    private final Map<PacketType, PacketHandler> packetHandlers;
    private final AtomicReference<ClientState> state = new AtomicReference<>(ClientState.CONNECTING);

    protected DataInputStream in;
    protected DataOutputStream out;
    protected boolean supportsCPE;
    private String username;
    private short x, y, z;
    private byte yaw, pitch;

    public ClientHandler(Socket socket, MinecraftClassicServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        try {
            this.playerId = idManager.getNextAvailableId();
            socket.setTcpNoDelay(true);
            setupStreams();

            this.packetHandlers = initializePacketHandlers();
            System.out.println("New client connected. Assigned player ID: " + playerId);
        } catch (PlayerIDManager.NoAvailableIDException e) {
            throw new IOException("Server is full - maximum players reached");
        }
    }

    protected void setupStreams() throws IOException {
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    private Map<PacketType, PacketHandler> initializePacketHandlers() {
        Map<PacketType, PacketHandler> handlers = new EnumMap<>(PacketType.class);
        handlers.put(PacketType.POSITION_ORIENTATION, this::handleClientPosition);
        handlers.put(PacketType.SET_BLOCK, this::handleSetBlock);
        handlers.put(PacketType.MESSAGE, this::handleMessage);
        return Collections.unmodifiableMap(handlers);
    }

    public static void broadcastPacket(Packet packet) {
        for (ClientHandler client : getClients()) {
            if (client.socket.isConnected()) {
                try {
                    client.sendPacket(packet);
                } catch (Exception e) {
                    System.out.println("ERROR SENDING PACKET TO " + client + " " + e.getMessage());
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
                }
            }
        }
    }

    public static Collection<ClientHandler> getClientsInLevel(String levelName) {
        return getClients().stream()
                .filter(client -> levelName.equals(client.server.getLevelManager().getPlayerLevel(Player.getInstance(client))))
                .collect(java.util.stream.Collectors.toList());
    }

    public static void broadcastPacketToLevel(Packet packet, String levelName) {
        getClientsInLevel(levelName).stream()
                .filter(client -> client.socket.isConnected())
                .forEach(client -> {
                    try {
                        client.sendPacket(packet);
                    } catch (Exception e) {
                        System.out.println("ERROR SENDING PACKET TO " + client + " " + e.getMessage());
                    }
                });
    }

    public static void broadcastPacketToLevelExcept(Packet packet, String levelName, ClientHandler except) {
        getClientsInLevel(levelName).stream()
                .filter(client -> client.socket.isConnected() && client != except)
                .forEach(client -> {
                    try {
                        client.sendPacket(packet);
                    } catch (Exception e) {
                        System.out.println("ERROR SENDING PACKET TO " + client + " " + e.getMessage());
                    }
                });
    }

    public static ClientHandler getByName(String username) {
        return getClients().stream()
                .filter(client -> username.equals(client.getUsername()))
                .findFirst()
                .orElse(null);
    }

    public static ClientHandler getByNameCaseInsensitive(String username) {
        return getClients().stream()
                .filter(client -> username.equalsIgnoreCase(client.getUsername()))
                .findFirst()
                .orElse(null);
    }

    public static int getClientCount() {
        return clients.size();
    }

    public static Collection<ClientHandler> getClients() {
        return clients.values();
    }

    @Override
    public String toString() {
        return String.format("<ID: %d NAME: %s IP: %s>",
                playerId, username, this.socket.getInetAddress().getHostAddress());
    }

    public Socket getSocket() {
        return socket;
    }

    public void sendPacket(Packet packet) throws IOException {
        if (state.get() == ClientState.DISCONNECTED) {
            return;
        }

        writeLock.lock();
        try {
            if (packet instanceof CPEPacket && !this.supportsCPE) {
                return;
            }
            packet.write(out);
            out.flush();
        } finally {
            writeLock.unlock();
        }
    }


    public void readPacket(Packet packet) throws IOException {
        readLock.lock();
        try {
            packet.read(in);
        } finally {
            readLock.unlock();
        }
    }

    public byte readPacketId() throws IOException {
        readLock.lock();
        try {
            return in.readByte();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void run() {
        try {
            if (handlePlayerIdentification()) {
                state.set(ClientState.ACTIVE);
                sendLevelData();
                spawnPlayer();
                broadcastSpawn();
                clients.put(playerId, this);
                EventRegistry.callEvent(new PlayerJoinEvent(Player.getInstance(this)));
                gameLoop();
            }
        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
        } finally {
            disconnectPlayer("Connection closed");
        }
    }

    private void gameLoop() {
        try {
            while (state.get() == ClientState.ACTIVE && socket.isConnected()) {
                byte packetId = readPacketId();
                PacketType packetType = PacketType.fromId(packetId);

                PacketHandler handler = packetHandlers.get(packetType);
                if (handler != null) {
                    handler.handle();
                } else {
                    System.out.println("Unhandled packet type from " + username + ": " + packetType);
                }
            }
        } catch (EOFException e) {
            handleDisconnect("Client disconnected normally");
        } catch (IOException e) {
            handleDisconnect("Connection error: " + e.getMessage());
        } catch (Exception e) {
            handleDisconnect("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleDisconnect(String reason) {
        disconnectPlayer(reason);
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

        if (getByNameCaseInsensitive(username) != null) {
            disconnectPlayer("A player with that name is already online!");
            return false;
        }

        if (!validatePlayer(packet)) {
            return false;
        }

        sendServerIdentification();
        sendPlayerNamePacket();
        return true;
    }

    private boolean validatePlayer(PlayerIdentificationPacket packet) {
        if (server.isVerifyPlayers() && !server.verifyPlayer(username, packet.getVerificationKey())) {
            if (this instanceof WebSocketClientHandler) {
                if (!isValidWebGuest()) {
                    disconnectPlayer("Name verification failed!");
                    return false;
                }
            } else {
                disconnectPlayer("Name verification failed!");
                return false;
            }
        }

        if (server.getBanList().contains(username)) {
            disconnectPlayer("You are banned!");
            return false;
        }

        return true;
    }

    private boolean isValidWebGuest() {
        return server.getConfig().isEnableWebGuests() &&
                username.startsWith("[Guest]") &&
                username.length() > 7 &&
                username.substring(7).matches("\\d+");
    }

    private void sendPlayerNamePacket() throws IOException {
        ExtAddPlayerNamePacket playerNamePacket = new ExtAddPlayerNamePacket();
        playerNamePacket.setGroupName("players");
        playerNamePacket.setNameID(this.playerId);
        playerNamePacket.setAutocompletePlayerName(username);
        playerNamePacket.setListPlayerName(username);
        playerNamePacket.setGroupRank((byte) 0);
        broadcastPacket(playerNamePacket);
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

    private Level getCurrentLevel()
    {
        return API.getInstance().getServer().getLevelManager().getLevel("main");
    }

    private void sendLevelData() throws IOException {
        Level level = getCurrentLevel();
        sendLevelInitialize();
        sendCompressedLevelData(level);
        sendLevelFinalize(level);
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
        //dos.flush();

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

    private void handleClientPosition() throws IOException {
        ClientPositionPacket packet = new ClientPositionPacket();
        readPacket(packet);

        short newX = packet.getX();
        short newY = packet.getY();
        short newZ = packet.getZ();

        if (!isValidPosition(newX, newY, newZ)) {
            sendPositionCorrection();
            return;
        }

        updateAndBroadcastPosition(packet);
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
    }

    private void updateAndBroadcastPosition(ClientPositionPacket packet) throws IOException {
        synchronized (this) {
            x = packet.getX();
            y = packet.getY();
            z = packet.getZ();
            yaw = packet.getYaw();
            pitch = packet.getPitch();
        }

        String levelName = server.getLevelManager().getPlayerLevel(Player.getInstance(this));
        ServerPositionPacket updatePacket = createPositionUpdatePacket();
        broadcastPacketToLevelExcept(updatePacket, levelName, this);
    }

    private ServerPositionPacket createPositionUpdatePacket() {
        ServerPositionPacket packet = new ServerPositionPacket();
        packet.setPlayerId(playerId);
        packet.setX(x);
        packet.setY(y);
        packet.setZ(z);
        packet.setYaw(yaw);
        packet.setPitch(pitch);
        return packet;
    }

    private void handleMessage() throws IOException {
        MessagePacket packet = new MessagePacket();
        readPacket(packet);

        String message = packet.getMessage();
        if (message == null || message.length() > MAX_MESSAGE_LENGTH) {
            return;
        }

        if (message.startsWith("/")) {
            API.getInstance().getCommandRegistry().executeCommand(
                    Player.getInstance(this), message.substring(1));
            return;
        }

        String levelName = server.getLevelManager().getPlayerLevel(Player.getInstance(this));
        MessagePacket broadcastPacket = new MessagePacket();
        broadcastPacket.setPlayerId(playerId);
        broadcastPacket.setMessage(username + ":" + message);
        broadcastPacketToLevel(broadcastPacket, levelName);
    }

    private void handleSetBlock() {
        try {
            SetBlockClientPacket packet = new SetBlockClientPacket();
            readPacket(packet);

            if (!isValidBlockChange(packet)) {
                sendCurrentBlockState(packet);
                return;
            }

            Player player = Player.getInstance(this);
            Level currentLevel = getCurrentLevel();
            Location blockLocation = Location.fromBlockCoordinates(
                    packet.getX(), packet.getY(), packet.getZ());
            BlockType currentBlockType = BlockType.getById(
                    currentLevel.getBlock(packet.getX(), packet.getY(), packet.getZ()));

            if (packet.getMode().isDestroy()) {
                handleBlockDestruction(player, blockLocation, currentBlockType, packet);
            } else if (packet.getMode().isPlace()) {
                handleBlockPlacement(player, blockLocation, packet);
            }
        } catch (Exception e) {
            System.out.println("Error handling SET_BLOCK from " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleBlockDestruction(Player player, Location location,
                                        BlockType currentType, SetBlockClientPacket packet) throws IOException {
        PlayerBreakBlockEvent breakEvent = new PlayerBreakBlockEvent(player, location, currentType);
        EventRegistry.callEvent(breakEvent);

        if (!breakEvent.isCancelled()) {
            getCurrentLevel().setBlock(packet.getX(), packet.getY(), packet.getZ(), BlockType.AIR);
            broadcastBlockChange(packet.getX(), packet.getY(), packet.getZ(), BlockType.AIR);
        }
    }

    private void handleBlockPlacement(Player player, Location location,
                                      SetBlockClientPacket packet) throws IOException {
        PlayerPlaceBlockEvent placeEvent = new PlayerPlaceBlockEvent(player, location, packet.getBlockType());
        EventRegistry.callEvent(placeEvent);

        if (!placeEvent.isCancelled()) {
            getCurrentLevel().setBlock(packet.getX(), packet.getY(), packet.getZ(), placeEvent.getBlockType());
            broadcastBlockChange(packet.getX(), packet.getY(), packet.getZ(), placeEvent.getBlockType());
        }
    }

    private void sendCurrentBlockState(SetBlockClientPacket packet) {
        try {
            BlockType currentBlockType = BlockType.getById(
                    getCurrentLevel().getBlock(packet.getX(), packet.getY(), packet.getZ()));
            sendBlockCorrection(packet.getX(), packet.getY(), packet.getZ(), currentBlockType);
        } catch (IOException e) {
            System.out.println("Error sending block correction to " + username + ": " + e.getMessage());
        }
    }

    @Override
    public void close() {
        disconnectPlayer("Server shutting down");
    }

    public void disconnectPlayer(String reason) {
        if (!state.compareAndSet(ClientState.ACTIVE, ClientState.DISCONNECTING)) {
            return;
        }

        System.out.println("Disconnecting player: " + (username != null ? username : "unknown") +
                " (Reason: " + reason + ")");

        try {
            sendDisconnectPacket(reason);
        } finally {
            cleanup();
        }
    }

    private void sendDisconnectPacket(String reason) {
        try {
            if (socket != null && !socket.isClosed()) {
                DisconnectPlayerPacket disconnectPacket = new DisconnectPlayerPacket();
                disconnectPacket.setReason(reason);
                sendPacket(disconnectPacket);
                broadcastPacket(new ExtRemovePlayerNamePacket(this.playerId));
            }
        } catch (IOException e) {
            System.out.println("Error sending disconnect packet: " + e.getMessage());
        }
    }

    private void cleanup() {
        try {
            if (playerId != -1) {
                clients.remove(playerId);
                idManager.releaseId(playerId);
                broadcastDespawn();
                Player.removeFromCache(this);
            }

            closeResources();
        } finally {
            state.set(ClientState.DISCONNECTED);
        }
    }

    protected void broadcastDespawn() {
        String levelName = server.getLevelManager().getPlayerLevel(Player.getInstance(this));
        DespawnPlayerPacket despawnPacket = new DespawnPlayerPacket();
        despawnPacket.setPlayerId(playerId);

        broadcastPacketToLevelExcept(despawnPacket, levelName, this);
    }

    private void closeResources() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("Error closing resources: " + e.getMessage());
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

    private void sendBlockCorrection(short x, short y, short z, BlockType blockType) throws IOException {
        SetBlockServerPacket correctionPacket = new SetBlockServerPacket();
        correctionPacket.setX(x);
        correctionPacket.setY(y);
        correctionPacket.setZ(z);
        correctionPacket.setBlockType(blockType.getId());
        sendPacket(correctionPacket);
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
        return packet.getX() >= 0 && packet.getX() < level.getWidth() &&
                packet.getY() >= 0 && packet.getY() < level.getHeight() &&
                packet.getZ() >= 0 && packet.getZ() < level.getDepth() &&
                packet.getMode() != null &&
                packet.getBlockType() != null;
    }

    // Getters
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

    @FunctionalInterface
    private interface PacketHandler {
        void handle() throws IOException;
    }
}