package classic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Timer;
import java.util.TimerTask;

public class HeartbeatManager {
    private static final String HEARTBEAT_URL = "http://www.classicube.net/server/heartbeat/";
    private static final long HEARTBEAT_INTERVAL = 45 * 1000; // 45 seconds
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SALT_FILE = "server.salt";

    private final MinecraftClassicServer server;
    private final String salt;
    private final Timer heartbeatTimer;
    private String serverUrl;
    private boolean hasError;
    private String lastError;

    public HeartbeatManager(MinecraftClassicServer server) {
        this.server = server;
        this.salt = loadOrGenerateSalt();
        this.heartbeatTimer = new Timer("HeartbeatTimer", true);
        this.hasError = false;
    }

    public void start() {
        System.out.println("Starting heartbeat manager with salt: " + salt);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendHeartbeat();
            }
        }, 0, HEARTBEAT_INTERVAL);
    }

    public void stop() {
        heartbeatTimer.cancel();
    }

    private void sendHeartbeat() {
        try {
            StringBuilder urlBuilder = new StringBuilder(HEARTBEAT_URL);
            urlBuilder.append("?port=").append(server.getPort());
            urlBuilder.append("&max=").append(server.getMaxPlayers());
            urlBuilder.append("&name=").append(URLEncoder.encode(server.getServerName(), StandardCharsets.UTF_8.toString()));
            urlBuilder.append("&public=").append("true");
            urlBuilder.append("&salt=").append(salt);
            urlBuilder.append("&users=").append(ClientHandler.getClientCount());
            urlBuilder.append("&software=").append(URLEncoder.encode("JavaServerRewritten", StandardCharsets.UTF_8.toString()));

            URL url = new URL(urlBuilder.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String response = reader.readLine();
                    if (response != null && response.startsWith("http")) {
                        serverUrl = response;
                        hasError = false;
                        System.out.println("Heartbeat successful - Server URL: " + serverUrl);
                    } else if (response != null && response.contains("error")) {
                        hasError = true;
                        lastError = response;
                        System.out.println("Heartbeat error: " + response);
                    }
                }
            } else {
                hasError = true;
                lastError = "HTTP Error: " + responseCode;
                System.out.println("Failed to send heartbeat. Response code: " + responseCode);
            }

            conn.disconnect();
        } catch (IOException e) {
            hasError = true;
            lastError = "Connection error: " + e.getMessage();
            System.out.println("Failed to send heartbeat: " + e.getMessage());
        }
    }

    private String loadOrGenerateSalt() {
        try {
            // Try to load existing salt
            if (Files.exists(Paths.get(SALT_FILE))) {
                String loadedSalt = new String(Files.readAllBytes(Paths.get(SALT_FILE)), StandardCharsets.UTF_8).trim();
                if (loadedSalt.length() == 16) {  // Verify salt is valid
                    System.out.println("Loaded existing salt from " + SALT_FILE);
                    return loadedSalt;
                }
            }
        } catch (IOException e) {
            System.out.println("Error loading salt: " + e.getMessage());
        }

        // Generate new salt if loading failed
        String newSalt = generateSalt();
        try {
            Files.write(Paths.get(SALT_FILE), newSalt.getBytes(StandardCharsets.UTF_8));
            System.out.println("Generated and saved new salt to " + SALT_FILE);
            System.out.println("DO NOT SHARE THIS FILE TO ANYONE!");
        } catch (IOException e) {
            System.out.println("Warning: Could not save salt: " + e.getMessage());
        }
        return newSalt;
    }

    private String generateSalt() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder salt = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            salt.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return salt.toString();
    }

    public static String generateMppass(String salt, String username) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update((salt + username).getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }


    public String getSalt() {
        return salt;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public boolean hasError() {
        return hasError;
    }

    public String getLastError() {
        return lastError;
    }
}