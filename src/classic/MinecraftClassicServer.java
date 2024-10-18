package classic;

import classic.level.Level;
import classic.level.LevelGenerator;
import classic.packets.DisconnectPlayerPacket;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.net.*;
import java.text.DecimalFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MinecraftClassicServer {
    public static final int PORT = 25565;
    public static final byte PROTOCOL_VERSION = 0x07;
    public static final String SERVER_NAME = "Java Classic Server";
    public static final String SERVER_MOTD = "Welcome to a basic Minecraft Classic server!";
    public static final String SERVER_SALT = "your16CharSaltHere"; // Replace with your actual salt
    public static final boolean VERIFY_PLAYERS = false; // Set to true to enable verification
    public static final int MAX_PLAYERS = 1; // Set your desired maximum player count here

    public static Level level;

    private static final Object levelLock = new Object();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final DecimalFormat df = new DecimalFormat("#.##");

    static {
        level = LevelGenerator.generateFlatWorld();
    }

    public static byte getBlock(short x, short y, short z) {
        synchronized (levelLock) {
            return level.getBlock(x, y, z);
        }
    }

    public static void setBlock(short x, short y, short z, byte blockType) {
        synchronized (levelLock) {
            level.setBlock(x, y, z, blockType);
        }
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Minecraft Classic server running on port " + PORT);
        System.out.println("Player verification is " + (VERIFY_PLAYERS ? "enabled" : "disabled"));
        System.out.println("Maximum players: " + MAX_PLAYERS);

        // Start the metrics reporting task
        startMetricsReporting();

        while (true) {
            Socket clientSocket = serverSocket.accept();
            if (ClientHandler.getClientCount() < MAX_PLAYERS) {
                new Thread(new ClientHandler(clientSocket)).start();
                System.out.println("New player connected. Current player count: " + ClientHandler.getClientCount());
            } else {
                DisconnectPlayerPacket disconnectPlayerPacket = new  DisconnectPlayerPacket();
                disconnectPlayerPacket.setReason("Server is full");
                disconnectPlayerPacket.write(new DataOutputStream(clientSocket.getOutputStream()));
                System.out.println("Server is full. Rejecting new connection.");
//                try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
  //              }
                clientSocket.close();
            }
        }
    }

    private static void startMetricsReporting() {
        scheduler.scheduleAtFixedRate(() -> {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long totalMemory = memoryBean.getHeapMemoryUsage().getMax();
            double memoryUsage = (double) usedMemory / totalMemory * 100;

            double cpuLoad = -1;
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                cpuLoad = ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad() * 100;
            }

            System.out.println("Memory Usage: " + df.format(memoryUsage) + "% (" +
                    formatBytes(usedMemory) + " / " + formatBytes(totalMemory) + ")");
            if (cpuLoad >= 0) {
                System.out.println("CPU Usage: " + df.format(cpuLoad) + "%");
            } else {
                System.out.println("CPU Usage: Not available");
            }
            System.out.println("Player Count: " + ClientHandler.getClientCount() + " / " + MAX_PLAYERS);
            System.out.println("--------------------");
        }, 0, 15, TimeUnit.SECONDS);
    }

    private static String formatBytes(long bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return df.format(size) + " " + units[unitIndex];
    }

    public static boolean isServerFull() {
        return ClientHandler.getClientCount() >= MAX_PLAYERS;
    }

    public static void playerDisconnected(Socket socket) {
        System.out.println("Player disconnected. Current player count: " + ClientHandler.getClientCount());
    }
}