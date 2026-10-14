package cn.skysigns.test.mixin;

import cn.skysigns.test.Inf01PacketProbe;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonNetworkHandler.class)
public abstract class Inf01PacketSendMixin {
    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void inf01$sent(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ChunkDataS2CPacket chunk && (Object) this instanceof ServerPlayNetworkHandler handler) {
            Inf01PacketProbe.sent(handler, handler.player.getEntityWorld().getRegistryKey().getValue().toString(), chunk.getChunkX(), chunk.getChunkZ());
        }
    }
}
