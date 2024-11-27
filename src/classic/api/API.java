package classic.api;

import classic.ClientHandler;
import classic.MinecraftClassicServer;
import com.sun.security.ntlm.Client;

import java.util.ArrayList;

public class API
{
    private static API instance;

    private MinecraftClassicServer server;

    public static API getInstance() {
        return instance;
    }

    public API(MinecraftClassicServer server)
    {
        this.server = server;
        instance = this;
    }

    public static ArrayList<Player> getPlayers() {
        ArrayList<Player> players = new ArrayList<Player>();
        for (ClientHandler handle : ClientHandler.getClients())
        {
            players.add(new Player(handle));
        }
        return players;
    }
}
