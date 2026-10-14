package cn.skysigns.test.mixin;

import cn.skysigns.test.Inf01PacketProbe;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class Inf01PacketReceiveMixin {
    // loadChunk runs after vanilla's main-thread handoff. Dropping here prevents actual FULL installation.
    @Inject(method = "loadChunk", at = @At("HEAD"), cancellable = true)
    private void inf01$received(int x, int z, ChunkData data, CallbackInfo ci) {
        var handler = (ClientPlayNetworkHandler) (Object) this;
        if (Inf01PacketProbe.received(handler, handler.getWorld().getRegistryKey().getValue().toString(), x, z)) ci.cancel();
    }
}
