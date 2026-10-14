package cn.skysigns.network;

import cn.skysigns.SkySigns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

public record StationReportPayload(
        Text weather, Text localWeather, Text pressure, Text humidity, Text wind, Text forecast,
        int pressureValue, int humidityValue, int windValue,
        long stationPos, int timelineFlags, int secondsRemaining
) implements CustomPayload {
    public static final int OPEN_FLAG = 0x100;
    public static final int MANUAL_FLAG = 0x200;
    // v2 carries translatable Text rather than the v1 pre-rendered strings.
    public static final Id<StationReportPayload> ID = new Id<>(Identifier.of(SkySigns.MOD_ID, "station_report_v2"));
    public static final PacketCodec<RegistryByteBuf, StationReportPayload> CODEC = PacketCodec.of(
            (report, buf) -> {
                TextCodecs.PACKET_CODEC.encode(buf, report.weather());
                TextCodecs.PACKET_CODEC.encode(buf, report.localWeather());
                TextCodecs.PACKET_CODEC.encode(buf, report.pressure());
                TextCodecs.PACKET_CODEC.encode(buf, report.humidity());
                TextCodecs.PACKET_CODEC.encode(buf, report.wind());
                TextCodecs.PACKET_CODEC.encode(buf, report.forecast());
                PacketCodecs.VAR_INT.encode(buf, report.pressureValue());
                PacketCodecs.VAR_INT.encode(buf, report.humidityValue());
                PacketCodecs.VAR_INT.encode(buf, report.windValue());
                PacketCodecs.LONG.encode(buf, report.stationPos());
                PacketCodecs.VAR_INT.encode(buf, report.timelineFlags());
                PacketCodecs.VAR_INT.encode(buf, report.secondsRemaining());
            },
            buf -> new StationReportPayload(
                    TextCodecs.PACKET_CODEC.decode(buf), TextCodecs.PACKET_CODEC.decode(buf),
                    TextCodecs.PACKET_CODEC.decode(buf), TextCodecs.PACKET_CODEC.decode(buf),
                    TextCodecs.PACKET_CODEC.decode(buf), TextCodecs.PACKET_CODEC.decode(buf),
                    PacketCodecs.VAR_INT.decode(buf), PacketCodecs.VAR_INT.decode(buf),
                    PacketCodecs.VAR_INT.decode(buf), PacketCodecs.LONG.decode(buf),
                    PacketCodecs.VAR_INT.decode(buf), PacketCodecs.VAR_INT.decode(buf)
            )
    );

    public boolean opensScreen() {
        return (timelineFlags & OPEN_FLAG) != 0;
    }

    public boolean automatic() {
        return (timelineFlags & MANUAL_FLAG) == 0;
    }

    public int progressPercent() {
        return timelineFlags & 0x7F;
    }

    @Override
    public Id<StationReportPayload> getId() {
        return ID;
    }
}
