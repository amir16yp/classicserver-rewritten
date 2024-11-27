package classic;

import classic.api.API;
import classic.level.Level;
import classic.level.LevelGenerator;
import classic.packets.DisconnectPlayerPacket;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Scanner;

public class MinecraftClassicServer {
    private static final String LEVEL_FILE = "world.dat";
    private static final long SAVE_INTERVAL = 5 * 60 * 1000; // 5 minutes in milliseconds

    private final int port;
    private final byte protocolVersion;
    private final String serverName;
    private final String serverMotd;
    private final boolean verifyPlayers;
    private final int maxPlayers;
    private final Level level;
    private final ServerSocket serverSocket;
    private final Object levelLock;
    private final Timer autoSaveTimer;
    private boolean isRunning;

    public MinecraftClassicServer(int port) throws IOException {
        this.port = port;
        this.protocolVersion = 0x07;
        this.serverName = "Java Classic Server";
        this.serverMotd = "Welcome to a basic Minecraft Classic server!";
        this.verifyPlayers = false;
        this.maxPlayers = 20;
        this.level = loadOrGenerateLevel();
        this.serverSocket = new ServerSocket(port);
        this.levelLock = new Object();
        this.isRunning = false;
        this.autoSaveTimer = new Timer("LevelAutoSave", true);
        new API(this);
    }

    private void startCommandReader() {
        Thread commandThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (isRunning) {
                if (scanner.hasNextLine()) {
                    String command = scanner.nextLine().trim();
                    handleCommand(command);
                }
            }
            scanner.close();
        });
        commandThread.setName("CommandReader");
        commandThread.setDaemon(true);
        commandThread.start();
    }

    private void handleCommand(String command) {
        String[] parts = command.split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "stop":
                System.out.println("Stopping server...");
                stop();
                break;
            case "save":
                System.out.println("Manually saving level...");
                saveLevel();
                break;
            case "players":
                System.out.println("Current players: " + ClientHandler.getClientCount() + "/" + maxPlayers);
                break;
            case "help":
                printHelp();
                break;
            default:
                System.out.println("Unknown command. Type 'help' for available commands.");
                break;
        }
    }

    private void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  help    - Show this help message");
        System.out.println("  stop    - Stop the server");
        System.out.println("  save    - Save the level immediately");
        System.out.println("  players - Show current player count");
    }

    public void start() {
        isRunning = true;
        System.out.println("Minecraft Classic server running on port " + port);
        System.out.println("Player verification is " + (verifyPlayers ? "enabled" : "disabled"));
        System.out.println("Maximum players: " + maxPlayers);
        System.out.println("Type 'help' for available commands");

        // Start auto-save timer
        setupAutoSave();

        // Start command reader
        startCommandReader();

        // Main server loop
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

    // ... rest of the existing methods remain the same ...

    private Level loadOrGenerateLevel() {
        Path levelPath = Paths.get(LEVEL_FILE);
        if (Files.exists(levelPath)) {
            try {
                System.out.println("Loading existing level from " + LEVEL_FILE);
                return Level.loadFromFile(LEVEL_FILE);
            } catch (IOException e) {
                System.out.println("Failed to load level: " + e.getMessage());
                System.out.println("Generating new level instead...");
            }
        }
        return new LevelGenerator().generateFlatWorld();
    }

    private void setupAutoSave() {
        autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                saveLevel();
            }
        }, SAVE_INTERVAL, SAVE_INTERVAL);
    }

    private void saveLevel() {
        synchronized (levelLock) {
            try {
                level.saveToFile(LEVEL_FILE);
                System.out.println("Level auto-saved to " + LEVEL_FILE);
            } catch (IOException e) {
                System.out.println("Failed to auto-save level: " + e.getMessage());
            }
        }
    }

    public void stop() {
        isRunning = false;
        autoSaveTimer.cancel();

        System.out.println("Saving level before shutdown...");
        saveLevel();

        try {
            serverSocket.close();
        } catch (IOException e) {
            System.out.println("Error closing server socket: " + e.getMessage());
        }

        // Exit the program
        System.exit(0);
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