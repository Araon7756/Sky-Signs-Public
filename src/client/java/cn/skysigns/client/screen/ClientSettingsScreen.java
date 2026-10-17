package cn.skysigns.client.screen;

import cn.skysigns.client.SkySignsClient;
import cn.skysigns.client.config.ClientConfig;
import cn.skysigns.client.render.WindPrecipitationVisual;
import cn.skysigns.network.StationReportPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Objects;

/** Vanilla controls with a scrolling body and a fixed save/return footer. */
public final class ClientSettingsScreen extends Screen {
    private final Screen parent;
    private final ClientConfig config;
    private boolean saveFailed;
    private String reason;
    private ButtonWidget toggle;
    private StrengthSlider strength;
    private int titleY;

    public ClientSettingsScreen(Screen parent) { this(parent, SkySignsClient.config()); }

    public ClientSettingsScreen(Screen parent, ClientConfig config) {
        super(Text.translatable("screen.skysigns.settings.title"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        reason = WindPrecipitationVisual.unavailableReason();
        int bodyWidth = Math.max(80, Math.min(310, width - 32));
        var list = addDrawableChild(new SettingsList(bodyWidth));
        if (saveFailed) addText(list, Text.translatable("screen.skysigns.settings.save_failed"), bodyWidth);
        if (reason != null) addText(list, Text.translatable(reason), bodyWidth);
        toggle = ButtonWidget.builder(toggleLabel(), button -> {
            config.setEnabled(!config.enabled());
            updateControls();
        }).dimensions(0, 0, bodyWidth, 20).build();
        list.addControl(toggle, 26);
        strength = new StrengthSlider(bodyWidth);
        list.addControl(strength, 26);
        list.addControl(ButtonWidget.builder(Text.translatable("screen.skysigns.settings.defaults"), button -> {
            config.restoreDefaults();
            strength.refresh();
            updateControls();
        }).dimensions(0, 0, bodyWidth, 20).build(), 26);
        addText(list, Text.translatable("screen.skysigns.settings.help"), bodyWidth);
        int bodyHeight = Math.min(list.contentHeight(), Math.max(1, height - 64));
        int top = Math.max(0, (height - bodyHeight - 64) / 2);
        titleY = top + 10;
        list.position(width, bodyHeight, 0, top + 32);
        int footerY = list.getBottom() + 6;
        int footerWidth = saveFailed ? (bodyWidth - 6) / 2 : bodyWidth;
        addDrawableChild(ButtonWidget.builder(Text.translatable(saveFailed
                        ? "screen.skysigns.settings.retry" : "gui.done"), button -> close())
                .dimensions((width - bodyWidth) / 2, footerY, footerWidth, 20).build());
        if (saveFailed) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.skysigns.settings.return_unsaved"),
                    button -> returnToParent()).dimensions((width - bodyWidth) / 2 + footerWidth + 6,
                    footerY, footerWidth, 20).build());
        }
        updateControls();
    }

    private void addText(SettingsList list, Text text, int bodyWidth) {
        var widget = new MultilineTextWidget(text, textRenderer).setMaxWidth(bodyWidth);
        list.addControl(widget, widget.getHeight() + 10);
    }

    private Text toggleLabel() {
        return Text.translatable("screen.skysigns.settings.precipitation", Text.translatable(
                config.enabled() ? "options.on" : "options.off"));
    }

    private void updateControls() {
        toggle.setMessage(toggleLabel());
        strength.active = config.enabled() && reason == null;
        toggle.active = reason == null;
    }

    @Override
    public void tick() {
        if (!Objects.equals(reason, WindPrecipitationVisual.unavailableReason())) clearAndInit();
    }

    public void acceptStationReport(StationReportPayload report) {
        if (parent instanceof WeatherStationScreen station && station.stationPos() == report.stationPos()
                && station.isValidParent(client)) station.updateReport(report);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public Text getNarratedTitle() {
        var text = title.copy().append(". ").append(Text.translatable("screen.skysigns.settings.help"));
        if (reason != null) text.append(". ").append(Text.translatable(reason));
        if (saveFailed) text.append(". ").append(Text.translatable("screen.skysigns.settings.save_failed"));
        return text;
    }

    @Override
    public void close() {
        if (config.save()) {
            returnToParent();
        } else {
            saveFailed = true;
            clearAndInit();
            narrateScreenIfNarrationEnabled(true);
        }
    }

    private void returnToParent() {
        Screen target = parent;
        if (target instanceof WeatherStationScreen station && !station.isValidParent(client)) target = null;
        client.setScreen(target);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, titleY, 0xFFFFFFFF);
    }

    private final class StrengthSlider extends SliderWidget {
        StrengthSlider(int width) {
            super(0, 0, width, 20, Text.empty(), config.strength());
            updateMessage();
        }
        void refresh() { value = config.strength(); updateMessage(); }
        @Override protected void updateMessage() {
            setMessage(Text.translatable("screen.skysigns.settings.strength", Math.round(value * 100)));
        }
        @Override protected void applyValue() { config.setStrength((float) value); }
    }

    private final class SettingsList extends ElementListWidget<ControlRow> {
        private final int rowWidth;
        SettingsList(int rowWidth) {
            super(ClientSettingsScreen.this.client, ClientSettingsScreen.this.width,
                    Math.max(1, ClientSettingsScreen.this.height - 64), 32, 26);
            this.rowWidth = rowWidth;
            centerListVertically = false;
        }
        void addControl(ClickableWidget control, int height) { addEntry(new ControlRow(control), height); }
        int contentHeight() { return getContentsHeightWithPadding(); }
        @Override public int getRowWidth() { return rowWidth; }
        @Override protected void drawMenuListBackground(DrawContext context) { }
        @Override protected void drawHeaderAndFooterSeparators(DrawContext context) { }
    }

    private static final class ControlRow extends ElementListWidget.Entry<ControlRow> {
        private final ClickableWidget control;
        ControlRow(ClickableWidget control) { this.control = control; }
        @Override public List<? extends Element> children() { return List.of(control); }
        @Override public List<? extends Selectable> selectableChildren() { return List.of(control); }
        @Override public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
            control.setPosition(getContentMiddleX() - control.getWidth() / 2, getContentY());
            control.render(context, mouseX, mouseY, delta);
        }
    }
}
