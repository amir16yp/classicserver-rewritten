package classic;

import classic.packets.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

public class ClientHandler implements Runnable {
    // Constants
    private static final byte EXPECTED_PROTOCOL_VERSION = 7;

    // Client-specific fields
    private final Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;
    private byte playerId;
    private short x, y, z;
    private byte yaw, pitch;

    // Shared resources
    private static final Map<Byte, ClientHandler> clients = new ConcurrentHashMap<>();
    private static byte nextPlayerId = 0;

    // Constructor
    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.playerId = getNextPlayerId();
        System.out.println("New client connected. Assigned player ID: " + playerId);
    }

    public static int getClientCount() {
        return clients.size();
    }

    // Main run method
    @Override
    public void run() {
        try {
            setupStreams();
            if (handlePlayerIdentification()) {
                sendLevelData();
                spawnPlayer();
                broadcastSpawn();
                clients.put(playerId, this);
                gameLoop();
            }
        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
            e.printStackTrace();
            MinecraftClassicServer.playerDisconnected(this.socket);
        } finally {
            disconnectPlayer("Connection closed");
        }
    }

    // Setup and initialization methods
    private void setupStreams() throws IOException {
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    private boolean handlePlayerIdentification() throws IOException {
        byte firstPacketId = in.readByte();
        if (firstPacketId != PacketType.PLAYER_IDENTIFICATION.getId()) {
            System.out.println("Unexpected first packet: " + firstPacketId);
            disconnectPlayer("Invalid initial packet");
            return false;
        }

        PlayerIdentificationPacket packet = new PlayerIdentificationPacket();
        packet.read(in);

        System.out.println("Player identification received for: " + packet.getUsername() +
                " (Protocol version: " + packet.getProtocolVersion() + ")");

        if (packet.getProtocolVersion() != EXPECTED_PROTOCOL_VERSION) {
            System.out.println("Protocol version mismatch. Expected: " + EXPECTED_PROTOCOL_VERSION +
                    ", Received: " + packet.getProtocolVersion());
            disconnectPlayer("Incompatible protocol version");
            return false;
        }

        this.username = packet.getUsername();
        sendServerIdentification();
        return true;
    }

    private void sendServerIdentification() throws IOException {
        ServerIdentificationPacket response = new ServerIdentificationPacket();
        response.setProtocolVersion(EXPECTED_PROTOCOL_VERSION);
        response.setServerName(MinecraftClassicServer.SERVER_NAME);
        response.setServerMOTD(MinecraftClassicServer.SERVER_MOTD);
        response.setUserType((byte) 0x00); // Set to 0x64 for op, 0x00 for normal user
        response.write(out);
        out.flush();
        System.out.println("Sent server identification to " + username);
    }

    // Level data methods
    private void sendLevelData() throws IOException {
        System.out.println("Sending level data to " + username + "...");
        sendLevelInitialize();
        sendCompressedLevelData();
        sendLevelFinalize();
    }

    private void sendLevelInitialize() throws IOException {
        new LevelInitializePacket().write(out);
    }

    private void sendCompressedLevelData() throws IOException {
        byte[] levelData = MinecraftClassicServer.level.getBlockData();
        byte[] compressedData = compressLevelData(levelData);

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
            chunkPacket.write(out);

            System.out.println("Sent chunk " + (chunkIndex + 1) + " of " + totalChunks +
                    " (" + chunkPacket.getPercentComplete() + "% complete) to " + username);
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

    private void sendLevelFinalize() throws IOException {
        LevelFinalizePacket finalizePacket = new LevelFinalizePacket();
        finalizePacket.setXSize(MinecraftClassicServer.level.getWidth());
        finalizePacket.setYSize(MinecraftClassicServer.level.getHeight());
        finalizePacket.setZSize(MinecraftClassicServer.level.getDepth());
        finalizePacket.write(out);

        System.out.println("Level data sent successfully to " + username + ". Dimensions: " +
                MinecraftClassicServer.level.getWidth()+ "x" + MinecraftClassicServer.level.getHeight()+ "x" + MinecraftClassicServer.level.getDepth());
    }

    // Player spawning methods
    private void spawnPlayer() throws IOException {
        x = (short) (MinecraftClassicServer.level.getWidth() / 2 * 32);
        y = (short) ((MinecraftClassicServer.level.getHeight() / 2 * 32) + 51);
        z = (short) (MinecraftClassicServer.level.getDepth() / 2 * 32);
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
        spawnPacket.write(out);

        ServerPositionPacket positionPacket = new ServerPositionPacket();
        positionPacket.setPlayerId((byte) -1);
        positionPacket.setX(x);
        positionPacket.setY(y);
        positionPacket.setZ(z);
        positionPacket.setYaw(yaw);
        positionPacket.setPitch(pitch);
        positionPacket.write(out);

        //System.out.println("Spawned player " + username + " (ID: " + playerId + ") at x=" + (x/32.0) + ", y=" + (y/32.0) + ", z=" + (z/32.0));
    }

    private void broadcastSpawn() throws IOException {
        for (ClientHandler client : new ArrayList<>(clients.values())) {
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
        spawnPacket.write(receiver.out);
        //System.out.println("Sent spawn packet for " + playerToSpawn.username + " (ID: " + playerToSpawn.playerId + ") to " + receiver.username + " (ID: " + receiver.playerId + ")");
    }

    // Game loop and packet handling
    private void gameLoop() {
        System.out.println("Entering game loop for player: " + username);
        try {
            while (socket.isConnected()) {
                byte packetId = in.readByte();
                PacketType packetType = PacketType.fromId(packetId);
                //System.out.println("Received packet from " + username + ": " + packetType + " (ID: " + packetId + ")");

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
        packet.read(in);

        //System.out.println("Received position update from " + username +
//                ": x=" + packet.getX() + ", y=" + packet.getY() + ", z=" + packet.getZ());

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
        ServerPositionPacket updatePacket = new ServerPositionPacket();
        updatePacket.setPlayerId(playerId);
        updatePacket.setX(x);
        updatePacket.setY(y);
        updatePacket.setZ(z);
        updatePacket.setYaw(yaw);
        updatePacket.setPitch(pitch);

        for (ClientHandler client : new ArrayList<>(clients.values())) {
            if (client != this && client.socket.isConnected()) {
                try {
                    updatePacket.write(client.out);
                    //System.out.println("Sent position update for " + username + " (ID: " + playerId + ") to " + client.username + " (ID: " + client.playerId + ")");
                } catch (IOException e) {
                    System.out.println("Failed to send position update to " + client.username + ": " + e.getMessage());
                }
            }
        }
    }

    private void sendPositionCorrection() throws IOException {
        ServerPositionPacket correctPacket = new ServerPositionPacket();
        correctPacket.setPlayerId(playerId);
        correctPacket.setX(x);
        correctPacket.setY(y);
        correctPacket.setZ(z);
        correctPacket.setYaw(yaw);
        correctPacket.setPitch(pitch);
        correctPacket.write(out);
        //System.out.println("Sent correction packet to " + username);
    }

    private void handleMessage() throws IOException {
        MessagePacket packet = new MessagePacket();
        packet.read(in);

        MessagePacket broadcastPacket = new MessagePacket();
        broadcastPacket.setPlayerId(playerId);
        broadcastPacket.setMessage(username + ": " + packet.getMessage());

        for (ClientHandler client : clients.values()) {
            broadcastPacket.write(client.out);
        }
    }

    // Utility methods
    private static synchronized byte getNextPlayerId() {
        byte id = nextPlayerId++;
        if (nextPlayerId < 0) nextPlayerId = 0;
        return id;
    }

    private boolean isValidPosition(short newX, short newY, short newZ) {
        int blockX = newX / 32;
        int blockY = newY / 32;
        int blockZ = newZ / 32;

        return blockX >= 0 && blockX < MinecraftClassicServer.level.getWidth() &&
                blockY >= 0 && blockY < MinecraftClassicServer.level.getHeight() &&
                blockZ >= 0 && blockZ < MinecraftClassicServer.level.getDepth();
    }

    private boolean isValidBlockChange(SetBlockClientPacket packet) {
        if (packet.getX() < 0 || packet.getX() >= MinecraftClassicServer.level.getWidth() ||
                packet.getY() < 0 || packet.getY() >= MinecraftClassicServer.level.getHeight() ||
                packet.getZ() < 0 || packet.getZ() >= MinecraftClassicServer.level.getDepth()) {
            System.out.println("Invalid block coordinates: " + packet.getX() + ", " + packet.getY() + ", " + packet.getZ());
            return false;
        }

        if (packet.getMode() != 0 && packet.getMode() != 1) {
            //System.out.println("Invalid block change mode: " + packet.getMode());
            return false;
        }

        if (packet.getBlockType() < 0 || packet.getBlockType() > 49) {
            //System.out.println("Invalid block type: " + packet.getBlockType());
            return false;
        }

        return true;
    }

    private boolean isValidPacket(Packet packet) {
        // Add specific checks for each packet type
        if (packet instanceof SetBlockServerPacket) {
            SetBlockServerPacket sbPacket = (SetBlockServerPacket) packet;
            return sbPacket.getX() >= 0 && sbPacket.getX() < MinecraftClassicServer.level.getWidth() &&
                    sbPacket.getY() >= 0 && sbPacket.getY() < MinecraftClassicServer.level.getHeight() &&
                    sbPacket.getZ() >= 0 && sbPacket.getZ() < MinecraftClassicServer.level.getDepth() &&
                    sbPacket.getZ() >= 0 && sbPacket.getZ() < MinecraftClassicServer.level.getDepth() &&
                    sbPacket.getBlockType() >= 0 && sbPacket.getBlockType() <= 49;
        }
        // Add more checks for other packet types
        return true;
    }
    // Disconnection and cleanup
    public void disconnectPlayer(String reason) {
        System.out.println("Disconnecting player: " + (username != null ? username : "unknown") + " (Reason: " + reason + ")");
        try {
            if (socket != null && !socket.isClosed())
            {
                DisconnectPlayerPacket disconnectPacket = new DisconnectPlayerPacket();
                disconnectPacket.setReason(reason);
                disconnectPacket.write(out);
                out.flush();
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
            }
        }
    }

    private void broadcastDespawn() throws IOException {
        DespawnPlayerPacket despawnPacket = new DespawnPlayerPacket();
        despawnPacket.setPlayerId(playerId);

        for (ClientHandler client : new ArrayList<>(clients.values())) {
            if (client != this && client.socket.isConnected()) {
                try {
                    despawnPacket.write(client.out);
                    //System.out.println("Sent despawn packet for " + username + " (ID: " + playerId + ") to " + client.username + " (ID: " + client.playerId + ")");
                } catch (IOException e) {
                    System.out.println("Failed to send despawn packet to " + client.username + ": " + e.getMessage());
                }
            }
        }
    }

    private void handleSetBlock() {
        try {
            SetBlockClientPacket packet = new SetBlockClientPacket();
            packet.read(in);

            //System.out.println("Received SET_BLOCK from " + username + ": " + packet);

            if (isValidBlockChange(packet)) {
                byte currentBlockType = MinecraftClassicServer.getBlock(packet.getX(), packet.getY(), packet.getZ());

                if (packet.getMode() == 0x00) {  // Destroy block
                    MinecraftClassicServer.setBlock(packet.getX(), packet.getY(), packet.getZ(), (byte) 0);
                    broadcastBlockChange(packet.getX(), packet.getY(), packet.getZ(), (byte) 0);
                } else if (packet.getMode() == 0x01) {  // Create block
                    MinecraftClassicServer.setBlock(packet.getX(), packet.getY(), packet.getZ(), packet.getBlockType());
                    broadcastBlockChange(packet.getX(), packet.getY(), packet.getZ(), packet.getBlockType());
                }
            } else {
                byte currentBlockType = MinecraftClassicServer.getBlock(packet.getX(), packet.getY(), packet.getZ());
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


    public void broadcastBlockChange(short x, short y, short z, byte blockType) {
        SetBlockServerPacket broadcastPacket = new SetBlockServerPacket();
        broadcastPacket.setX(x);
        broadcastPacket.setY(y);
        broadcastPacket.setZ(z);
        broadcastPacket.setBlockType(blockType);

        for (ClientHandler client : new ArrayList<>(clients.values())) {
            try {
                broadcastPacket.write(client.out);
                client.out.flush();
                //System.out.println("Sent SET_BLOCK to " + client.username + ": x=" + x + ", y=" + y + ", z=" + z + ", blockType=" + blockType);
            } catch (IOException e) {
                System.out.println("Failed to send SET_BLOCK to " + client.username + ": " + e.getMessage());
            }
        }
    }

    private void sendBlockCorrection(short x, short y, short z, byte blockType) {
        try {
            SetBlockServerPacket correctionPacket = new SetBlockServerPacket();
            correctionPacket.setX(x);
            correctionPacket.setY(y);
            correctionPacket.setZ(z);
            correctionPacket.setBlockType(blockType);
            correctionPacket.write(out);
            out.flush();
            //System.out.println("Sent block correction to " + username + ": " + correctionPacket);
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

    public DataOutputStream getOutputStream() {
        return out;
    }

    public DataInputStream getInputStream() {
        return in;
    }

    public void setOutputStream(DataOutputStream outputStream) {
        this.out = outputStream;
    }
}