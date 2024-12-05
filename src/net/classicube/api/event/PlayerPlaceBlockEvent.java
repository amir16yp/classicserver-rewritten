package net.classicube.api.event;

import net.classicube.api.Location;
import net.classicube.api.Player;
import net.classicube.api.enums.BlockType;

public class PlayerPlaceBlockEvent extends PlayerBlockEvent{
    public PlayerPlaceBlockEvent(Player player, Location blockLocation, BlockType blockType) {
        super(player, blockLocation, blockType);
    }

    public void setBlockType(BlockType blockType)
    {
        this.blockType = blockType;
    }
}
