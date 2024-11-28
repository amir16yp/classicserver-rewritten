package classic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class PlayerList {
    private final Set<String> players;
    private final String filename;
    private final String listType;

    public PlayerList(String listType, String filename) {
        this.players = new HashSet<>();
        this.listType = listType;
        this.filename = filename;
        loadFromFile();
    }

    public boolean add(String playerName) {
        boolean added = players.add(playerName.toLowerCase());
        if (added) {
            saveToFile();
            System.out.println("Added " + playerName + " to " + listType + " list");
        }
        return added;
    }

    public boolean remove(String playerName) {
        boolean removed = players.remove(playerName.toLowerCase());
        if (removed) {
            saveToFile();
            System.out.println("Removed " + playerName + " from " + listType + " list");
        }
        return removed;
    }

    public boolean contains(String playerName) {
        return players.contains(playerName.toLowerCase());
    }

    public Set<String> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    public void clear() {
        players.clear();
        saveToFile();
        System.out.println("Cleared " + listType + " list");
    }

    private void loadFromFile() {
        try {
            Path path = Paths.get(filename);
            if (Files.exists(path)) {
                players.clear();
                List<String> lines = Files.readAllLines(path);
                for (String line : lines) {
                    String trimmed = line.trim().toLowerCase();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        players.add(trimmed);
                    }
                }
                System.out.println("Loaded " + players.size() + " players from " + listType + " list");
            }
        } catch (IOException e) {
            System.out.println("Error loading " + listType + " list: " + e.getMessage());
        }
    }

    private void saveToFile() {
        try {
            List<String> lines = new ArrayList<>(players);
            Collections.sort(lines); // Save in alphabetical order
            Files.write(Paths.get(filename), lines);
        } catch (IOException e) {
            System.out.println("Error saving " + listType + " list: " + e.getMessage());
        }
    }

    public void reload() {
        loadFromFile();
    }

    public int size() {
        return players.size();
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }
}