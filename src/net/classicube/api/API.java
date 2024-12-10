package net.classicube.api;

import net.classicube.ClientHandler;
import net.classicube.MinecraftClassicServer;
import net.classicube.api.enums.BlockType;
import net.classicube.api.enums.ChatColors;
import net.classicube.api.enums.EnvColorType;
import net.classicube.level.Level;
import net.classicube.level.LevelManager;
import net.classicube.packets.MessagePacket;
import net.classicube.packets.cpe.EnvColorsPacket;
import net.classicube.packets.cpe.MakeSelectionPacket;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class API {

    private static final int MAX_MESSAGE_LENGTH = 64;
    public static boolean initialized = false;
    private static API instance;
    private final MinecraftClassicServer server;
    private final CommandRegistry commandRegistry;
    private final PluginLoader pluginLoader;

    private API(MinecraftClassicServer server) {
        if (instance != null) {
            throw new IllegalStateException("API instance already exists - use initializeAPI()!");
        }
        this.server = server;
        this.commandRegistry = new CommandRegistry();
        this.pluginLoader = new PluginLoader();
        registerDefaultCommands();

    }


    public static List<String> splitMessage(String message) {
        List<String> chunks = new ArrayList<>();

        // If message is short enough, just return it
        if (message.length() <= MAX_MESSAGE_LENGTH) {
            chunks.add(message);
            return chunks;
        }

        // Split message into lines first
        String[] lines = message.split("\n");

        for (String line : lines) {
            // If the line is empty, add it as a separate chunk
            if (line.isEmpty()) {
                chunks.add("");
                continue;
            }

            // Split line into words
            String[] words = line.split(" ");
            StringBuilder currentChunk = new StringBuilder();

            for (String word : words) {
                // If the word alone is longer than max length, split it
                if (word.length() > MAX_MESSAGE_LENGTH) {
                    // First add any existing chunk
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString());
                        currentChunk.setLength(0);
                    }

                    // Split the long word
                    int start = 0;
                    while (start < word.length()) {
                        int end = Math.min(start + MAX_MESSAGE_LENGTH, word.length());
                        chunks.add(word.substring(start, end));
                        start = end;
                    }
                    continue;
                }

                // Check if adding this word would exceed the limit
                if (currentChunk.length() + word.length() + 1 > MAX_MESSAGE_LENGTH) {
                    // Add current chunk to list and start a new one
                    chunks.add(currentChunk.toString());
                    currentChunk.setLength(0);
                    currentChunk.append(word);
                } else {
                    // Add space if not first word in chunk
                    if (currentChunk.length() > 0) {
                        currentChunk.append(" ");
                    }
                    currentChunk.append(word);
                }
            }

            // Add final chunk of the line if there is one
            if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString());
            }
        }

        return chunks;
    }

    public static API initializeAPI(MinecraftClassicServer server) {
        if (initialized) {
            throw new IllegalStateException("API has already been initialized!");
        }
        synchronized (API.class) {
            if (instance == null) {
                instance = new API(server);
                instance.pluginLoader.loadPlugins("plugins");
                instance.pluginLoader.enablePlugins();
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

    public void broadcastMessage(String message) {
        for (String line : splitMessage(message)) {
            MessagePacket linePacket = new MessagePacket();
            linePacket.setMessage(line);
            ClientHandler.broadcastPacket(linePacket);
        }
    }

    private ArrayList<Player> getOnlinePlayers() {
        ArrayList<Player> players = new ArrayList<>();
        for (ClientHandler handle : ClientHandler.getClients()) {
            players.add(Player.getInstance(handle));
        }
        return players;
    }

    private void registerDefaultCommands() {
        commandRegistry.registerCommand("stop", true, (sender, args) -> {
            server.stop();
            return "Server stopping...";
        });

        commandRegistry.registerCommand("say", true, (sender, args) -> {
            API.getInstance().broadcastMessage(ChatColors.RED + "[SERVER] " + ChatColors.YELLOW + String.join(" ", args));
            return "sent message";
        });

        commandRegistry.registerCommand("cpetest:selection", true, ((sender, args) -> {
            if (sender instanceof Player) {
                List<Player> players = getOnlinePlayers();
                BlockSelection selection = new BlockSelection(players.get(0).getLocation(), players.get(1).getLocation(), new Color(255, 0, 0, 128), "test");
                MakeSelectionPacket makeSelectionPacket = new MakeSelectionPacket(selection);
                ClientHandler.broadcastPacket(makeSelectionPacket);
            }

            return "test complete";
        }));

        commandRegistry.registerCommand("sphere", true, ((sender, args) -> {
            if (sender instanceof Player) {
                Level level = ((Player) sender).getLevel();
                Location loc = ((Player) sender).getLocation();
                level.createHollowSphere(loc.getX(), loc.getY(), loc.getZ(), 20, BlockType.GLASS);
                return level.toString();
            }

            return "test complete";
        }));

        commandRegistry.registerCommand("cpetest:envcolor", true, (((sender, args) -> {
            if (sender instanceof Player) {
                ClientHandler clientHandler = ((Player) sender).getHandle();
                clientHandler.sendPacket(new EnvColorsPacket(EnvColorType.SKY_COLOR, Color.RED));
                clientHandler.sendPacket(new EnvColorsPacket(EnvColorType.CLOUD_COLOR, Color.BLACK));
                clientHandler.sendPacket(new EnvColorsPacket(EnvColorType.FOG_COLOR, Color.ORANGE));
            }
            return "test complete";
        })));

        commandRegistry.registerCommand("tp", false, ((sender, args) -> {
            if (sender instanceof Player) {
                if (args.length == 0) {
                    return "must specify a player name";
                }
                Player playerSender = (Player) sender;
                String playerNameToTP = args[0];
                ClientHandler toTPhandle = ClientHandler.getByNameCaseInsensitive(playerNameToTP);
                if (toTPhandle != null) {
                    Player toTP = Player.getInstance(toTPhandle);
                    playerSender.teleport(toTP.getLocation());
                    return "teleported to " + toTP.getUsername();
                } else {
                    return "player not found";
                }
            }
            return "you must be a player for this command";
        }));

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
            StringBuilder helpMessage = new StringBuilder("Available commands:\n");

            // Get all registered commands from the registry
            Map<String, RegisteredCommand> commands = commandRegistry.getCommands();

            // Sort commands alphabetically
            List<String> sortedCommands = new ArrayList<>(commands.keySet());
            Collections.sort(sortedCommands);

            // Build help message
            for (String cmdName : sortedCommands) {
                RegisteredCommand cmd = commands.get(cmdName);
                String opOnly = cmd.isRequiresOp() ? " (OP)" : "";
                helpMessage.append(cmdName).append(opOnly).append("\n");
            }

            return helpMessage.toString();
        });

        commandRegistry.registerCommand("level", false, (sender, args) -> {
            if (args.length < 1) {
                return "Usage: /level <list|goto|create|delete>";
            }

            LevelManager levelManager = server.getLevelManager();

            // Only players can switch levels
            Player player = (sender instanceof Player) ? (Player) sender : null;

            switch (args[0].toLowerCase()) {
                case "list":
                    StringBuilder levels = new StringBuilder("Available levels: ");
                    for (String name : levelManager.getLevelNames()) {
                        int playerCount = ClientHandler.getClientsInLevel(name).size();
                        levels.append(name).append(" (").append(playerCount).append(" players), ");
                    }
                    if (player != null) {
                        levels.append("\nYou are in: ").append(levelManager.getPlayerLevel(player));
                    }
                    return levels.toString();

                case "goto":
                    if (player == null) {
                        return "Only players can switch levels";
                    }
                    if (args.length < 2) {
                        return "Usage: /level goto <levelname>";
                    }
                    String targetLevel = args[1];
                    if (!levelManager.levelExists(targetLevel)) {
                        return "Level does not exist: " + targetLevel;
                    }

                    String currentLevel = levelManager.getPlayerLevel(player);
                    if (currentLevel.equals(targetLevel)) {
                        return "You are already in that level!";
                    }

                    if (levelManager.switchPlayerLevel(player, targetLevel)) {
                        return "Successfully switched to level: " + targetLevel;
                    } else {
                        return "Failed to switch to level: " + targetLevel;
                    }

                case "create":
                    if (!sender.isOP()) {
                        return "Only operators can create levels";
                    }
                    if (args.length < 5) {
                        return "Usage: /level create <name> <width> <height> <depth>";
                    }
                    try {
                        String name = args[1];
                        if (levelManager.levelExists(name)) {
                            return "Level already exists: " + name;
                        }
                        short width = Short.parseShort(args[2]);
                        short height = Short.parseShort(args[3]);
                        short depth = Short.parseShort(args[4]);

                        // Validate dimensions
                        if (width < 16 || height < 16 || depth < 16) {
                            return "Dimensions must be at least 16";
                        }
                        if (width > 1024 || height > 1024 || depth > 1024) {
                            return "Dimensions must not exceed 1024";
                        }

                        levelManager.createLevel(name, width, height, depth);
                        return "Created new level: " + name + " (" + width + "x" + height + "x" + depth + ")";
                    } catch (NumberFormatException e) {
                        return "Invalid dimensions. Must be numbers between 16 and 1024";
                    } catch (IOException e) {
                        return "Failed to create level: " + e.getMessage();
                    }

                case "delete":
                    if (!sender.isOP()) {
                        return "Only operators can delete levels";
                    }
                    if (args.length < 2) {
                        return "Usage: /level delete <name>";
                    }
                    String levelToDelete = args[1];
                    if (!levelManager.levelExists(levelToDelete)) {
                        return "Level does not exist: " + levelToDelete;
                    }
                    if (levelToDelete.equals("main")) {
                        return "Cannot delete the main level";
                    }
                    try {
                        if (!ClientHandler.getClientsInLevel(levelToDelete).isEmpty()) {
                            return "Cannot delete level while players are in it";
                        }
                        levelManager.deleteLevel(levelToDelete);
                        return "Deleted level: " + levelToDelete;
                    } catch (IOException e) {
                        return "Failed to delete level: " + e.getMessage();
                    }

                case "info":
                    if (args.length < 2) {
                        if (player == null) {
                            return "Usage: /level info <name>";
                        }
                        // If no level specified and sender is player, show current level
                        String currentPlayerLevel = levelManager.getPlayerLevel(player);
                        return levelManager.getLevel(currentPlayerLevel).toString();
                    }
                    String levelName = args[1];
                    if (!levelManager.levelExists(levelName)) {
                        return "Level does not exist: " + levelName;
                    }
                    return levelManager.getLevel(levelName).toString();

                default:
                    return "Unknown subcommand. Available: list, goto, create, delete, info";
            }
        });
    }

    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    public MinecraftClassicServer getServer() {
        return server;
    }

    public PluginLoader getPluginLoader() {
        return pluginLoader;
    }
}
