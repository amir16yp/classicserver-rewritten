package net.classicube;

import net.classicube.api.API;
import net.classicube.api.CommandSender;
import net.classicube.api.ConsoleCommandSender;
import net.classicube.api.PluginLoader;
import net.classicube.level.FlatLevelGenerator;
import net.classicube.level.Level;
import net.classicube.level.LevelGenerator;
import net.classicube.level.LevelManager;
import net.classicube.packets.DisconnectPlayerPacket;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

public class MinecraftClassicServer {

    public static final String APP_NAME = "JavaCCRewritten";

    private static final String LEVEL_FILE = "world.dat";
    private static final long SAVE_INTERVAL = 5 * 60 * 1000; // 5 minutes in milliseconds
    public static boolean ENABLE_HEARTBEAT;  // Set from config
    private final int port;
    private final byte protocolVersion;
    private final String serverName;
    private final String serverMotd;
    private final int maxPlayers;
    private final ServerSocket serverSocket;
    private final Object levelLock;
    private final Timer autoSaveTimer;
    private final Config config;
    private final PlayerList banList = new PlayerList("ban", "banlist.txt");
    private final PlayerList opList = new PlayerList("admin", "oplist.txt");
    private final LevelManager levelManager;
    private boolean verifyPlayers;      // Set from config
    private boolean isRunning;
    private HeartbeatManager heartbeatManager;

    public MinecraftClassicServer() throws IOException {
        this.config = new Config();
        this.config.loadConfig();
        this.levelManager = new LevelManager();
        this.port = config.getPort();
        this.protocolVersion = 0x07;
        this.serverName = config.getServerName();
        this.serverMotd = config.getServerMotd();
        this.maxPlayers = config.getMaxPlayers();
        this.verifyPlayers = config.isVerifyPlayers();
        ENABLE_HEARTBEAT = config.isEnableHeartbeat();
        this.serverSocket = new ServerSocket(port);
        this.levelLock = new Object();
        this.isRunning = false;
        this.autoSaveTimer = new Timer("LevelAutoSave", true);

        if (ENABLE_HEARTBEAT) {
            this.heartbeatManager = new HeartbeatManager(this);
            this.heartbeatManager.start();
        }

        // Load all existing levels
        loadAllLevels();

        // Create main level if it doesn't exist
        if (!levelManager.levelExists("main")) {
            System.out.println("Creating new main level...");
            levelManager.createLevel("main",
                    (short) config.getLevelWidth(),
                    (short) config.getLevelHeight(),
                    (short) config.getLevelLength());
        }
        API.initializeAPI(this);
    }

    public static void main(String[] args) throws IOException {
        MinecraftClassicServer server = new MinecraftClassicServer();
        server.start();
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    private void loadAllLevels() {
        Path levelsPath = Paths.get("levels");
        if (!Files.exists(levelsPath)) {
            try {
                Files.createDirectories(levelsPath);
            } catch (IOException e) {
                System.err.println("Failed to create levels directory: " + e.getMessage());
                return;
            }
        }

        try {
            Files.list(levelsPath)
                    .filter(path -> path.toString().toLowerCase().endsWith(".dat"))
                    .forEach(path -> {
                        String levelName = path.getFileName().toString();
                        levelName = levelName.substring(0, levelName.length() - 4); // Remove .dat

                        try {
                            levelManager.loadLevel(levelName);
                        } catch (IOException e) {
                            System.err.println("Failed to load level " + levelName + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error scanning levels directory: " + e.getMessage());
        }
    }

    public boolean isVerifyPlayers() {
        return verifyPlayers;
    }

    private void startCommandReader() {
        CommandSender consoleCommandSender = new ConsoleCommandSender();
        Thread commandThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (isRunning) {
                if (scanner.hasNextLine()) {
                    String command = scanner.nextLine().trim();
                    API.getInstance().getCommandRegistry().executeCommand(consoleCommandSender, command);
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

    public void start() {
        isRunning = true;
        System.out.println("Minecraft Classic server running on port " + port);
        System.out.println("Player verification is " + (verifyPlayers ? "enabled" : "disabled"));
        System.out.println("Heartbeat is " + (ENABLE_HEARTBEAT ? "enabled" : "disabled"));
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


    private void setupAutoSave() {
        autoSaveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                saveLevel();
            }
        }, SAVE_INTERVAL, SAVE_INTERVAL);
    }

    public void saveLevel() {
        levelManager.saveAllLevels();
    }

    public void stop() {
        System.out.println("Stopping server...");
        isRunning = false;
        autoSaveTimer.cancel();

        if (heartbeatManager != null) {
            heartbeatManager.stop();
        }
        API.getInstance().getPluginLoader().disablePlugins();
        System.out.println("Saving level before shutdown...");
        saveLevel();

        try {
            serverSocket.close();
        } catch (IOException e) {
            System.out.println("Error closing server socket: " + e.getMessage());
        }

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

    public void updateConfigurationFromReload() {
        config.loadConfig();
        this.verifyPlayers = config.isVerifyPlayers();
        ENABLE_HEARTBEAT = config.isEnableHeartbeat();
        if (ENABLE_HEARTBEAT && heartbeatManager == null) {
            heartbeatManager = new HeartbeatManager(this);
            heartbeatManager.start();
        } else if (!ENABLE_HEARTBEAT && heartbeatManager != null) {
            heartbeatManager.stop();
            heartbeatManager = null;
        }
        PluginLoader pluginLoader = API.getInstance().getPluginLoader();
        pluginLoader.disablePlugins();
        pluginLoader.loadPlugins("plugins");
        pluginLoader.enablePlugins();
    }
}