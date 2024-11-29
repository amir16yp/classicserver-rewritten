package net.classicube.api;

import net.classicube.ClientHandler;
import net.classicube.MinecraftClassicServer;
import net.classicube.packets.MessagePacket;

import java.util.ArrayList;
import java.util.List;

public class API {

    private static final int MAX_MESSAGE_LENGTH = 64;

    private static API instance;
    private static boolean initialized = false;
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
        // Admin commands (require op)
        /*
        String[] array = {"Skip", "Hello", "World", "Java", "Programming"};
String result =
System.out.println(result);
         */
        commandRegistry.registerCommand("stop", true, (sender, args) -> {
            server.stop();
            return "Server stopping...";
        });

        commandRegistry.registerCommand("say", true, (sender, args) -> {
            API.getInstance().broadcastMessage(ChatColors.RED + "[SERVER] " + ChatColors.YELLOW + String.join(" ", args));
            return "sent message";
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

    public PluginLoader getPluginLoader() {
        return pluginLoader;
    }
}
