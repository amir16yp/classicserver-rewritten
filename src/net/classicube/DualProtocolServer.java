package net.classicube;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DualProtocolServer {
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final ServerSocket serverSocket;
    private final MinecraftClassicServer mcServer;
    private final Map<Socket, WebSocketClientHandler> wsClients = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    private final Map<String, CachedResponse> webCache = new ConcurrentHashMap<>();

    public String getPublicIP() {
        try {
            URL url = new URL("https://classicube.net/api/myip");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            return response.toString().trim(); // Return the public IP
        } catch (Exception e) {
            e.printStackTrace();
            return null; // Return null or fallback if there is an error
        }
    }


    public DualProtocolServer(MinecraftClassicServer mcServer, int port) throws IOException {
        this.mcServer = mcServer;
        this.serverSocket = new ServerSocket(port);
        this.webCache.put("/", new CachedResponse("WEB GUEST DISABELD".getBytes(), "text/html"));
        if (this.mcServer.getConfig().isEnableWebGuests())
        {
            setupWebguest();
        }

        startConnectionListener();
    }

    private void setupWebguest() throws IOException {
        String response = new String(readBytesFromResource("/net/classicube/webclient.html"));

        String host = determineHost();
        response = response.replaceAll("\\[\\[HOST]]", host);

        String ipAddr = getListeningIP();
        response = response.replaceAll("\\[\\[IPADDR]]", ipAddr);
        response = response.replaceAll("\\[\\[PORT]]", String.valueOf(mcServer.getPort()));

        this.webCache.put("/", new CachedResponse(response.getBytes(), "text/html"));

        File texFile = new File("webtex.zip");
        if (texFile.exists()) {
            this.webCache.put("/static/default.zip", new CachedResponse(readStreamFully(texFile.toURL().openStream()), "application/zip"));
        } else {
            byte[] webTex = fetchWebTex();
            this.webCache.put("/static/default.zip", new CachedResponse(webTex, "application/zip"));
            saveWebTex(texFile, webTex);
        }
    }

    private String determineHost() {
        if (!mcServer.getConfig().getWebGuestDomain().isEmpty()) {
            return mcServer.getConfig().getWebGuestDomain();
        } else {
            String publicIP = getPublicIP();
            System.out.println("but no domain specified, using public IP " + publicIP + " for web guests");
            return publicIP;
        }
    }

    private byte[] fetchWebTex() throws IOException {
        try (InputStream inputStream = new URL("http://www.classicube.net/static/default.zip").openStream()) {
            return readStreamFully(inputStream);
        }
    }

    private void saveWebTex(File texFile, byte[] webTex) {
        try (FileOutputStream fos = new FileOutputStream(texFile)) {
            fos.write(webTex);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    private static byte[] readBytesFromResource(String resourcePath) throws IOException {
        try (InputStream inputStream = DualProtocolServer.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new FileNotFoundException("Resource not found: " + resourcePath);
            }
            return readStreamFully(inputStream);
        }
    }

    private static byte[] readStreamFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(data)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    public String getListeningIP() {
        return this.serverSocket.getInetAddress().getHostAddress();
    }

    private void sendResponse(Socket socket, byte[] content, String contentType) throws IOException {
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + content.length + "\r\n\r\n";
        socket.getOutputStream().write(header.getBytes());
        socket.getOutputStream().write(content);
        socket.getOutputStream().flush();
        socket.close();
    }

    private void handleHttpRequest(Socket socket, String path) throws IOException {
        CachedResponse cachedResponse = webCache.get(path);

        if (cachedResponse != null) {
            sendResponse(socket, cachedResponse.getContent(), cachedResponse.getContentType());
        }
    }

    private String getRequestedPath(String headerStr) {
        String[] lines = headerStr.split("\r\n");
        for (String line : lines) {
            if (line.startsWith("GET")) {
                return line.split(" ")[1]; // Extract the path from the GET request
            }
        }
        return "";
    }

    private void handleWebSocketRequest(Socket socket, String headerStr) throws IOException, NoSuchAlgorithmException {
        Map<String, String> headers = parseHeaders(headerStr);
        String key = headers.get("sec-websocket-key");

        // Check if it's a valid WebSocket upgrade request
        if (!isWebSocketUpgrade(headers) || key == null) {
            handleHttpRequest(socket, getRequestedPath(headerStr));
            socket.close();
            return;
        }

        // Send WebSocket handshake response if it's a valid WebSocket request
        String acceptKey = generateAcceptKey(key);
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                "Sec-WebSocket-Protocol: ClassiCube\r\n\r\n";

        socket.getOutputStream().write(response.getBytes());

        // Create and start the WebSocket client handler
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

    private static class CachedResponse {
        private final byte[] content;
        private final String contentType;

        public CachedResponse(byte[] content, String contentType) {
            this.content = content;
            this.contentType = contentType;
        }

        public byte[] getContent() {
            return content;
        }

        public String getContentType() {
            return contentType;
        }
    }

}