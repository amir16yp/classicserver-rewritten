package classic;

import classic.api.API;
import classic.api.CommandSender;
import classic.api.ConsoleCommandSender;
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
    public static boolean ENABLE_HEARTBEAT = true;  // Static flag to control heartbeat
    private boolean verifyPlayers = true;      // Static flag to control name verification
    private final int port;
    private final byte protocolVersion;
    private final String serverName;
    private final String serverMotd;
    private final int maxPlayers;
    private final Level level;
    private final ServerSocket serverSocket;
    private final Object levelLock;
    private final Timer autoSaveTimer;
    private boolean isRunning;
    private HeartbeatManager heartbeatManager;
    private PlayerList banList = new PlayerList("ban", "banlist.txt");
    private PlayerList opList = new PlayerList("admin", "oplist.txt");

    public boolean isVerifyPlayers() {
        return verifyPlayers;
    }

    public MinecraftClassicServer(int port) throws IOException {
        this.port = port;
        this.protocolVersion = 0x07;
        this.serverName = "Java Classic Server";
        this.serverMotd = "Welcome to a basic Minecraft Classic server!";
        this.maxPlayers = 20;
        this.level = loadOrGenerateLevel();
        this.serverSocket = new ServerSocket(port);
        this.levelLock = new Object();
        this.isRunning = false;
        this.autoSaveTimer = new Timer("LevelAutoSave", true);
        if (ENABLE_HEARTBEAT)
        {
            this.heartbeatManager = new HeartbeatManager(this);
            this.heartbeatManager.start();
        }
        API.initializeAPI(this);
    }

    private void startCommandReader() {
        CommandSender consoleCommandSender = new ConsoleCommandSender();
        Thread commandThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (isRunning) {
                if (scanner.hasNextLine()) {
                    String command = scanner.nextLine().trim();
                    API.getInstance().getCommandRegistry().executeCommand(consoleCommandSender,command);
                }
            }
            scanner.close();
        });
        commandThread.setName("CommandReader");
        commandThread.setDaemon(true);
        commandThread.start();
    }

    public boolean verifyPlayer(String username, String key) {
        if (!this.verifyPlayers || !ENABLE_HEARTBEAT || heartbeatManager == null) {
            return true;  // Skip verification if disabled or heartbeat is disabled
        }

        String salt = heartbeatManager.getSalt();
        String expectedHash = HeartbeatManager.generateMppass(salt, username);
        return expectedHash.equals(key);
    }

    public String handleCommand(String command) {
        String[] parts = command.split("\\s+");
        String cmd = parts[0].toLowerCase();

        String response = "";

        switch (cmd) {
            case "stop":
                stop();
                break;
            case "save":
                response = "Manually saving level...";
                saveLevel();
                break;
            case "players":
                response = "Current players: " + ClientHandler.getClientCount() + "/" + maxPlayers;
                break;
            case "op":
                String toOP = parts[1];
                if (toOP != null)
                {
                    this.getOpList().add(toOP.toLowerCase());
                    response = "added " + toOP.toLowerCase() + " to OP list";
                } else {
                    response = " must specify OP name";
                }
                break;
            case "deop":
                String toDEOP = parts[1];
                if (toDEOP != null)
                {
                    this.getOpList().remove(toDEOP.toLowerCase());
                    response = "removed " + toDEOP.toLowerCase() + " from op list";
                } else {
                    response = " must specify OP name";
                }
                break;
            case "ban":
                // TODO: implement banning/unbanning
                break;
            default:
                response = "Unknown command. Type 'help' for available commands.";
                break;
        }
        return response;
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
        return new LevelGenerator((short) 1024, (short) 64, (short) 1024    ).generateFlatWorld();
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
        System.out.println("Stopping...");
        isRunning = false;
        autoSaveTimer.cancel();
        if (heartbeatManager != null)
        {
            heartbeatManager.stop();
        }
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

    public PlayerList getBanList() {
        return banList;
    }

    public PlayerList getOpList() {
        return opList;
    }

    public int getPort() {
        return port;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }
}