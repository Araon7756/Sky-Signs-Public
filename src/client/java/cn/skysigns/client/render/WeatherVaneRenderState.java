package cn.skysigns.client.render;

import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;

public final class WeatherVaneRenderState extends BlockEntityRenderState {
    public final ItemRenderState rotor = new ItemRenderState();
    public float renderedAngle;
    public boolean initialized;
}
