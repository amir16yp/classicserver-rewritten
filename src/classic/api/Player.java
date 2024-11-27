package classic.api;

import classic.ClientHandler;
import classic.packets.MessagePacket;

import java.io.IOException;

public class Player
{
    private ClientHandler handle;

    public Player(ClientHandler handle)
    {
        this.handle = handle;
    }

    public String getIPAddress()
    {
        return handle.getSocket().getInetAddress().getHostAddress();
    }

    public void sendMessage(String message)
    {
        MessagePacket packet = new  MessagePacket();
        packet.setMessage(message);
        packet.setPlayerId((byte) -1);
        try {
            packet.write(handle.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void kick(String reason)
    {
        handle.disconnectPlayer(reason);
    }

}
