package cn.skysigns.client.screen;

import net.minecraft.registry.tag.FluidTags;
import cn.skysigns.network.StationReportPayload;
import cn.skysigns.network.StationRefreshRequestPayload;
import cn.skysigns.registry.ModBlocks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import cn.skysigns.client.ClientSession;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import org.joml.Vector2i;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class WeatherStationScreen extends Screen {
    private static final int TOOLTIP_MARGIN = 12;
    private static final TooltipPositioner TOOLTIP_POSITIONER = (screenWidth, screenHeight, mouseX, mouseY,
                                                                 tooltipWidth, tooltipHeight) -> new Vector2i(
            MathHelper.clamp(mouseX + 12, TOOLTIP_MARGIN,
                    Math.max(TOOLTIP_MARGIN, screenWidth - tooltipWidth - TOOLTIP_MARGIN)),
            MathHelper.clamp(mouseY - 12, TOOLTIP_MARGIN,
                    Math.max(TOOLTIP_MARGIN, screenHeight - tooltipHeight - TOOLTIP_MARGIN)));

    private StationReportPayload report;
    private final ClientSession session;
    private int refreshTicks;
    private int panelWidth;
    private int pointerX;
    private int pointerY;
    private Text hoveredText;

    public WeatherStationScreen(StationReportPayload report) {
        super(Text.translatable("screen.skysigns.station.title"));
        this.report = report;
        this.session = ClientSession.capture(MinecraftClient.getInstance());
    }

    @Override
    protected void init() {
        int panel = Math.max(1, Math.min(420, width - 12));
        int panelHeight = height < 240 ? 172 : 228;
        int top = Math.max(0, (height - panelHeight) / 2);
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.skysigns.settings.short"),
                button -> client.setScreen(new ClientSettingsScreen(this)))
                .dimensions((width + panel) / 2 - 84, top + 2, 80, 20).build());
    }

    public boolean isValidParent(MinecraftClient client) {
        if (!session.matches(client) || client.player == null) return false;
        BlockPos pos = BlockPos.fromLong(report.stationPos());
        return client.world.getBlockState(pos).isOf(ModBlocks.WEATHER_STATION)
                && !cn.skysigns.block.WaterAwareInstrument.isSubmerged(client.world, pos)
                && client.player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64.0D;
    }

    public long stationPos() {
        return report.stationPos();
    }

    public void updateReport(StationReportPayload report) {
        this.report = report;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        if (client == null || !isValidParent(client)) {
            close();
            return;
        }
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            ClientPlayNetworking.send(new StationRefreshRequestPayload(report.stationPos()));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        panelWidth = Math.max(1, Math.min(420, width - 12));
        pointerX = mouseX;
        pointerY = mouseY;
        hoveredText = null;
        boolean compact = height < 240;
        int header = compact ? 24 : 32;
        int weatherHeight = compact ? 30 : 44;
        int rowHeight = compact ? 26 : 32;
        int footer = compact ? 40 : 56;
        int panelHeight = header + weatherHeight + rowHeight * 3 + footer;
        int left = (width - panelWidth) / 2;
        int top = Math.max(0, (height - panelHeight) / 2);
        context.fill(left, top, left + panelWidth, top + panelHeight, 0xFF183E50);
        context.fill(left + 2, top + header, left + panelWidth - 2, top + panelHeight - 2, 0xFFF3F6F4);
        fittedText(context, title, left + 12, top + (header - 9) / 2, panelWidth - 104, 0xFFFFFFFF);
        int y = top + header;
        context.fill(left + 8, y + 3, left + panelWidth - 8, y + weatherHeight - 2, 0xFFE0ECEF);
        int weatherLabelWidth = textRenderer.getWidth(Text.translatable("screen.skysigns.station.weather")) + 10;
        fittedText(context, Text.translatable("screen.skysigns.station.weather"),
                left + 14, y + 6, weatherLabelWidth, 0xFF3B5866);
        fittedText(context, report.weather(), left + 14 + weatherLabelWidth, y + 6,
                panelWidth - 28 - weatherLabelWidth, 0xFF142D3B);
        fittedText(context, Text.translatable("screen.skysigns.station.local", report.localWeather()),
                left + 14, y + (compact ? 18 : 24), panelWidth - 28, 0xFF3B5866);
        y += weatherHeight;
        int labelWidth = 0;
        for (String key : new String[]{"pressure", "humidity", "wind"}) {
            labelWidth = Math.max(labelWidth, textRenderer.getWidth(Text.translatable("screen.skysigns.station." + key)));
        }
        labelWidth = Math.min(labelWidth + 12, panelWidth / 2);
        row(context, left, y, rowHeight, labelWidth, "screen.skysigns.station.pressure", report.pressure(),
                0xFF6A83A8, MathHelper.clamp(Math.round((report.pressureValue() - 980) * 100.0F / 50.0F), 0, 100));
        row(context, left, y + rowHeight, rowHeight, labelWidth, "screen.skysigns.station.humidity",
                report.humidity(), 0xFF398DAA, report.humidityValue());
        row(context, left, y + rowHeight * 2, rowHeight, labelWidth, "screen.skysigns.station.wind",
                report.wind(), 0xFF557FAD, report.windValue());
        y += rowHeight * 3;
        context.fill(left + 8, y, left + panelWidth - 8, y + footer - 6, 0xFFE0ECEF);
        fittedText(context, Text.translatable("screen.skysigns.station.forecast", report.forecast()),
                left + 14, y + 5, panelWidth - 28, 0xFF142D3B);
        if (report.automatic()) {
            int seconds = Math.max(0, report.secondsRemaining());
            fittedText(context, Text.translatable("screen.skysigns.station.timing",
                    report.progressPercent(), seconds / 60, seconds % 60),
                    left + 14, y + 18, panelWidth - 28, 0xFF142D3B);
            int progress = MathHelper.clamp(report.progressPercent(), 0, 100);
            context.fill(left + 14, y + footer - 10, left + panelWidth - 14, y + footer - 7, 0xFFB5C8CF);
            context.fill(left + 14, y + footer - 10,
                    left + 14 + Math.round((panelWidth - 28) * progress / 100.0F), y + footer - 7, 0xFF287FA3);
        } else {
            fittedText(context, Text.translatable("screen.skysigns.station.manual"),
                    left + 14, y + 18, panelWidth - 28, 0xFF263E49);
        }
        super.render(context, mouseX, mouseY, delta);
        if (hoveredText != null) {
            context.drawTooltip(textRenderer,
                    textRenderer.wrapLines(hoveredText, Math.max(1, width - TOOLTIP_MARGIN * 2)),
                    TOOLTIP_POSITIONER, mouseX, mouseY, false);
        }
    }

    private void row(DrawContext context, int left, int y, int rowHeight, int labelWidth,
                     String label, Text value, int color, int percent) {
        fittedText(context, Text.translatable(label), left + 14, y + 4, labelWidth - 8, 0xFF3B5866);
        fittedText(context, value, left + 14 + labelWidth, y + 4, panelWidth - 28 - labelWidth, 0xFF142D3B);
        int barWidth = Math.max(0, panelWidth - 28);
        context.fill(left + 14, y + 17, left + panelWidth - 14, y + 21, 0xFFB5C8CF);
        context.fill(left + 14, y + 17,
                left + 14 + Math.round(barWidth * MathHelper.clamp(percent, 0, 100) / 100.0F),
                y + 21, color);
    }

    private void fittedText(DrawContext context, Text text, int x, int y, int availableWidth, int color) {
        availableWidth = Math.max(0, availableWidth);
        if (availableWidth == 0) return;
        if (textRenderer.getWidth(text) <= availableWidth) {
            context.drawText(textRenderer, text, x, y, color, false);
            return;
        }
        String shortened = textRenderer.trimToWidth(text.getString(),
                Math.max(0, availableWidth - textRenderer.getWidth("…"))) + "…";
        context.drawText(textRenderer, shortened, x, y, color, false);
        if (pointerX >= x && pointerX < x + availableWidth && pointerY >= y
                && pointerY < y + textRenderer.fontHeight) hoveredText = text;
    }
}
