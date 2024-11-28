package classic.api;

import classic.ClientHandler;
import classic.MinecraftClassicServer;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.Map;

public class API {
    private static API instance;
    private static boolean initialized = false;
    private final MinecraftClassicServer server;
    private final CommandRegistry commandRegistry;

    public static API initializeAPI(MinecraftClassicServer server) {
        if (initialized) {
            throw new IllegalStateException("API has already been initialized!");
        }
        synchronized (API.class) {
            if (instance == null) {
                instance = new API(server);
                initialized = true;
            }
        }
        return instance;
    }

    private API(MinecraftClassicServer server) {
        if (instance != null) {
            throw new IllegalStateException("API instance already exists - use initializeAPI()!");
        }
        this.server = server;
        this.commandRegistry = new CommandRegistry();
        registerDefaultCommands();
    }

    public static API getInstance() {
        if (instance == null) {
            throw new IllegalStateException("API has not been initialized! Call initializeAPI() first.");
        }
        return instance;
    }

    public static ArrayList<Player> getPlayers() {
        return getInstance().getOnlinePlayers();
    }

    private ArrayList<Player> getOnlinePlayers() {
        ArrayList<Player> players = new ArrayList<>();
        for (ClientHandler handle : classic.ClientHandler.getClients()) {
            players.add(Player.getInstance(handle));
        }
        return players;
    }

    private void registerDefaultCommands() {
        // Admin commands (require op)
        commandRegistry.registerCommand("stop", true, (sender, args) -> {
            server.stop();
            return "Server stopping...";
        });

        commandRegistry.registerCommand("stats", true, ((sender, args) -> {
            StringBuilder statsBuilder = new StringBuilder();
            Runtime runtime = Runtime.getRuntime();

            // Memory usage in MB
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            long maxMemory = runtime.maxMemory() / 1024 / 1024;

            // Process CPU usage (JVM)
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            double cpuLoad = ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad() * 100;

            // Uptime
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000; // in seconds
            long days = uptime / 86400;
            long hours = (uptime % 86400) / 3600;
            long minutes = (uptime % 3600) / 60;
            long seconds = uptime % 60;

            statsBuilder.append(ChatColors.GOLD).append("Server Stats: ");
            statsBuilder.append(ChatColors.GREEN).append("Memory: ").append(ChatColors.WHITE).append(usedMemory).append("/").append(maxMemory).append("MB ");
            statsBuilder.append(ChatColors.GREEN).append("CPU: ").append(ChatColors.WHITE).append(String.format("%.1f", cpuLoad)).append("% ");
            statsBuilder.append(ChatColors.GREEN).append("Uptime: ").append(ChatColors.WHITE)
                    .append(days > 0 ? days + "d " : "")
                    .append(hours > 0 ? hours + "h " : "")
                    .append(minutes > 0 ? minutes + "m " : "")
                    .append(seconds + "s ");
            statsBuilder.append(ChatColors.GREEN).append("Players: ").append(ChatColors.WHITE)
                    .append(ClientHandler.getClientCount()).append("/")
                    .append(API.getInstance().getServer().getMaxPlayers());

            return statsBuilder.toString();
        }));
        commandRegistry.registerCommand("save", true, (sender, args) -> {
            server.handleCommand("save");
            return "Manually saving level...";
        });

        commandRegistry.registerCommand("op", true, (sender, args) -> {
            if (args.length < 1) {
                return "Usage: /op <player>";
            }
            server.getOpList().add(args[0].toLowerCase());
            return "Added " + args[0].toLowerCase() + " to OP list";
        });

        commandRegistry.registerCommand("deop", true, (sender, args) -> {
            if (args.length < 1) {
                return "Usage: /deop <player>";
            }
            server.getOpList().remove(args[0].toLowerCase());
            return "Removed " + args[0].toLowerCase() + " from OP list";
        });

        // Public commands (no op required)
        commandRegistry.registerCommand("players", false, (sender, args) -> {
            return "Current players: " + getPlayers().size() + "/" + 20;
        });

        commandRegistry.registerCommand("help", false, (sender, args) -> {
            StringBuilder help = new StringBuilder("Available commands:\n");
            boolean isOp = sender.isOP();
            for (Map.Entry<String, RegisteredCommand> entry : commandRegistry.getCommands().entrySet()) {
                if (!entry.getValue().isRequiresOp() || isOp) {
                    help.append("/" + entry.getKey() + (entry.getValue().isRequiresOp() ? " (OP)" : "") + "\n");
                }
            }
            return help.toString();
        });
    }

    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    public MinecraftClassicServer getServer() {
        return server;
    }
}
