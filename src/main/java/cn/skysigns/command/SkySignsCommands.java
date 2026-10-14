package cn.skysigns.command;

import cn.skysigns.weather.WindManager;
import cn.skysigns.weather.WindState;
import cn.skysigns.weather.AtmosphericReadings;
import cn.skysigns.weather.SkyWeatherType;
import cn.skysigns.weather.WeatherManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class SkySignsCommands {
    private SkySignsCommands() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("skysigns")
                        .then(literal("status")
                                .executes(context -> showStatus(context.getSource())))
                        .then(literal("forecast")
                                .executes(context -> showForecast(context.getSource())))
                        .then(literal("wind")
                                .then(literal("set")
                                        .requires(source -> source.hasPermissionLevel(2))
                                        .then(argument("degrees", IntegerArgumentType.integer(0, 359))
                                                .then(argument("strength", IntegerArgumentType.integer(0, 100))
                                                        .executes(context -> setWind(
                                                                context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "degrees"),
                                                                IntegerArgumentType.getInteger(context, "strength")
                                                        )))))
                                .then(literal("auto")
                                        .requires(source -> source.hasPermissionLevel(2))
                                        .executes(context -> enableAutomaticWind(context.getSource()))))
                        .then(literal("weather")
                                .then(literal("status")
                                        .executes(context -> showStatus(context.getSource())))
                                .then(literal("set")
                                        .requires(source -> source.hasPermissionLevel(2))
                                        .then(literal("clear")
                                                .executes(context -> setWeather(context.getSource(), SkyWeatherType.CLEAR)))
                                        .then(literal("rain")
                                                .executes(context -> setWeather(context.getSource(), SkyWeatherType.RAIN)))
                                        .then(literal("storm")
                                                .executes(context -> setWeather(context.getSource(), SkyWeatherType.STORM))))
                                .then(literal("auto")
                                        .requires(source -> source.hasPermissionLevel(2))
                                        .executes(context -> enableAutomaticWeather(context.getSource()))))
        ));
    }

    private static int showStatus(ServerCommandSource source) {
        WindState wind = WindManager.get(source.getWorld());
        source.sendFeedback(() -> statusText(source.getWorld(), wind), false);
        return Math.round(wind.strength() * 100.0F);
    }

    private static int setWind(ServerCommandSource source, int degrees, int strengthPercent) {
        if (WindManager.get(source.getWorld()).isReadOnly()) return readOnlyError(source);
        WindManager.setManual(source.getWorld(), degrees, strengthPercent / 100.0F);
        WindState wind = WindManager.get(source.getWorld());
        source.sendFeedback(() -> Text.translatable("message.skysigns.wind_set", WindManager.describe(wind)), true);
        return strengthPercent;
    }

    private static int showForecast(ServerCommandSource source) {
        BlockPos pos = BlockPos.ofFloored(source.getPosition());
        AtmosphericReadings.Snapshot readings = AtmosphericReadings.measure(source.getWorld(), pos);
        source.sendFeedback(() -> Text.translatable("message.skysigns.forecast",
                readings.pressureText(), readings.humidityText(),
                readings.localPrecipitation(), readings.forecast()), false);
        return readings.humidityPercent();
    }

    private static int enableAutomaticWind(ServerCommandSource source) {
        if (WindManager.get(source.getWorld()).isReadOnly()) return readOnlyError(source);
        WindManager.enableAutomatic(source.getWorld());
        source.sendFeedback(() -> Text.translatable("message.skysigns.wind_auto"), true);
        return 1;
    }

    private static int setWeather(ServerCommandSource source, SkyWeatherType type) {
        if (WeatherManager.get(source.getWorld()).isReadOnly()) return readOnlyError(source);
        if (!WeatherManager.applicable(source.getWorld())) return inapplicableError(source);
        WeatherManager.setManual(source.getWorld(), type);
        if (WeatherManager.get(source.getWorld()).isReadOnly()) return readOnlyError(source);
        source.sendFeedback(() -> Text.translatable("message.skysigns.weather_set", type.displayName()), true);
        return 1;
    }

    private static int enableAutomaticWeather(ServerCommandSource source) {
        if (WeatherManager.get(source.getWorld()).isReadOnly()) return readOnlyError(source);
        if (!WeatherManager.applicable(source.getWorld())) return inapplicableError(source);
        WeatherManager.enableAutomatic(source.getWorld());
        if (WeatherManager.get(source.getWorld()).isReadOnly()) return readOnlyError(source);
        source.sendFeedback(() -> Text.translatable("message.skysigns.weather_auto"), true);
        return 1;
    }

    private static Text statusText(ServerWorld world, WindState wind) {
        return Text.translatable("message.skysigns.status",
                WeatherManager.describe(world), WindManager.describe(wind),
                Text.translatable(wind.automatic() ? "mode.skysigns.auto" : "mode.skysigns.manual"));
    }

    private static int inapplicableError(ServerCommandSource source) {
        source.sendError(Text.translatableWithFallback("weather.skysigns.description.inapplicable",
                "Weather observations are not applicable here"));
        return 0;
    }

    private static int readOnlyError(ServerCommandSource source) {
        source.sendError(Text.translatable("message.skysigns.save_read_only"));
        return 0;
    }
}
