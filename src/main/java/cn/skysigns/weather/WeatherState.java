package cn.skysigns.weather;

import cn.skysigns.SkySigns;
import cn.skysigns.storage.SaveDataMigrations;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public final class WeatherState extends PersistentState {
    public static final PersistentStateType<WeatherState> TYPE = new PersistentStateType<>(
            "skysigns_weather", WeatherState::new,
            NbtCompound.CODEC.xmap(WeatherState::fromNbt, WeatherState::toNbt), DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private SkyWeatherType type = SkyWeatherType.CLEAR;
    private WeatherPlan plan = WeatherPlan.normal(SkyWeatherType.CLEAR, SkyWeatherType.RAIN, 0, 0);
    private boolean automatic = true, initialized, paused, readOnly;
    private long pauseAt;
    private NbtCompound storedData = new NbtCompound();

    public WeatherState() {}
    public WeatherState(SkyWeatherType type, SkyWeatherType forecast, long s, long t) { set(type, forecast, s, t); }
    public boolean isReadOnly() { return readOnly; }
    public SkyWeatherType type() { return type; }
    public SkyWeatherType forecastType() { return plan.target(); }
    public long phaseStartedAt() { return plan.start(); }
    public long nextAutomaticChange() { return plan.change(); }
    public boolean automatic() { return automatic; }
    public boolean initialized() { return initialized; }
    public WeatherPlan plan() { return plan; }
    public boolean paused() { return paused; }
    public long pauseAt() { return pauseAt; }
    public long effectiveTime(long now) { return automatic && paused ? pauseAt : now; }

    public void install(WeatherPlan plan, SkyWeatherType type, boolean automatic) {
        writable(); plan.validate(automatic);
        this.plan = plan; this.type = type; this.automatic = automatic; this.initialized = true;
        if (!automatic) { paused = false; pauseAt = 0; }
        markDirty();
    }
    public void set(SkyWeatherType type, SkyWeatherType forecast, long s, long t) {
        install(WeatherPlan.normal(type, forecast, s, t), type, true);
    }
    public void setForecastType(SkyWeatherType target) {
        writable();
        install(new WeatherPlan(plan.from(), target, plan.start(), plan.precursor(), plan.change(), plan.end(),
                plan.curve(), plan.anchorAt(), plan.anchorEnd(), plan.anchor()), type, automatic);
    }
    public void setAutomatic(boolean automatic) {
        writable(); this.automatic = automatic;
        if (!automatic) { paused = false; pauseAt = 0; }
        markDirty();
    }
    public void setType(SkyWeatherType type) { writable(); this.type = type; markDirty(); }
    public void pause(long now) { writable(); paused = true; pauseAt = now; markDirty(); }
    public void resume(long now) {
        writable(); WeatherPlan shifted = plan.shift(Math.subtractExact(now, pauseAt));
        plan = shifted; paused = false; pauseAt = 0; markDirty();
    }
    public void shift(long delta) {
        writable(); WeatherPlan shifted = plan.shift(delta);
        long shiftedPause = paused ? Math.addExact(pauseAt, delta) : 0;
        plan = shifted; pauseAt = shiftedPause; markDirty();
    }
    /** Runtime overflow also preserves the last serializable state, never inventing a new plan. */
    public void protect() { storedData = toNbt(); readOnly = true; }
    private void writable() { SaveDataMigrations.requireWritable(readOnly, SaveDataMigrations.Kind.WEATHER); }

    private static WeatherState fromNbt(NbtCompound raw) {
        var migration = SaveDataMigrations.upgrade(raw, SaveDataMigrations.Kind.WEATHER);
        WeatherState state = new WeatherState();
        state.storedData = migration.data().copy(); state.readOnly = migration.readOnly();
        if (!state.readOnly) {
            try {
                NbtCompound n = migration.data();
                state.type = WeatherPlan.type(n, "type"); state.plan = WeatherPlan.read(n);
                state.automatic = WeatherPlan.flag(n, "automatic"); state.initialized = WeatherPlan.flag(n, "initialized");
                state.paused = WeatherPlan.flag(n, "paused"); state.pauseAt = WeatherPlan.time(n, "pause_at");
                state.plan.validate(state.automatic);
                if (state.type != state.plan.from() && state.type != state.plan.target()) throw new IllegalArgumentException("Type outside plan");
                if ((!state.automatic && state.paused) || (!state.paused && state.pauseAt != 0)) throw new IllegalArgumentException("Invalid pause");
                if (state.paused) {
                    if (state.pauseAt < state.plan.start()) throw new IllegalArgumentException("Pause before plan");
                    Math.subtractExact(state.pauseAt, state.plan.start());
                }
                if (migration.migrated()) state.markDirty();
            } catch (IllegalArgumentException | ArithmeticException invalid) {
                state.storedData = raw.copy(); state.readOnly = true;
                SkySigns.LOGGER.warn("Invalid Sky Signs WEATHER plan: {}; keeping original NBT read-only", invalid.getMessage());
            }
        }
        return state;
    }
    private NbtCompound toNbt() {
        NbtCompound n = storedData.copy();
        if (readOnly) return n;
        n.putInt(SaveDataMigrations.VERSION_KEY, 2); plan.write(n);
        n.putString("type", type.name()); n.putBoolean("automatic", automatic); n.putBoolean("initialized", initialized);
        n.putBoolean("paused", paused); n.putLong("pause_at", pauseAt);
        return n;
    }
}
