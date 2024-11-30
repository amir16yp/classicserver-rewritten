package net.classicube.packets.cpe;

import net.classicube.api.enums.EnvColorType;
import net.classicube.packets.PacketType;

import java.awt.*;
import java.io.DataOutputStream;
import java.io.IOException;

public class EnvColorsPacket extends CPEPacket{
    private final EnvColorType colorType;
    private final Color color;

    public EnvColorsPacket(EnvColorType colorType, Color color) {
        super(PacketType.CPE_ENV_SET_COLORS);
        this.colorType = colorType;
        this.color = color;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        out.writeByte(colorType.getValue());
        out.writeShort(color.getRed());
        out.writeShort(color.getGreen());
        out.writeShort(color.getBlue());
    }
}
