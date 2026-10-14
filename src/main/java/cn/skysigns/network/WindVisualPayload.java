package cn.skysigns.network;

import cn.skysigns.SkySigns;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** A small, once-per-second visual snapshot; the server remains authoritative. */
public record WindVisualPayload(float directionDegrees, float strength) implements CustomPayload {
    public static final Id<WindVisualPayload> ID = new Id<>(Identifier.of(SkySigns.MOD_ID, "wind_visual"));
    public static final PacketCodec<RegistryByteBuf, WindVisualPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeFloat(payload.directionDegrees());
                buf.writeFloat(payload.strength());
            },
            buf -> new WindVisualPayload(buf.readFloat(), buf.readFloat())
    );

    @Override
    public Id<WindVisualPayload> getId() {
        return ID;
    }
}
