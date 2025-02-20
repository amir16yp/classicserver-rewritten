package net.classicube;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private static final String CONFIG_FILE = "server.properties";
    private final Properties properties;
    // Default values
    private int port = 25565;
    private String serverName = "Java Classic Server";
    private String serverMotd = "Welcome to a basic Minecraft Classic server!";
    private int maxPlayers = 20;
    private boolean enableHeartbeat = true;
    private boolean verifyPlayers = true;
    private boolean enableWebGuests = true;
    private String webGuestDomain = "";
    private String tempAdminPass = "";
    private int levelWidth = 1024;
    private int levelHeight = 64;
    private int levelLength = 1024;
    public Config() {
        this.properties = new Properties();
    }

    public boolean isEnableWebGuests() {
        return enableWebGuests;
    }

    public String getWebGuestDomain() {
        return webGuestDomain;
    }

    public void loadConfig() {
        File configFile = new File(CONFIG_FILE);

        if (!configFile.exists()) {
            saveDefaultConfig();
        }

        try (FileInputStream fis = new FileInputStream(configFile)) {
            properties.load(fis);

            // Load values from properties file
            port = Integer.parseInt(properties.getProperty("port", String.valueOf(port)));
            serverName = properties.getProperty("server-name", serverName);
            serverMotd = properties.getProperty("motd", serverMotd);
            maxPlayers = Integer.parseInt(properties.getProperty("max-players", String.valueOf(maxPlayers)));
            enableHeartbeat = Boolean.parseBoolean(properties.getProperty("enable-heartbeat", String.valueOf(enableHeartbeat)));
            verifyPlayers = Boolean.parseBoolean(properties.getProperty("verify-players", String.valueOf(verifyPlayers)));
            enableWebGuests = Boolean.parseBoolean(properties.getProperty("enable-web-guest", String.valueOf(enableWebGuests)));
            webGuestDomain = String.valueOf(properties.getProperty("web-domain", String.valueOf(webGuestDomain)));
            levelWidth = Integer.parseInt(properties.getProperty("level-width", String.valueOf(levelWidth)));
            levelHeight = Integer.parseInt(properties.getProperty("level-height", String.valueOf(levelHeight)));
            levelLength = Integer.parseInt(properties.getProperty("level-length", String.valueOf(levelLength)));
            tempAdminPass = String.valueOf(properties.getProperty("tempadminpass", String.valueOf(tempAdminPass)));
        } catch (IOException e) {
            System.out.println("Failed to load config file: " + e.getMessage());
            System.out.println("Using default values");
        }
    }

    public void saveDefaultConfig() {
        properties.setProperty("port", String.valueOf(port));
        properties.setProperty("server-name", serverName);
        properties.setProperty("motd", serverMotd);
        properties.setProperty("max-players", String.valueOf(maxPlayers));
        properties.setProperty("enable-heartbeat", String.valueOf(enableHeartbeat));
        properties.setProperty("verify-players", String.valueOf(verifyPlayers));
        properties.setProperty("enable-web-guest", String.valueOf(enableWebGuests));
        properties.setProperty("web-domain", String.valueOf(webGuestDomain));
        properties.setProperty("level-width", String.valueOf(levelWidth));
        properties.setProperty("level-height", String.valueOf(levelHeight));
        properties.setProperty("level-length", String.valueOf(levelLength));
        properties.setProperty("tempadminpass", String.valueOf(tempAdminPass));
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "Minecraft Classic Server Configuration");
        } catch (IOException e) {
            System.out.println("Failed to save default config: " + e.getMessage());
        }
    }

    // Getters
    public int getPort() {
        return port;
    }

    public String getServerName() {
        return serverName;
    }

    public String getServerMotd() {
        return serverMotd;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public boolean isEnableHeartbeat() {
        return enableHeartbeat;
    }

    public boolean isVerifyPlayers() {
        return verifyPlayers;
    }

    public int getLevelWidth() {
        return levelWidth;
    }

    public int getLevelHeight() {
        return levelHeight;
    }

    public int getLevelLength() {
        return levelLength;
    }

    public String getTempAdminPass() {
        return tempAdminPass;
    }
}