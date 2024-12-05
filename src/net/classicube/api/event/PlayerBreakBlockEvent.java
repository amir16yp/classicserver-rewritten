package net.classicube.api.event;

import net.classicube.api.Location;
import net.classicube.api.Player;
import net.classicube.api.enums.BlockType;

public class PlayerBreakBlockEvent extends PlayerBlockEvent{
    public PlayerBreakBlockEvent(Player player, Location blockLocation, BlockType blockType) {
        super(player, blockLocation, blockType);
    }
}
