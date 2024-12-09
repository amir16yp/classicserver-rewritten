package net.classicube;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DualProtocolServer {
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final ServerSocket serverSocket;
    private final MinecraftClassicServer mcServer;
    private final Map<Socket, WebSocketClientHandler> wsClients = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public DualProtocolServer(MinecraftClassicServer mcServer, int port) throws IOException {
        this.mcServer = mcServer;
        this.serverSocket = new ServerSocket(port);
        startConnectionListener();
    }

    private void startConnectionListener() {
        new Thread(() -> {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    handleInitialConnection(socket);
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Connection error: " + e.getMessage());
                    }
                }
            }
        }, "Connection-Listener").start();
    }

    private void handleInitialConnection(Socket socket) throws IOException {
        InputStream original = socket.getInputStream();
        byte[] buffer = new byte[4];
        int read = original.read(buffer, 0, 4);

        if (read > 0 && buffer[0] == 'G' && buffer[1] == 'E' && buffer[2] == 'T') {
            byte[] header = new byte[4092];
            read = original.read(header);
            if (read > 0) {
                byte[] fullHeader = new byte[4 + read];
                System.arraycopy(buffer, 0, fullHeader, 0, 4);
                System.arraycopy(header, 0, fullHeader, 4, read);
                try {
                    handleWebSocketRequest(socket, new String(fullHeader));
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            // Create a new stream that includes the initial bytes plus remaining input
            InputStream combined = new SequenceInputStream(
                    new ByteArrayInputStream(Arrays.copyOf(buffer, read)),
                    original
            );
            Socket wrappedSocket = new DelegatingSocket(socket, combined);
            handleTCPConnection(wrappedSocket);
        }
    }

    private void handleWebSocketRequest(Socket socket, String headerStr) throws IOException, NoSuchAlgorithmException {
        Map<String, String> headers = parseHeaders(headerStr);
        String key = headers.get("sec-websocket-key");

        if (!isWebSocketUpgrade(headers) || key == null) {
            socket.close();
            return;
        }

        // Send WebSocket handshake response
        String acceptKey = generateAcceptKey(key);
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                "Sec-WebSocket-Protocol: ClassiCube\r\n\r\n";

        socket.getOutputStream().write(response.getBytes());

        WebSocketClientHandler handler = new WebSocketClientHandler(socket, mcServer);
        wsClients.put(socket, handler);
        new Thread(handler).start();
    }

    private boolean isWebSocketUpgrade(Map<String, String> headers) {
        return "websocket".equalsIgnoreCase(headers.get("upgrade")) &&
                "ClassiCube".equals(headers.get("sec-websocket-protocol"));
    }

    private Map<String, String> parseHeaders(String headerStr) {
        Map<String, String> headers = new HashMap<>();
        for (String line : headerStr.split("\r\n")) {
            int colon = line.indexOf(": ");
            if (colon > 0) {
                headers.put(
                        line.substring(0, colon).toLowerCase(),
                        line.substring(colon + 2)
                );
            }
        }
        return headers;
    }


    private void handleTCPConnection(Socket socket) throws IOException {
        if (ClientHandler.getClientCount() < mcServer.getMaxPlayers()) {
            new Thread(new ClientHandler(socket, mcServer)).start();
        } else {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing rejected connection: " + e.getMessage());
            }
        }
    }

    private String generateAcceptKey(String key) throws NoSuchAlgorithmException {
        String concat = key + WEBSOCKET_GUID;
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] hash = sha1.digest(concat.getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }

    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }

        for (Map.Entry<Socket, WebSocketClientHandler> entry : wsClients.entrySet()) {
            try {
                entry.getValue().disconnectPlayer("Server shutting down");
                entry.getKey().close();
            } catch (Exception e) {
                System.err.println("Error closing WebSocket: " + e.getMessage());
            }
        }
        wsClients.clear();
    }
}