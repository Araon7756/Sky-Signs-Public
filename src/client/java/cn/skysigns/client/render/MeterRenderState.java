package cn.skysigns.client.render;

import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;

public final class MeterRenderState extends BlockEntityRenderState {
    public final ItemRenderState needle = new ItemRenderState();
    public float renderedPercent;
    public boolean initialized;
    public float facingDegrees;
}
