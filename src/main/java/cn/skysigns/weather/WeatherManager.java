package cn.skysigns.weather;

import cn.skysigns.SkySigns;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;
import net.minecraft.world.level.ServerWorldProperties;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

public final class WeatherManager {
    private static final int INITIAL_CHANGE_DELAY = 1200, MANUAL_DURATION = 72000;
    private static final Map<ServerWorld, Runtime> RUNTIMES = new WeakHashMap<>();
    private static final class Runtime {
        int ownWrites;
        Long lastTick;
        WeatherPlan precursorNotified;
        final ArrayDeque<WeatherPlan.Bases> events = new ArrayDeque<>();
    }
    private WeatherManager() {}
    private static Runtime runtime(ServerWorld w) { return RUNTIMES.computeIfAbsent(w, ignored -> new Runtime()); }
    /** Loading may migrate deterministically; querying never initializes or consumes RNG. */
    public static WeatherState get(ServerWorld world) {
        WeatherState state = world.getPersistentStateManager().get(WeatherState.TYPE);
        return state != null ? state : new WeatherState();
    }
    public static boolean applicable(ServerWorld w) { return w.getDimension().hasSkyLight() && !w.getDimension().hasCeiling(); }
    public static SkyWeatherType readVanillaWeather(ServerWorld w) {
        if (!applicable(w)) return SkyWeatherType.CLEAR;
        var p = w.getLevelProperties();
        return p.isRaining() ? (p.isThundering() ? SkyWeatherType.STORM : SkyWeatherType.RAIN) : SkyWeatherType.CLEAR;
    }
    public record View(SkyWeatherType type, WeatherPlan plan, WeatherPlan.Bases bases, WeatherPlan.Stage stage,
                       long time, boolean automatic, boolean paused, boolean readOnly, boolean applicable) {
        public int progressPercent() {
            return automatic ? (int) (100 * WeatherPlan.fraction(time, plan.start(), plan.change())) : 0;
        }
        public int secondsRemaining() {
            if (!automatic) return -1;
            if (time >= plan.change()) return 0;
            return (int) Math.min(Integer.MAX_VALUE, remainingTicks(plan.change(), time) / 20);
        }
        public double pressureDelta() { return paused || readOnly || !applicable ? 0 : plan.pressureDelta(time, automatic); }
    }
    public static View view(ServerWorld w) {
        WeatherState s = get(w);
        if (s.isReadOnly() || !s.initialized() || !applicable(w)) {
            SkyWeatherType actual = readVanillaWeather(w);
            WeatherPlan p = WeatherPlan.normal(actual, actual, 0, 0);
            return new View(actual, p, WeatherPlan.Bases.of(actual), WeatherPlan.Stage.STABLE,
                    w.getTime(), false, false, s.isReadOnly(), applicable(w));
        }
        long t = s.effectiveTime(w.getTime());
        return new View(s.type(), s.plan(), s.plan().bases(t, s.automatic()), s.plan().stage(t, s.automatic()),
                t, s.automatic(), s.paused(), false, true);
    }
    private static WeatherState prepare(ServerWorld w) {
        if (!applicable(w)) return get(w);
        WeatherState s = w.getPersistentStateManager().getOrCreate(WeatherState.TYPE);
        if (s.isReadOnly()) return s;
        try {
            long now = w.getTime();
            Runtime r = runtime(w);
            // Never infer a rollback across process/save loads.
            if (r.lastTick != null && now < r.lastTick) {
                boolean notified = r.precursorNotified == s.plan();
                s.shift(Math.subtractExact(now, r.lastTick));
                if (notified) r.precursorNotified = s.plan();
            }
            r.lastTick = now;
            if (!s.initialized()) {
                SkyWeatherType actual = readVanillaWeather(w);
                if (s.automatic()) {
                    s.install(WeatherPlan.normal(actual, chooseNext(w, actual), now, Math.addExact(now, INITIAL_CHANGE_DELAY)), actual, true);
                } else {
                    s.install(WeatherPlan.manual(actual, now, WeatherPlan.Bases.of(actual)), actual, false);
                }
            }
            if (s.automatic()) {
                boolean cycle = w.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE);
                if (!cycle && !s.paused()) s.pause(now);
                else if (cycle && s.paused()) {
                    boolean notified = r.precursorNotified == s.plan();
                    s.resume(now);
                    if (notified) r.precursorNotified = s.plan();
                }
            }
        } catch (ArithmeticException invalid) { protect(s, invalid); }
        return s;
    }
    private static void protect(WeatherState s, ArithmeticException invalid) {
        s.protect(); SkySigns.LOGGER.warn("Sky Signs weather timeline overflow; preserving plan read-only", invalid);
    }
    /** Called before tickWeather, before vanilla can decrement a timer to zero and toggle. */
    public static void beforeWeather(ServerWorld w) {
        WeatherState s = prepare(w);
        if (s.isReadOnly() || !applicable(w)) return;
        try { adoptFlags(w, s); }
        catch (ArithmeticException invalid) { protect(s, invalid); return; }
        if (s.automatic()) protectTimers(w, s);
        WindManager.initialize(w); // weather first; queries never initialize either subsystem
    }
    private static long remainingTicks(long deadline, long now) {
        if (now >= deadline) return 0;
        try { return Math.subtractExact(deadline, now); }
        catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
    }
    private static void protectTimers(ServerWorld w, WeatherState s) {
        long t = s.effectiveTime(w.getTime());
        long cutoff = t < s.plan().change() ? s.plan().change() : s.plan().end();
        // +2 accounts for vanilla's decrement-before-toggle and the following clock increment.
        long remaining = remainingTicks(cutoff, t);
        int duration = remaining >= Integer.MAX_VALUE - 2L ? Integer.MAX_VALUE : (int) remaining + 2;
        ServerWorldProperties p = (ServerWorldProperties) w.getLevelProperties();
        p.setClearWeatherTime(s.type() == SkyWeatherType.CLEAR ? duration : 0);
        p.setRainTime(duration); p.setThunderTime(duration);
    }
    /** Injected immediately after tickTime, ahead of scheduled blocks/entities and meter sampling. */
    public static void tick(ServerWorld w) {
        WeatherState s = prepare(w);
        if (s.isReadOnly() || !applicable(w)) return;
        try {
            adoptFlags(w, s);
            if (!s.automatic() || s.paused()) return;
            long t = s.effectiveTime(w.getTime());
            WeatherPlan p = s.plan();
            if (t >= p.change() && s.type() != p.target()) {
                s.setType(p.target()); applyVanilla(w, p.target(), 2);
                Text message = Text.translatable(WindManager.get(w).automatic()
                        ? "message.skysigns.weather_changed.wind_auto" : "message.skysigns.weather_changed.wind_manual", p.target().displayName());
                w.getPlayers().forEach(player -> player.sendMessage(message, false));
            }
            if (t >= p.end()) {
                WeatherPlan.Bases bases = p.bases(t, true);
                SkyWeatherType next = p.target();
                long change = Math.addExact(t, chooseDuration(w, next));
                // LEGACY humidity/wind and zero intervals carry their actual bases into a short rebase.
                WeatherPlan plan = p.curve() == WeatherPlan.Curve.LEGACY || (p.precursor() == p.change() && p.change() == p.end())
                        ? WeatherPlan.rebase(next, chooseNext(w, next), t, change, bases)
                        : WeatherPlan.normal(next, chooseNext(w, next), t, change);
                s.install(plan, next, true); // one completion and one draw; never catch up missed cycles
            }
            if (t >= s.plan().precursor() && t < s.plan().change() && runtime(w).precursorNotified != s.plan()) {
                runtime(w).precursorNotified = s.plan();
                WindManager.precursor(w, s.plan().change());
            }
        } catch (ArithmeticException invalid) { protect(s, invalid); }
    }
    private static void adoptFlags(ServerWorld w, WeatherState s) {
        SkyWeatherType actual = readVanillaWeather(w);
        if (actual != s.type()) replace(w, s, actual, s.plan().bases(s.effectiveTime(w.getTime()), s.automatic()), -1);
    }
    private static void replace(ServerWorld w, WeatherState s, SkyWeatherType actual, WeatherPlan.Bases anchor, int duration) {
        long t = s.effectiveTime(w.getTime());
        if (s.automatic()) {
            long change = Math.addExact(t, duration >= 0 ? duration : chooseDuration(w, actual));
            s.install(WeatherPlan.rebase(actual, chooseNext(w, actual), t, change, anchor), actual, true);
        } else s.install(WeatherPlan.manual(actual, t, anchor), actual, false);
    }
    /** Only one notification path per successful setWeather/resetWeather invocation, no tick deduplication. */
    public static void beginExternal(ServerWorld w) {
        if (runtime(w).ownWrites != 0) return;
        WeatherState s = prepare(w);
        if (!s.isReadOnly() && applicable(w)) runtime(w).events.push(s.plan().bases(s.effectiveTime(w.getTime()), s.automatic()));
    }
    public static void endExternal(ServerWorld w, int duration) {
        Runtime r = runtime(w);
        if (r.ownWrites != 0 || r.events.isEmpty()) return;
        var anchor = r.events.pop(); WeatherState s = get(w);
        if (s.isReadOnly() || !applicable(w)) return;
        try { replace(w, s, readVanillaWeather(w), anchor, duration); }
        catch (ArithmeticException invalid) { protect(s, invalid); }
    }
    public static void setManual(ServerWorld w, SkyWeatherType type) {
        WeatherState s = prepare(w);
        if (s.isReadOnly() || !applicable(w)) return;
        var anchor = view(w).bases();
        try {
            s.install(WeatherPlan.manual(type, w.getTime(), anchor), type, false);
            applyVanilla(w, type, MANUAL_DURATION);
        } catch (ArithmeticException invalid) { protect(s, invalid); }
    }
    public static void enableAutomatic(ServerWorld w) {
        WeatherState s = prepare(w);
        if (s.isReadOnly() || !applicable(w)) return;
        var anchor = view(w).bases(); SkyWeatherType actual = readVanillaWeather(w);
        try {
            long now = w.getTime();
            s.install(WeatherPlan.rebase(actual, chooseNext(w, actual), now, Math.addExact(now, chooseDuration(w, actual)), anchor), actual, true);
            if (!w.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) s.pause(now);
        } catch (ArithmeticException invalid) { protect(s, invalid); }
    }
    private static void applyVanilla(ServerWorld w, SkyWeatherType type, int duration) {
        Runtime r = runtime(w); r.ownWrites++;
        try { w.setWeather(type == SkyWeatherType.CLEAR ? duration : 0,
                type == SkyWeatherType.CLEAR ? 0 : duration, type.raining(), type.thundering()); }
        finally { r.ownWrites--; }
    }
    public static Text describe(ServerWorld world) { return describe(view(world)); }
    public static Text describe(View v) {
        if (!v.applicable) return Text.translatableWithFallback("weather.skysigns.description.inapplicable", "Weather observations are not applicable here");
        if (v.readOnly) return Text.translatable("message.skysigns.save_read_only");
        if (v.paused) return Text.translatableWithFallback("weather.skysigns.description.paused", "%s (automatic forecast paused)", v.type.displayName());
        if (v.stage == WeatherPlan.Stage.REBASE) return Text.translatableWithFallback("weather.skysigns.description.rebase", "%s (readings adjusting)", v.type.displayName());
        if (!v.automatic) return Text.translatable("weather.skysigns.description.manual", v.type.displayName());
        if (v.stage == WeatherPlan.Stage.TRANSITION) return Text.translatableWithFallback(
                v.type == SkyWeatherType.CLEAR ? "weather.skysigns.description.easing" : "weather.skysigns.description.transition",
                v.type == SkyWeatherType.CLEAR ? "%s (easing)" : "%s (transitioning)", v.type.displayName());
        int seconds = v.secondsRemaining();
        return Text.translatable("weather.skysigns.description.auto", v.type.displayName(), seconds / 60, seconds % 60);
    }
    public static Text forecast(View v) {
        if (!v.applicable) return Text.translatableWithFallback("weather.skysigns.description.inapplicable", "Weather observations are not applicable here");
        if (v.readOnly) return Text.translatable("message.skysigns.save_read_only");
        if (!v.automatic) return Text.translatable("reading.skysigns.forecast.manual");
        if (v.stage == WeatherPlan.Stage.TRANSITION) return Text.translatableWithFallback("reading.skysigns.forecast.transition",
                "Transition to %s; next forecast not scheduled", v.type.displayName());
        if (v.paused) return Text.translatableWithFallback("reading.skysigns.forecast.paused", "%s (forecast paused)", v.plan.target().displayName());
        int seconds = v.secondsRemaining();
        String key = seconds <= 60 ? "reading.skysigns.forecast.soon" : seconds <= 180 ? "reading.skysigns.forecast.near" : "reading.skysigns.forecast.later";
        return Text.translatable(key, v.plan.target().displayName());
    }
    private static SkyWeatherType chooseNext(ServerWorld w, SkyWeatherType type) {
        float roll = w.getRandom().nextFloat();
        return switch (type) {
            case CLEAR -> roll < .72F ? SkyWeatherType.RAIN : SkyWeatherType.STORM;
            case RAIN -> roll < .62F ? SkyWeatherType.CLEAR : SkyWeatherType.STORM;
            case STORM -> roll < .68F ? SkyWeatherType.RAIN : SkyWeatherType.CLEAR;
        };
    }
    private static int chooseDuration(ServerWorld w, SkyWeatherType type) {
        int minimum = switch (type) { case CLEAR -> 4; case RAIN -> 3; case STORM -> 2; };
        int extra = type == SkyWeatherType.CLEAR ? 4 : 3;
        return 1200 * (minimum + w.getRandom().nextInt(extra));
    }
}
