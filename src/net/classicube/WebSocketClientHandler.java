package net.classicube;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class WebSocketClientHandler extends ClientHandler {
    private final BlockingQueue<byte[]> messageQueue = new ArrayBlockingQueue<>(1024);
    private WebSocketInputStream wsInput;

    public WebSocketClientHandler(Socket socket, MinecraftClassicServer server) throws IOException {
        super(socket, server);
        setupStreams();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    @Override
    protected void setupStreams() throws IOException {
        this.wsInput = new WebSocketInputStream(this.socket.getInputStream());
        this.in = new DataInputStream(this.wsInput);
        this.out = new DataOutputStream(new WebSocketOutputStream(this.socket.getOutputStream()));
    }

    private class WebSocketInputStream extends InputStream {
        private final InputStream source;
        private ByteArrayInputStream buffer;
        private boolean isMasked;
        private byte[] maskKey;
        private int payloadLength;

        public WebSocketInputStream(InputStream source) {
            this.source = source;
        }

        @Override
        public int read() throws IOException {
            if (buffer == null || buffer.available() == 0) {
                readFrame();
            }
            return buffer != null ? buffer.read() : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (buffer == null || buffer.available() == 0) {
                readFrame();
            }
            return buffer != null ? buffer.read(b, off, len) : -1;
        }

        private void readFrame() throws IOException {
            int firstByte = source.read();
            if (firstByte == -1) throw new EOFException();

            boolean isFinal = (firstByte & 0x80) != 0;
            int opcode = firstByte & 0x0F;

            int secondByte = source.read();
            if (secondByte == -1) throw new EOFException();

            isMasked = (secondByte & 0x80) != 0;
            payloadLength = secondByte & 0x7F;

            if (payloadLength == 126) {
                payloadLength = (source.read() << 8) | source.read();
            } else if (payloadLength == 127) {
                payloadLength = 0;
                for (int i = 0; i < 8; i++) {
                    payloadLength = (payloadLength << 8) | source.read();
                }
            }

            if (isMasked) {
                maskKey = new byte[4];
                source.read(maskKey, 0, 4);
            }

            byte[] payload = new byte[payloadLength];
            int bytesRead = 0;
            while (bytesRead < payloadLength) {
                int read = source.read(payload, bytesRead, payloadLength - bytesRead);
                if (read == -1) throw new EOFException();
                bytesRead += read;
            }

            if (isMasked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= maskKey[i % 4];
                }
            }

            buffer = new ByteArrayInputStream(payload);

            if (!isFinal) {
                readFrame(); // Handle continuation frames
            }
        }
    }

    private class WebSocketOutputStream extends OutputStream {
        private final OutputStream source;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        public WebSocketOutputStream(OutputStream source) {
            this.source = source;
        }

        @Override
        public void write(int b) throws IOException {
            buffer.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            buffer.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            byte[] data = buffer.toByteArray();
            if (data.length > 0) {
                sendWebSocketFrame(data);
                buffer.reset();
            }
        }

        private void sendWebSocketFrame(byte[] payload) throws IOException {
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x82); // Binary frame, FIN=1

            if (payload.length < 126) {
                frame.write(payload.length);
            } else if (payload.length < 65536) {
                frame.write(126);
                frame.write((payload.length >> 8) & 0xFF);
                frame.write(payload.length & 0xFF);
            } else {
                frame.write(127);
                for (int i = 7; i >= 0; i--) {
                    frame.write((payload.length >> (8 * i)) & 0xFF);
                }
            }

            frame.write(payload);
            source.write(frame.toByteArray());
            source.flush();
        }
    }
}