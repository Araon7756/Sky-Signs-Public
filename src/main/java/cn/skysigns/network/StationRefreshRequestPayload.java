package cn.skysigns.network;

import cn.skysigns.SkySigns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StationRefreshRequestPayload(long stationPos) implements CustomPayload {
    public static final Id<StationRefreshRequestPayload> ID =
            new Id<>(Identifier.of(SkySigns.MOD_ID, "station_refresh"));
    public static final PacketCodec<RegistryByteBuf, StationRefreshRequestPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.LONG, StationRefreshRequestPayload::stationPos,
                    StationRefreshRequestPayload::new);

    @Override
    public Id<StationRefreshRequestPayload> getId() {
        return ID;
    }
}
