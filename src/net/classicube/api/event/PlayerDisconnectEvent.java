package net.classicube.api.event;

import net.classicube.api.Player;

public class PlayerDisconnectEvent extends Event
{
    private final Player player;
    private final String reason;
    public PlayerDisconnectEvent(Player player, String reason)
    {
        this.player = player;
        this.reason = reason;
    }
}
