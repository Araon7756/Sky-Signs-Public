package cn.skysigns.weather;

import net.minecraft.text.Text;

public enum SkyWeatherType {
    CLEAR("weather.skysigns.clear", false, false),
    RAIN("weather.skysigns.rain", true, false),
    STORM("weather.skysigns.storm", true, true);

    private final String translationKey;
    private final boolean raining;
    private final boolean thundering;

    SkyWeatherType(String translationKey, boolean raining, boolean thundering) {
        this.translationKey = translationKey;
        this.raining = raining;
        this.thundering = thundering;
    }

    public Text displayName() {
        return Text.translatable(translationKey);
    }

    public boolean raining() {
        return raining;
    }

    public boolean thundering() {
        return thundering;
    }
}
