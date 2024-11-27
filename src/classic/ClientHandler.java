package classic;

import classic.api.API;
import classic.api.ChatColors;
import classic.api.Player;
import classic.level.Level;
import classic.packets.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

public class ClientHandler implements Runnable {
    private static final ConcurrentHashMap<Byte, ClientHandler> clients = new ConcurrentHashMap<>();
    private static byte nextPlayerId = 0;
    private final Socket socket;
    private final MinecraftClassicServer server;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;
    private byte playerId;
    private short x, y, z;
    private byte yaw, pitch;

    public Socket getSocket() {
        return socket;
    }

    public ClientHandler(Socket socket, MinecraftClassicServer server) {
        this.socket = socket;
        this.server = server;
        this.playerId = getNextPlayerId();
        System.out.println("New client connected. Assigned player ID: " + playerId);
    }

    public static int getClientCount() {
        return clients.values().size();
    }

    public static Collection<ClientHandler> getClients()
    {
        return clients.values();
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
        byte firstPacketId = in.readByte();
        if (firstPacketId != PacketType.PLAYER_IDENTIFICATION.getId()) {
            disconnectPlayer("Invalid initial packet");
            return false;
        }

        PlayerIdentificationPacket packet = new PlayerIdentificationPacket();
        packet.read(in);

        if (packet.getProtocolVersion() != server.getProtocolVersion()) {
            disconnectPlayer("Incompatible protocol version");
            return false;
        }

        username = packet.getUsername();
        sendServerIdentification();
        return true;
    }

    private void sendServerIdentification() throws IOException {
        ServerIdentificationPacket response = new ServerIdentificationPacket();
        response.setProtocolVersion(server.getProtocolVersion());
        response.setServerName(server.getServerName());
        response.setServerMOTD(server.getServerMotd());
        response.setUserType((byte) 0x00);
        response.write(out);
        out.flush();
    }

    private void sendLevelData() throws IOException {
        sendLevelInitialize();
        sendCompressedLevelData();
        sendLevelFinalize();
    }

    private void sendLevelInitialize() throws IOException {
        new LevelInitializePacket().write(out);
    }

    private void sendCompressedLevelData() throws IOException {
        byte[] levelData = server.getLevel().getBlockData();
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

        Level level = this.server.getLevel();

        finalizePacket.setXSize(level.getWidth());
        finalizePacket.setYSize(level.getHeight());
        finalizePacket.setZSize(level.getDepth());
        finalizePacket.write(out);

        System.out.println("Level data sent successfully to " + username + ". Dimensions: " +
                level.getWidth()+ "x" + level.getHeight()+ "x" + level.getDepth());
    }

    // Player spawning methods
    private void spawnPlayer() throws IOException {
        x = (short) (this.server.getLevel().getWidth() / 2 * 32);
        y = (short) ((this.server.getLevel().getHeight() / 2 * 32) + 51);
        z = (short) (this.server.getLevel().getDepth() / 2 * 32);
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

        return blockX >= 0 && blockX < this.server.getLevel().getWidth() &&
                blockY >= 0 && blockY < this.server.getLevel().getHeight() &&
                blockZ >= 0 && blockZ < this.server.getLevel().getDepth();
    }

    private boolean isValidBlockChange(SetBlockClientPacket packet) {
        if (packet.getX() < 0 || packet.getX() >= this.server.getLevel().getWidth() ||
                packet.getY() < 0 || packet.getY() >= this.server.getLevel().getHeight() ||
                packet.getZ() < 0 || packet.getZ() >= this.server.getLevel().getDepth()) {
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
                byte currentBlockType = this.server.getBlock(packet.getX(), packet.getY(), packet.getZ());

                if (packet.getMode() == 0x00) {  // Destroy block
                    this.server.setBlock(packet.getX(), packet.getY(), packet.getZ(), (byte) 0);
                    broadcastBlockChange(packet.getX(), packet.getY(), packet.getZ(), (byte) 0);
                } else if (packet.getMode() == 0x01) {  // Create block
                    this.server.setBlock(packet.getX(), packet.getY(), packet.getZ(), packet.getBlockType());
                    broadcastBlockChange(packet.getX(), packet.getY(), packet.getZ(), packet.getBlockType());
                }
            } else {
                byte currentBlockType = this.server.getBlock(packet.getX(), packet.getY(), packet.getZ());
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