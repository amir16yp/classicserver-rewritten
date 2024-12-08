package net.classicube.packets.cpe;

import net.classicube.api.enums.WeatherType;
import net.classicube.packets.PacketType;

import java.io.DataOutputStream;
import java.io.IOException;

public class EnvWeatherPacket extends CPEPacket {
    private final WeatherType weatherType;

    public EnvWeatherPacket(WeatherType weatherType)
    {
        super(PacketType.CPE_WEATHER_TYPE);
        this.weatherType = weatherType;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        super.write(out);
        out.writeByte(weatherType.getId());
    }
}
