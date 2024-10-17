import classic.level.Level;
import classic.level.LevelGenerator;

import java.io.*;
import java.net.*;

public class MinecraftClassicServer {
    public static final int PORT = 25565;
    public static final byte PROTOCOL_VERSION = 0x07;
    public static final String SERVER_NAME = "Java Classic Server";
    public static final String SERVER_MOTD = "Welcome to a basic Minecraft Classic server!";
    public static final String SERVER_SALT = "your16CharSaltHere"; // Replace with your actual salt
    public static final boolean VERIFY_PLAYERS = false; // Set to true to enable verification

    public static Level level;

    static {
        level = LevelGenerator.generateFlatWorld();
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Minecraft Classic server running on port " + PORT);
        System.out.println("Player verification is " + (VERIFY_PLAYERS ? "enabled" : "disabled"));

        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }
}