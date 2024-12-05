package net.classicube.api.event;

import net.classicube.api.Location;
import net.classicube.api.Player;
import net.classicube.api.enums.BlockType;

public class PlayerBlockEvent extends Event
{
    private final Player player;
    private final Location blockLocation;
    private final BlockType blockType;

    public PlayerBlockEvent(Player player, Location blockLocation, BlockType blockType) {
        this.player = player;
        this.blockLocation = blockLocation;
        this.blockType = blockType;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getBlockLocation() {
        return blockLocation;
    }

    public BlockType getBlockType() {
        return blockType;
    }
}
