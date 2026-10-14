package cn.skysigns.test;

import cn.skysigns.registry.ModBlocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

/** Test-only magnified GUI item previews; never included in the released mod. */
final class InstrumentPreviewScreen extends Screen {
    InstrumentPreviewScreen() {
        super(Text.literal("Sky Signs beta.7 item previews"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF102431);
        context.drawText(textRenderer, title, 12, 12, 0xFFFFFFFF, false);
        Item[] items = {ModBlocks.WEATHER_VANE.asItem(), ModBlocks.BAROMETER.asItem(),
                ModBlocks.HYGROMETER.asItem(), ModBlocks.WEATHER_STATION.asItem()};
        for (int i = 0; i < items.length; i++) {
            int x = 12 + i * (width - 24) / items.length;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, 44);
            context.getMatrices().scale(4.0F, 4.0F);
            context.drawItem(items[i].getDefaultStack(), 0, 0);
            context.getMatrices().popMatrix();
            context.drawText(textRenderer, Text.literal(Integer.toString(i + 1)), x + 24, 116, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
