package classic;

import classic.api.API;
import classic.level.Level;
import classic.level.LevelGenerator;
import classic.packets.DisconnectPlayerPacket;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MinecraftClassicServer {
    private final int port;
    private final byte protocolVersion;
    private final String serverName;
    private final String serverMotd;
    private final boolean verifyPlayers;
    private final int maxPlayers;
    private final Level level;
    private final ServerSocket serverSocket;
    private final Object levelLock;
    private boolean isRunning;

    public MinecraftClassicServer(int port) throws IOException {
        this.port = port;
        this.protocolVersion = 0x07;
        this.serverName = "Java Classic Server";
        this.serverMotd = "Welcome to a basic Minecraft Classic server!";
        this.verifyPlayers = false;
        this.maxPlayers = 20;
        this.level = new LevelGenerator().generateFlatWorld();
        this.serverSocket = new ServerSocket(port);
        this.levelLock = new Object();
        this.isRunning = false;
        new API(this);
    }

    public void start() {
        isRunning = true;
        System.out.println("Minecraft Classic server running on port " + port);
        System.out.println("Player verification is " + (verifyPlayers ? "enabled" : "disabled"));
        System.out.println("Maximum players: " + maxPlayers);

        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleNewConnection(clientSocket);
            } catch (IOException e) {
                if (isRunning) {
                    System.out.println("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        isRunning = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.out.println("Error closing server socket: " + e.getMessage());
        }
    }

    private void handleNewConnection(Socket clientSocket) throws IOException {
        if (ClientHandler.getClientCount() < maxPlayers) {
            new Thread(new ClientHandler(clientSocket, this)).start();
            System.out.println("New player connected. Current player count: " + ClientHandler.getClientCount());
        } else {
            rejectConnection(clientSocket);
        }
    }

    private void rejectConnection(Socket clientSocket) throws IOException {
        DisconnectPlayerPacket disconnectPacket = new DisconnectPlayerPacket();
        disconnectPacket.setReason("Server is full");
        disconnectPacket.write(new DataOutputStream(clientSocket.getOutputStream()));
        System.out.println("Server is full. Rejecting new connection.");
        clientSocket.close();
    }

    public byte getBlock(short x, short y, short z) {
        synchronized (levelLock) {
            return level.getBlock(x, y, z);
        }
    }

    public void setBlock(short x, short y, short z, byte blockType) {
        synchronized (levelLock) {
            level.setBlock(x, y, z, blockType);
        }
    }

    public Level getLevel() {
        return level;
    }

    public String getServerName() {
        return serverName;
    }

    public String getServerMotd() {
        return serverMotd;
    }

    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public boolean isServerFull() {
        return ClientHandler.getClientCount() >= maxPlayers;
    }

    public static void main(String[] args) throws IOException {
        MinecraftClassicServer server = new MinecraftClassicServer(25565);
        server.start();
    }
}