package net.classicube.api.event;

import net.classicube.api.Player;

public class PlayerJoinEvent extends Event {
    private final Player player;

    public PlayerJoinEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }
}