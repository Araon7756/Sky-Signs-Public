package cn.skysigns.test;

import cn.skysigns.client.ClientSession;
import cn.skysigns.client.ClientWindState;
import cn.skysigns.client.SkySignsClient;
import cn.skysigns.client.config.ClientConfig;
import cn.skysigns.client.render.WindPrecipitationVisual;
import cn.skysigns.client.screen.ClientSettingsScreen;
import cn.skysigns.client.screen.WeatherStationScreen;
import cn.skysigns.network.StationReportPayload;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static cn.skysigns.test.P3StateConfigAssertions.check;
import static cn.skysigns.test.P3StateConfigAssertions.near;

@SuppressWarnings("UnstableApiUsage")
final class P3ClientAssertions {
    static void verify(ClientGameTestContext context, TestSingleplayerContext singleplayer,
                       BlockPos pos, StationReportPayload report) {
        String language = context.computeOnClient(client -> client.options.language);
        int[] window = context.computeOnClient(client -> new int[]{client.getWindow().getWidth(),
                client.getWindow().getHeight(), client.options.getGuiScale().getValue()});
        try {
            // Exercise the actual registered, rebindable entry without a station.
            context.runOnClient(client -> {
                client.setScreen(null);
                var key = KeyBinding.byId("key.skysigns.settings");
                check(key != null && key.isUnbound(), "Settings key missing or bound by default");
                var binding = InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F6);
                key.setBoundKey(binding);
                KeyBinding.updateKeysByCode();
                KeyBinding.onKeyPressed(binding);
            });
            context.waitForScreen(ClientSettingsScreen.class);
            context.runOnClient(client -> {
                KeyBinding.byId("key.skysigns.settings").setBoundKey(InputUtil.UNKNOWN_KEY);
                KeyBinding.updateKeysByCode();
                check(!client.currentScreen.shouldPause(), "Settings changed station pause semantics");
                client.currentScreen.close();
                check(client.currentScreen == null, "Key entry return path");
            });
            for (String locale : new String[]{"en_us", "zh_cn"}) {
                changeLanguage(context, locale);
                for (int[] size : new int[][]{{200, 220}, {240, 220}, {320, 180}}) {
                    context.runOnClient(client -> {
                        client.setScreen(new WeatherStationScreen(report));
                        press(buttons(client.currentScreen).getFirst());
                        check(client.currentScreen instanceof ClientSettingsScreen, "Station settings entry missing");
                        client.options.getGuiScale().setValue(1);
                        client.getWindow().setWindowedSize(size[0], size[1]);
                    });
                    context.waitFor(client -> client.getWindow().getScaledWidth() == size[0]
                            && client.getWindow().getScaledHeight() == size[1]
                            && client.currentScreen.width == size[0] && client.currentScreen.height == size[1]);
                    context.runOnClient(client -> {
                        var screen = client.currentScreen;
                        var list = (EntryListWidget<?>) screen.children().getFirst();
                        check(list.getY() >= 30 && list.getBottom() <= screen.height - 30, "Body overlaps header/footer");
                        for (var button : buttons(screen)) {
                            check(!button.getMessage().getString().contains("skysigns."), "Unresolved button translation");
                        }
                        var footer = screen.children().stream().filter(ButtonWidget.class::isInstance)
                                .map(ButtonWidget.class::cast).findFirst().orElseThrow();
                        check(footer.getY() >= list.getBottom() && footer.getBottom() <= screen.height,
                                "Save button inaccessible");
                        check(Math.abs(footer.getX() + footer.getWidth() / 2 - screen.width / 2) <= 1,
                                "Settings are not horizontally centered in the viewport");
                        // Tab traverses real vanilla navigation paths; footer must be reachable even when body scrolls.
                        boolean footerFocused = false;
                        for (int i = 0; i < 14; i++) {
                            screen.keyPressed(new KeyInput(GLFW.GLFW_KEY_TAB, 0, 0));
                            footerFocused |= footer.isFocused();
                        }
                        check(footerFocused, "Keyboard cannot reach fixed footer");
                        check(!screen.getNarratedTitle().getString().contains("skysigns."), "Narrated status not translated");
                        SkySignsClient.acceptStationReport(client, ClientSession.capture(client), report);
                        check(client.currentScreen == screen, "Async open report replaced settings");
                    });
                    context.waitTicks(2);
                    context.takeScreenshot("p3-settings-" + locale + "-" + size[0] + "x" + size[1]);
                    context.runOnClient(client -> {
                        client.currentScreen.keyPressed(new KeyInput(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                        check(client.currentScreen instanceof WeatherStationScreen, "Esc lost valid parent");
                    });
                }
                restoreWindow(context, window);
                context.runOnClient(client -> client.setScreen(new ClientSettingsScreen(null)));
                context.waitTicks(2);
                context.takeScreenshot("p3-settings-centered-" + locale);
                context.runOnClient(client -> client.currentScreen.close());
            }
            preferences(context);
            // The same captured identity check is used by both queued network callbacks.
            context.runOnClient(client -> {
                var captured = ClientSession.capture(client);
                check(captured.matches(client), "Current identity rejected");
                check(!captured.matches(new Object(), captured.connection()), "Queued old-world task accepted");
                check(!captured.matches(captured.world(), new Object()), "Queued old-connection task accepted");
                var screen = new ClientSettingsScreen(null);
                client.setScreen(screen);
                SkySignsClient.acceptStationReport(client, new ClientSession(new Object(), captured.connection()), report);
                check(client.currentScreen == screen, "Stale callback changed screen");
                screen.close();
            });
            // Real parent invalidation: distance, water contact, removed block, and world/connection identity.
            var station = context.computeOnClient(client -> new WeatherStationScreen(report));
            context.runOnClient(client -> {
                check(station.isValidParent(client), "Fixture station not valid");
                var player = client.player;
                var location = new net.minecraft.util.math.Vec3d(player.getX(), player.getY(), player.getZ());
                player.setPosition(location.add(100, 0, 0));
                check(!station.isValidParent(client), "Distant station stayed valid");
                player.setPosition(location);
            });
            context.runOnClient(client -> {
                var previous = client.world.getBlockState(pos.up());
                try {
                    client.world.setBlockState(pos.up(), Blocks.WATER.getDefaultState());
                    check(!station.isValidParent(client), "Submerged station stayed valid");
                    client.setScreen(new ClientSettingsScreen(station));
                    client.currentScreen.close();
                    check(client.currentScreen == null, "Submerged parent restored");
                } finally { client.world.setBlockState(pos.up(), previous); }
            });
            context.runOnClient(client -> client.setScreen(new ClientSettingsScreen(station)));
            singleplayer.getServer().runOnServer(server -> server.getOverworld().setBlockState(pos, Blocks.AIR.getDefaultState()));
            context.waitFor(client -> !station.isValidParent(client));
            context.runOnClient(client -> {
                client.currentScreen.close();
                check(client.currentScreen == null, "Removed station restored");
            });
            singleplayer.getServer().runOnServer(server -> server.getOverworld().setBlockState(pos,
                    cn.skysigns.registry.ModBlocks.WEATHER_STATION.getDefaultState()));
            context.waitFor(station::isValidParent);
            context.runOnClient(client -> {
                var world = client.world;
                client.setScreen(new ClientSettingsScreen(station));
                try {
                    client.world = null;
                    check(!station.isValidParent(client), "Old-world parent stayed valid");
                    client.currentScreen.close();
                    check(client.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen,
                            "Worldless return did not use vanilla title screen");
                } finally { client.world = world; }
                client.setScreen(new WeatherStationScreen(report));
            });
            System.out.println("P3_CLIENT_OK key/station/async/parent/keyboard/narration/layout/languages/save");
        } finally {
            restoreWindow(context, window);
            changeLanguage(context, language);
            context.runOnClient(client -> client.setScreen(new WeatherStationScreen(report)));
        }
    }

    private static void restoreWindow(ClientGameTestContext context, int[] original) {
        context.runOnClient(client -> {
            client.options.getGuiScale().setValue(original[2]);
            client.getWindow().setWindowedSize(original[0], original[1]);
        });
        context.waitFor(client -> client.getWindow().getWidth() == original[0]
                && client.getWindow().getHeight() == original[1]);
        context.waitTicks(2);
    }

    private static void preferences(ClientGameTestContext context) {
        context.runOnClient(client -> {
            try {
                var directory = Files.createTempDirectory("skysigns-p3-screen-");
                var path = directory.resolve("client.properties");
                var config = new ClientConfig(path, message -> {});
                config.save();
                config.setStrength(0.43F);
                var screen = new ClientSettingsScreen(null, config);
                client.setScreen(screen);
                var controls = buttons(screen);
                press(controls.getFirst());
                check(!config.enabled(), "Toggle not applied immediately");
                var slider = descendants(screen).stream().filter(SliderWidget.class::isInstance)
                        .map(SliderWidget.class::cast).findFirst().orElseThrow();
                check(!slider.active, "Disabled tilt left slider enabled");
                near(config.strength(), 0.43F);
                press(controls.getFirst());
                check(slider.active, "Re-enabled tilt did not restore slider");
                slider.setFocused(true);
                slider.keyPressed(new KeyInput(GLFW.GLFW_KEY_ENTER, 0, 0));
                slider.keyPressed(new KeyInput(GLFW.GLFW_KEY_RIGHT, 0, 0));
                check(config.strength() > 0.43F, "Keyboard slider not applied");
                screen.close();
                near(new ClientConfig(path, message -> {}).strength(), config.strength());
                // Malformed disk creates a portable, repeatable UI save failure without touching player config.
                config.setStrength(0.24F);
                Files.writeString(path, "bad=" + "\\" + "uBAD!");
                client.setScreen(new ClientSettingsScreen(null, config));
                client.currentScreen.close();
                check(client.currentScreen instanceof ClientSettingsScreen && config.dirty(), "Failed save silently closed");
                check(client.currentScreen.getNarratedTitle().getString().contains(Text.translatable(
                        "screen.skysigns.settings.save_failed").getString()), "Save error not narrated");
                Files.writeString(path, "future=retained\n");
                press(buttons(client.currentScreen).stream().filter(button -> button.getMessage().equals(
                        Text.translatable("screen.skysigns.settings.retry"))).findFirst().orElseThrow());
                check(client.currentScreen == null && !config.dirty(), "UI retry did not save and return");
                near(new ClientConfig(path, message -> {}).strength(), 0.24F);
                // Explicit return after failure retains active memory without claiming persistence.
                config.setStrength(0.31F);
                Files.writeString(path, "bad=" + "\\" + "uBAD!");
                client.setScreen(new ClientSettingsScreen(null, config));
                client.currentScreen.close();
                press(buttons(client.currentScreen).stream().filter(button -> button.getMessage().equals(
                        Text.translatable("screen.skysigns.settings.return_unsaved"))).findFirst().orElseThrow());
                check(client.currentScreen == null && config.dirty(), "Unsaved return cleared memory/dirty state");
                near(config.strength(), 0.31F);
                Files.delete(path); Files.delete(directory);
                visual();
                irisCompatibility();
            } catch (java.io.IOException exception) { throw new AssertionError(exception); }
        });
    }

    private static void visual() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        var config = SkySignsClient.config();
        boolean enabled = config.enabled(); float strength = config.strength();
        var wind = ClientWindState.INSTANCE;
        try {
            config.setEnabled(true); config.setStrength(1);
            for (int i = 0; i < 4; i++) {
                wind.reset(); wind.accept(client.world, client.getNetworkHandler(), i * 90, 1);
                near(WindPrecipitationVisual.topOffsetX(8), new float[]{0, -2.4F, 0, 2.4F}[i]);
                near(WindPrecipitationVisual.topOffsetZ(8), new float[]{2.4F, 0, -2.4F, 0}[i]);
            }
            config.setStrength(0.5F);
            near(WindPrecipitationVisual.topOffsetX(4), 0.6F);
            near(wind.strength(), 1);
            config.setEnabled(false);
            near(WindPrecipitationVisual.topOffsetX(8), 0);
            near(wind.strength(), 1);
            wind.synchronize(new Object(), client.getNetworkHandler());
            near(WindPrecipitationVisual.topOffsetX(8), 0);
        } finally {
            config.setEnabled(enabled); config.setStrength(strength);
            wind.reset();
        }
    }

