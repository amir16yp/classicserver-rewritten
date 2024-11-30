package net.classicube.packets.cpe;

import net.classicube.packets.PacketType;

import java.io.DataOutputStream;
import java.io.IOException;

public class ExtAddPlayerNamePacket extends CPEPacket {
    private byte nameID;
    private String autocompletePlayerName;
    private String listPlayerName;
    private String groupName;
    private byte groupRank;

    public ExtAddPlayerNamePacket() {
        super(PacketType.CPE_EXT_ADD_PLAYERNAME);
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        out.writeShort(nameID);
        CPEPacket.writeString(out, autocompletePlayerName);
        CPEPacket.writeString(out, listPlayerName);
        CPEPacket.writeString(out, groupName);
        out.writeByte(groupRank);
    }

    public void setNameID(byte nameID) {
        this.nameID = nameID;
    }

    public void setAutocompletePlayerName(String autocompletePlayerName) {
        this.autocompletePlayerName = autocompletePlayerName;
    }

    public void setListPlayerName(String listPlayerName) {
        this.listPlayerName = listPlayerName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public void setGroupRank(byte groupRank) {
        this.groupRank = groupRank;
    }
}
