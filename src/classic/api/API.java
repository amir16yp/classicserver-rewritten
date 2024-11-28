package classic.api;

import classic.ClientHandler;
import classic.MinecraftClassicServer;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.ArrayList;

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
            players.add(new Player(handle));
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