    /** Exercise optional API responses/failure through the real reflective query, without requiring Iris. */
    private static void irisCompatibility() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        var config = SkySignsClient.config();
        var wind = ClientWindState.INSTANCE;
        String[] names = {"irisPresent", "shaderPackActive", "apiFailed", "irisApi", "irisShaderPackInUse"};
        var fields = new java.lang.reflect.Field[names.length];
        var previous = new Object[names.length];
        try {
            for (int i = 0; i < names.length; i++) {
                fields[i] = WindPrecipitationVisual.class.getDeclaredField(names[i]);
                fields[i].setAccessible(true); previous[i] = fields[i].get(null);
            }
            var api = new ShaderApiFixture();
            fields[0].set(null, true); fields[1].set(null, false); fields[2].set(null, false);
            fields[3].set(null, api);
            fields[4].set(null, ShaderApiFixture.class.getMethod("isShaderPackInUse"));
            wind.reset(); wind.accept(client.world, client.getNetworkHandler(), 90, 0.75F);
            boolean enabled = config.enabled(); float strength = config.strength();
            for (boolean active : new boolean[]{true, false}) {
                api.active = active;
                WindPrecipitationVisual.tick(client);
                check((WindPrecipitationVisual.unavailableReason() != null) == active, "Iris availability did not track API");
                if (active) near(WindPrecipitationVisual.topOffsetX(8), 0);
                client.setScreen(new ClientSettingsScreen(null));
                var toggle = buttons(client.currentScreen).getFirst();
                check(toggle.active != active, "Iris reason did not disable controls");
                near(wind.strength(), 0.75F); near(wind.direction(), 90);
                check(config.enabled() == enabled && config.strength() == strength, "Iris changed preferences");
            }
            api.fail = true;
            WindPrecipitationVisual.tick(client);
            check("screen.skysigns.settings.iris_error".equals(WindPrecipitationVisual.unavailableReason()), "API failure not explained");
            near(WindPrecipitationVisual.topOffsetX(8), 0);
            client.currentScreen.tick();
            check(client.currentScreen.getNarratedTitle().getString().contains(Text.translatable(
                    "screen.skysigns.settings.iris_error").getString()), "API failure status not narrated");
            near(wind.strength(), 0.75F);
            client.currentScreen.close();
            System.out.println("P3_IRIS_FIXTURE_OK simulated API on/off/exception; real Iris compatibility unverified");
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        finally {
            try { for (int i = 0; i < fields.length; i++) if (fields[i] != null) fields[i].set(null, previous[i]); }
            catch (IllegalAccessException exception) { throw new AssertionError(exception); }
            wind.reset();
        }
    }

    public static final class ShaderApiFixture {
        boolean active;
        boolean fail;
        public boolean isShaderPackInUse() {
            if (fail) throw new IllegalStateException("P3 optional API failure fixture");
            return active;
        }
    }

    private static void press(ButtonWidget button) { button.onPress(new KeyInput(GLFW.GLFW_KEY_ENTER, 0, 0)); }
    private static List<ButtonWidget> buttons(Screen screen) {
        return descendants(screen).stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).toList();
    }
    private static List<Element> descendants(ParentElement parent) {
        var elements = new ArrayList<Element>();
        for (var child : parent.children()) {
            elements.add(child);
            if (child instanceof ParentElement container) elements.addAll(descendants(container));
        }
        return elements;
    }
    private static void changeLanguage(ClientGameTestContext context, String language) {
        var reload = context.computeOnClient(client -> {
            client.getLanguageManager().setLanguage(language);
            client.options.language = language;
            return client.reloadResources();
        });
        context.waitFor(client -> reload.isDone()); reload.join();
        context.waitFor(client -> client.getOverlay() == null);
    }
}
