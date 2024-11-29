package net.classicube.api;

import net.classicube.ClientHandler;
import net.classicube.MinecraftClassicServer;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.Map;

public class API {
    private static API instance;
    private static boolean initialized = false;
    private final MinecraftClassicServer server;
    private final CommandRegistry commandRegistry;

    private API(MinecraftClassicServer server) {
        if (instance != null) {
            throw new IllegalStateException("API instance already exists - use initializeAPI()!");
        }
        this.server = server;
        this.commandRegistry = new CommandRegistry();
        registerDefaultCommands();
    }

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
        for (ClientHandler handle : ClientHandler.getClients()) {
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

        commandRegistry.registerCommand("save", true, (sender, args) -> {
            synchronized (server) {
                try {
                    server.saveLevel();
                    return "Manually saving level...";
                } catch (Exception e) {
                    return "Error saving level: " + e.getMessage();
                }
            }
        });

        commandRegistry.registerCommand("reload", true, (sender, args) -> {
            server.updateConfigurationFromReload();
            return "Configuration reloaded";
        });

        commandRegistry.registerCommand("players", false, (sender, args) -> {
            return "Current players: " + ClientHandler.getClientCount() + "/" + server.getMaxPlayers();
        });

        commandRegistry.registerCommand("op", true, (sender, args) -> {
            if (args.length < 1) {
                return "Usage: /op <player>";
            }
            String toOP = args[0].toLowerCase();
            server.getOpList().add(toOP);
            return "Added " + toOP + " to OP list";
        });

        commandRegistry.registerCommand("deop", true, (sender, args) -> {
            if (args.length < 1) {
                return "Usage: /deop <player>";
            }
            String toDEOP = args[0].toLowerCase();
            server.getOpList().remove(toDEOP);
            return "Removed " + toDEOP + " from OP list";
        });

        commandRegistry.registerCommand("ban", true, (sender, args) -> {
            if (args.length < 1) {
                return "Usage: /ban <player>";
            }
            String toBan = args[0].toLowerCase();
            server.getBanList().add(toBan);

            // Disconnect player if they're currently online
            ClientHandler playerHandle = ClientHandler.getByNameCaseInsensitive(toBan);
            if (playerHandle != null) {
                playerHandle.disconnectPlayer("You've been banned");
            }

            return "Added " + toBan + " to ban list";
        });

        commandRegistry.registerCommand("unban", true, (sender, args) -> {
            if (args.length < 1) {
                return "Usage: /unban <player>";
            }
            String toUnban = args[0].toLowerCase();
            server.getBanList().remove(toUnban);
            return "Removed " + toUnban + " from ban list";
        });

        commandRegistry.registerCommand("help", false, (sender, args) -> {
            return "Available commands:\n" +
                    "stop - Stops the server\n" +
                    "save - Saves the world\n" +
                    "reload - Reloads configuration\n" +
                    "players - Shows current player count\n" +
                    "op <player> - Gives operator status\n" +
                    "deop <player> - Removes operator status\n" +
                    "ban <player> - Bans a player\n" +
                    "unban <player> - Unbans a player\n" +
                    "stats - Shows server statistics\n" +
                    "help - Shows this help message";
        });
    }

    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    public MinecraftClassicServer getServer() {
        return server;
    }
}
