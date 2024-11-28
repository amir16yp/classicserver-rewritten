package classic.api;

import classic.ClientHandler;
import classic.packets.MessagePacket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Player implements CommandSender {
    private static final Map<ClientHandler, Player> playerCache = new ConcurrentHashMap<>();
    private final ClientHandler handle;
    private static final int MAX_MESSAGE_LENGTH = 64;

    private Player(ClientHandler handle) {
        this.handle = handle;
    }

    public static Player getInstance(ClientHandler handle) {
        return playerCache.computeIfAbsent(handle, Player::new);
    }

    // Called when a player disconnects to clean up the cache
    public static void removeFromCache(ClientHandler handle) {
        playerCache.remove(handle);
    }

    public String getIPAddress() {
        return handle.getSocket().getInetAddress().getHostAddress();
    }

    public String getUsername() {
        return handle.getUsername();
    }

    public void sendMessage(String message) {
        // Split message into chunks
        for (String chunk : splitMessage(message)) {
            MessagePacket packet = new MessagePacket();
            packet.setMessage(chunk);
            packet.setPlayerId((byte) -1);
            try {
                packet.write(handle.getOutputStream());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private List<String> splitMessage(String message) {
        List<String> chunks = new ArrayList<>();

        // If message is short enough, just return it
        if (message.length() <= MAX_MESSAGE_LENGTH) {
            chunks.add(message);
            return chunks;
        }

        // Split message into words
        String[] words = message.split(" ");
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

        // Add final chunk if there is one
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }

    @Override
    public boolean isOP() {
        return API.getInstance().getServer().getOpList().contains(this.getUsername().toLowerCase());
    }

    public void setOP(boolean op) {
        if (op) {
            API.getInstance().getServer().getOpList().add(this.getUsername().toLowerCase());
        } else {
            API.getInstance().getServer().getOpList().remove(this.getUsername().toLowerCase());
        }
    }

    public void kick(String reason) {
        handle.disconnectPlayer(reason);
    }

    // Get the underlying ClientHandler
    public ClientHandler getHandle() {
        return handle;
    }
}