package net.classicube;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

public class DelegatingSocket extends Socket {
    private final Socket delegate;
    private final InputStream inputStream;

    public DelegatingSocket(Socket delegate, InputStream inputStream) {
        this.delegate = delegate;
        this.inputStream = inputStream;
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return delegate.getOutputStream();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public InetAddress getInetAddress() {
        return delegate.getInetAddress();
    }

    public String getInetAddressString() {
        return delegate.getInetAddress().getHostAddress();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        delegate.setTcpNoDelay(on);
    }

    @Override
    public int getPort() {
        return delegate.getPort();
    }

    @Override
    public InetAddress getLocalAddress() {
        return delegate.getLocalAddress();
    }
}