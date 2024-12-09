package net.classicube;

import net.classicube.api.API;
import net.classicube.api.CommandSender;
import net.classicube.api.ConsoleCommandSender;
import net.classicube.api.PluginLoader;
import net.classicube.level.LevelManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

public class MinecraftClassicServer {
    public static final String APP_NAME = "JavaCCRewritten";
    private static final String LEVEL_FILE = "world.dat";
    private static final long SAVE_INTERVAL = 5 * 60 * 1000; // 5 minutes
    public static boolean ENABLE_HEARTBEAT;

    private final int port;
    private final byte protocolVersion;
    private final String serverName;
    private final String serverMotd;
    private final int maxPlayers;
    private final DualProtocolServer dualServer;
    private final Timer autoSaveTimer;
    private final Config config;
    private final PlayerList banList;
    private final PlayerList opList;
    private final LevelManager levelManager;
    private boolean verifyPlayers;
    private volatile boolean isRunning;
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
        this.banList = new PlayerList("ban", "banlist.txt");
        this.opList = new PlayerList("admin", "oplist.txt");
        ENABLE_HEARTBEAT = config.isEnableHeartbeat();
        this.autoSaveTimer = new Timer("LevelAutoSave", true);
        this.isRunning = false;
        setupHeartbeat();
        loadAllLevels();
        createMainLevel();
        API.initializeAPI(this);
        this.dualServer = new DualProtocolServer(this, port);
    }

    public Config getConfig() {
        return config;
    }

    public static void main(String[] args) throws IOException {
        MinecraftClassicServer server = new MinecraftClassicServer();
        server.start();
    }

    private void setupHeartbeat() {
        if (ENABLE_HEARTBEAT) {
            this.heartbeatManager = new HeartbeatManager(this);
            this.heartbeatManager.start();
        }
    }

    private void createMainLevel() throws IOException {
        if (!levelManager.levelExists("main")) {
            System.out.println("Creating new main level...");
            levelManager.createLevel("main",
                    (short) config.getLevelWidth(),
                    (short) config.getLevelHeight(),
                    (short) config.getLevelLength());
        }
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
                        levelName = levelName.substring(0, levelName.length() - 4);
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

    public void start() {
        isRunning = true;
        System.out.println("Minecraft Classic server running on port " + port);
        System.out.println("Player verification is " + (verifyPlayers ? "enabled" : "disabled"));
        System.out.println("Heartbeat is " + (ENABLE_HEARTBEAT ? "enabled" : "disabled"));
        System.out.println("Maximum players: " + maxPlayers);
        System.out.println("Type 'help' for available commands");

        setupAutoSave();
        startCommandReader();

        // Wait for stop command
        while (isRunning) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                if (isRunning) {
                    System.out.println("Server interrupted: " + e.getMessage());
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

    private void startCommandReader() {
        CommandSender console = new ConsoleCommandSender();
        Thread commandThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (isRunning) {
                if (scanner.hasNextLine()) {
                    String command = scanner.nextLine().trim();
                    API.getInstance().getCommandRegistry().executeCommand(console, command);
                }
            }
            scanner.close();
        });
        commandThread.setName("CommandReader");
        commandThread.setDaemon(true);
        commandThread.start();
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

        dualServer.stop();

        System.exit(0);
    }

    public void saveLevel() {
        levelManager.saveAllLevels();
    }

    public boolean verifyPlayer(String username, String key) {
        if (!verifyPlayers) {
            return true;
        }
        if (heartbeatManager == null)
        {
            heartbeatManager = new HeartbeatManager(this);
        }
        String salt = heartbeatManager.getSalt();
        String expectedHash = HeartbeatManager.generateMppass(salt, username);
        return expectedHash.equals(key);
    }

    public void updateConfigurationFromReload() {
        config.loadConfig();
        verifyPlayers = config.isVerifyPlayers();
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

    // Getters
    public LevelManager getLevelManager() {
        return levelManager;
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

    public boolean isVerifyPlayers() {
        return verifyPlayers;
    }
}