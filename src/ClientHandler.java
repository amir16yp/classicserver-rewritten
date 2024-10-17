import classic.level.LevelGenerator;
import classic.packets.*;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;
    private byte playerId;
    private short x = 5;
    private short y = 30;
    private short z =5;
    private byte yaw, pitch;
    private static final Map<Byte, ClientHandler> clients = new ConcurrentHashMap<>();
    private static byte nextPlayerId = 0;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.playerId = nextPlayerId++;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // Wait for and handle the initial player identification
            byte firstPacketId = in.readByte();
            if (firstPacketId == PacketType.PLAYER_IDENTIFICATION.getId()) {
                handlePlayerIdentification();
            } else {
                System.out.println("Unexpected first packet: " + firstPacketId);
                disconnectPlayer("Invalid initial packet");
                return;
            }

            if (socket.isConnected()) {
                clients.put(playerId, this);
                sendLevelData();
                spawnPlayer();
                broadcastSpawn();
                gameLoop();
            }
        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
            e.printStackTrace();
        } finally {
            disconnectPlayer("Connection closed");
        }
    }
    private void sendLevelData() throws IOException {
        System.out.println("Sending level data...");

        // Send level initialize packet
        LevelInitializePacket initPacket = new LevelInitializePacket();
        initPacket.write(out);

        // Prepare and compress level data
        byte[] levelData = MinecraftClassicServer.level.getBlockData();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(levelData.length);
        dos.write(levelData);
        dos.flush();

        ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(compressedBaos)) {
            gzipOut.write(baos.toByteArray());
        }
        byte[] compressedData = compressedBaos.toByteArray();

        // Send level data chunks
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
                    " (" + chunkPacket.getPercentComplete() + "% complete)");
        }

        // Send level finalize packet
        LevelFinalizePacket finalizePacket = new LevelFinalizePacket();
        finalizePacket.setXSize(LevelGenerator.getWidth());
        finalizePacket.setYSize(LevelGenerator.getHeight());
        finalizePacket.setZSize(LevelGenerator.getDepth());
        finalizePacket.write(out);

        System.out.println("classic.level.Level data sent successfully. Dimensions: " +
                LevelGenerator.getWidth() + "x" +
                LevelGenerator.getHeight() + "x" +
                LevelGenerator.getDepth());
    }

    private void sendServerIdentification() throws IOException {
        ServerIdentificationPacket response = new ServerIdentificationPacket();
        response.setProtocolVersion(MinecraftClassicServer.PROTOCOL_VERSION);
        response.setServerName(MinecraftClassicServer.SERVER_NAME);
        response.setServerMOTD(MinecraftClassicServer.SERVER_MOTD);
        response.setUserType((byte) 0x00); // Set to 0x64 for op, 0x00 for normal user
        response.write(out);
    }


    private void handleSetBlock() throws IOException {
        SetBlockPacket packet = new SetBlockPacket();
        packet.read(in);

        System.out.println("Received SET_BLOCK from " + username + ": x=" + packet.getX() +
                ", y=" + packet.getY() + ", z=" + packet.getZ() +
                ", mode=" + packet.getMode() + ", blockType=" + packet.getBlockType());

        // Validate the block change
        if (isValidBlockChange(packet)) {
            // Update the level data
            byte oldBlockType = MinecraftClassicServer.level.getBlock(packet.getX(), packet.getY(), packet.getZ());
            MinecraftClassicServer.level.setBlock(packet.getX(), packet.getY(), packet.getZ(), packet.getBlockType());

            // Broadcast the block change to all players, EXCEPT the sender
            for (ClientHandler client : new ArrayList<>(clients.values())) {
                if (client != this && client.socket.isConnected()) {
                    try {
                        packet.write(client.out);
                        client.out.flush(); // Ensure the packet is sent immediately
                    } catch (IOException e) {
                        System.out.println("Failed to send SET_BLOCK to " + client.username + ": " + e.getMessage());
                    }
                }
            }
        } else {
            System.out.println("Invalid block change from " + username);
            // Send a correction packet back to the client with the current block type
            byte currentBlockType = MinecraftClassicServer.level.getBlock(packet.getX(), packet.getY(), packet.getZ());
            SetBlockPacket correctionPacket = new SetBlockPacket();
            correctionPacket.setX(packet.getX());
            correctionPacket.setY(packet.getY());
            correctionPacket.setZ(packet.getZ());
            correctionPacket.setMode((byte) 1); // Set mode to 1 for placement
            correctionPacket.setBlockType(currentBlockType);
            correctionPacket.write(out);
            out.flush(); // Ensure the correction packet is sent immediately
        }
    }

    private boolean isValidBlockChange(SetBlockPacket packet) {
        // Check if the coordinates are within the world bounds
        if (packet.getX() < 0 || packet.getX() >= LevelGenerator.getWidth() ||
                packet.getY() < 0 || packet.getY() >= LevelGenerator.getHeight() ||
                packet.getZ() < 0 || packet.getZ() >= LevelGenerator.getDepth()) {
            System.out.println("Invalid block coordinates: " + packet.getX() + ", " + packet.getY() + ", " + packet.getZ());
            return false;
        }

        // Check if the mode is valid (0 for destroy, 1 for create)
        if (packet.getMode() != 0 && packet.getMode() != 1) {
            System.out.println("Invalid block change mode: " + packet.getMode());
            return false;
        }

        // Check if the block type is valid (you may need to define valid block types)
        if (packet.getBlockType() < 0 || packet.getBlockType() > 49) { // Assuming 0-49 are valid block types
            System.out.println("Invalid block type: " + packet.getBlockType());
            return false;
        }

        // Add any additional checks you need (e.g., player permissions, distance from player, etc.)

        return true;
    }



    private boolean verifyPlayerKey(String username, String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String toHash = MinecraftClassicServer.SERVER_SALT + username;
            byte[] hashBytes = md.digest(toHash.getBytes());

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            String expectedKey = sb.toString();

            return expectedKey.equals(key);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void spawnPlayer() throws IOException {
        // Set initial player position
        x = (short) (LevelGenerator.getWidth() * 16); // Multiply by 16 instead of 32 for fixed-point
        y = (short) ((LevelGenerator.getHeight() * 16) + 51); // Add player height offset
        z = (short) (LevelGenerator.getDepth() * 16);
        yaw = 0;
        pitch = 0;

        ServerPositionPacket spawnPacket = new ServerPositionPacket();
        spawnPacket.setPlayerId(playerId);
        spawnPacket.setX(x);
        spawnPacket.setY(y);
        spawnPacket.setZ(z);
        spawnPacket.setYaw(yaw);
        spawnPacket.setPitch(pitch);
        spawnPacket.write(out);
    }

    private void broadcastSpawn() throws IOException {
        SpawnPlayerPacket spawnPacket = new SpawnPlayerPacket();
        spawnPacket.setPlayerId(playerId);
        spawnPacket.setPlayerName(username);
        spawnPacket.setX(x);
        spawnPacket.setY(y);
        spawnPacket.setZ(z);
        spawnPacket.setYaw(yaw);
        spawnPacket.setPitch(pitch);

        for (ClientHandler client : clients.values()) {
            if (client != this) {
                spawnPacket.write(client.out);

                // Spawn existing players for the new player
                SpawnPlayerPacket existingPlayerPacket = new SpawnPlayerPacket();
                existingPlayerPacket.setPlayerId(client.playerId);
                existingPlayerPacket.setPlayerName(client.username);
                existingPlayerPacket.setX(client.x);
                existingPlayerPacket.setY(client.y);
                existingPlayerPacket.setZ(client.z);
                existingPlayerPacket.setYaw(client.yaw);
                existingPlayerPacket.setPitch(client.pitch);
                existingPlayerPacket.write(out);
            }
        }
    }
    private void gameLoop() {
        System.out.println("Entering game loop for player: " + username);
        try {
            while (socket.isConnected()) {
                byte packetId = in.readByte();
                PacketType packetType = PacketType.fromId(packetId);
                System.out.println("Received packet from " + username + ": " + packetType + " (ID: " + packetId + ")");

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
                        // Optionally, read and discard the packet data to maintain synchronization
                        // This depends on knowing the structure of each packet type
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

    private void handlePlayerIdentification() throws IOException {
        PlayerIdentificationPacket packet = new PlayerIdentificationPacket();
        packet.read(in);

        System.out.println("Player identification received for: " + packet.getUsername() +
                " (Protocol version: " + packet.getProtocolVersion() + ")");

        if (packet.getProtocolVersion() != MinecraftClassicServer.PROTOCOL_VERSION) {
            System.out.println("Protocol version mismatch. Expected: " +
                    MinecraftClassicServer.PROTOCOL_VERSION +
                    ", Received: " + packet.getProtocolVersion());
            disconnectPlayer("Incompatible protocol version");
            return;
        }

        this.username = packet.getUsername();

        // Send server identification
        sendServerIdentification();

        // Remove these lines from here
        // sendLevelData();
        // spawnPlayer();
        // broadcastSpawn();
    }

    private void handleClientPosition() throws IOException {
        ClientPositionPacket packet = new ClientPositionPacket();
        packet.read(in);

        System.out.println("Received position update from " + username +
                ": x=" + packet.getX() + ", y=" + packet.getY() + ", z=" + packet.getZ());

        // Basic position validation
        if (isValidPosition(packet.getX(), packet.getY(), packet.getZ())) {
            // Update player position
            x = packet.getX();
            y = packet.getY();
            z = packet.getZ();
            yaw = packet.getYaw();
            pitch = packet.getPitch();

            // Broadcast the position update to other players
            broadcastPositionUpdate();
        } else {
            System.out.println("Invalid position received from " + username +
                    ": x=" + packet.getX() + ", y=" + packet.getY() + ", z=" + packet.getZ());
            // If the position is invalid, send the client back to their last valid position
            ServerPositionPacket correctPacket = new ServerPositionPacket();
            correctPacket.setPlayerId(playerId);
            correctPacket.setX(x);
            correctPacket.setY(y);
            correctPacket.setZ(z);
            correctPacket.setYaw(yaw);
            correctPacket.setPitch(pitch);
            correctPacket.write(out);
            System.out.println("Sent correction packet to " + username);
        }
    }

    private void handleBlockUpdates() throws IOException, InterruptedException {
        // Check if there are any block updates needed based on the player's new position
        List<SetBlockPacket> updates = getBlockUpdatesForPosition(x, y, z);

        // Send block updates with a small delay
        for (SetBlockPacket update : updates) {
            Thread.sleep(50); // Add a small delay between block updates
            update.write(out);
        }
    }

    private List<SetBlockPacket> getBlockUpdatesForPosition(short x, short y, short z) {
        // Implement logic to determine if any blocks need to be updated
        // based on the player's position. Return a list of SetBlockPackets.
        // This is just a placeholder implementation
        return new ArrayList<>();
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
                    System.out.println("Sent position update to player: " + client.username);
                } catch (IOException e) {
                    System.out.println("Failed to send position update to " + client.username + ": " + e.getMessage());
                    // Consider disconnecting the client here if needed
                }
            }
        }
    }
    private void handleMessage() throws IOException {
        MessagePacket packet = new MessagePacket();
        packet.read(in);

        // Broadcast the message to all players
        MessagePacket broadcastPacket = new MessagePacket();
        broadcastPacket.setPlayerId(playerId);
        broadcastPacket.setMessage(username + ":" + packet.getMessage());

        for (ClientHandler client : clients.values()) {
            broadcastPacket.write(client.out);
        }
    }

    private void disconnectPlayer(String reason) {
        System.out.println("Disconnecting player: " + username + " (Reason: " + reason + ")");
        try {
            if (socket != null && !socket.isClosed()) {
                DisconnectPlayerPacket disconnectPacket = new DisconnectPlayerPacket();
                disconnectPacket.setReason(reason);
                disconnectPacket.write(out);
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error while disconnecting player: " + e.getMessage());
        } finally {
            clients.remove(playerId);
            try {
                broadcastDespawn();
            } catch (IOException e) {
                System.out.println("Error while broadcasting despawn: " + e.getMessage());
            }
        }
    }

    private void broadcastDespawn() throws IOException {
        DespawnPlayerPacket despawnPacket = new DespawnPlayerPacket();
        despawnPacket.setPlayerId(playerId);

        for (ClientHandler client : clients.values()) {
            despawnPacket.write(client.out);
        }
    }

    private boolean isValidPosition(short newX, short newY, short newZ) {
        // Implement your position validation logic here
        // This is a basic example, you might want to add more sophisticated checks
        return newX >= 0 && newX < LevelGenerator.getWidth() * 32 &&
                newY >= 0 && newY < LevelGenerator.getHeight() * 32 &&
                newZ >= 0 && newZ < LevelGenerator.getDepth() * 32;
    }
}