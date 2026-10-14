package cn.skysigns.weather;

import net.minecraft.nbt.*;

/** Immutable saved promise. Evaluation has no world access, writes or random draws. */
public record WeatherPlan(SkyWeatherType from, SkyWeatherType target, long start, long precursor,
                          long change, long end, Curve curve, long anchorAt, long anchorEnd, Bases anchor) {
    public static final long PRECURSOR_TICKS = 1200, TRANSITION_TICKS = 400, REBASE_TICKS = 400;
    public enum Curve { NORMAL, LEGACY, REBASE }
    public enum Stage { STABLE, PRECURSOR, TRANSITION, REBASE, MANUAL }

    public record Bases(float pressure, float wetHumidity, float dryHumidity, float windMin, float windMax) {
        public Bases {
            if (!Float.isFinite(pressure) || !Float.isFinite(wetHumidity) || !Float.isFinite(dryHumidity)
                    || !Float.isFinite(windMin) || !Float.isFinite(windMax)
                    || windMin < 0 || windMax > 1 || windMin > windMax) {
                throw new IllegalArgumentException("Invalid weather anchors");
            }
        }
        public static Bases of(SkyWeatherType type) {
            return switch (type) {
                case CLEAR -> new Bases(1020, 38, 38, .05F, .52F);
                case RAIN -> new Bases(1005, 82, 46, .35F, .78F);
                case STORM -> new Bases(989, 93, 52, .72F, 1F);
            };
        }
        public Bases toward(Bases b, double w) {
            return new Bases(lerp(pressure, b.pressure, w), lerp(wetHumidity, b.wetHumidity, w),
                    lerp(dryHumidity, b.dryHumidity, w), lerp(windMin, b.windMin, w), lerp(windMax, b.windMax, w));
        }
        private static float lerp(float a, float b, double w) {
            if (w <= 0) return a;
            if (w >= 1) return b;
            return (float) (a + ((double) b - a) * w);
        }
    }

    public static WeatherPlan normal(SkyWeatherType from, SkyWeatherType target, long s, long t) {
        long length = Math.subtractExact(t, s);
        if (length < 0) throw new IllegalArgumentException("Reversed weather time");
        return new WeatherPlan(from, target, s, t - Math.min(PRECURSOR_TICKS, length / 2), t,
                Math.addExact(t, Math.min(TRANSITION_TICKS, length / 4)), Curve.NORMAL, s, s, Bases.of(from));
    }

    public static WeatherPlan rebase(SkyWeatherType from, SkyWeatherType target, long s, long t, Bases anchor) {
        WeatherPlan p = normal(from, target, s, t);
        long a = Math.addExact(s, Math.min(REBASE_TICKS, Math.subtractExact(t, s)));
        return new WeatherPlan(from, target, s, Math.max(p.precursor, a), t, p.end, Curve.REBASE, s, a, anchor);
    }

    public static WeatherPlan manual(SkyWeatherType type, long s, Bases anchor) {
        return new WeatherPlan(type, type, s, s, s, s, Curve.REBASE, s,
                Math.addExact(s, REBASE_TICKS), anchor);
    }

    public void validate(boolean automatic) {
        if (from == null || target == null || curve == null || anchor == null) throw new IllegalArgumentException("Missing plan");
        if (start > precursor || precursor > change || change > end || anchorAt != start || anchorEnd < anchorAt)
            throw new IllegalArgumentException("Reversed weather time");
        Math.subtractExact(end, start);
        Math.subtractExact(anchorEnd, anchorAt);
        if (automatic && anchorEnd > change) throw new IllegalArgumentException("Anchor passes change");
        if (curve != Curve.REBASE && anchorEnd != start) throw new IllegalArgumentException("Unexpected anchor interval");
        if (curve == Curve.REBASE && automatic && precursor < anchorEnd) throw new IllegalArgumentException("Overlapping curves");
        if (!automatic && curve == Curve.REBASE && (from != target || start != end))
            throw new IllegalArgumentException("Invalid manual plan");
    }

    public double weight(long t) {
        if (precursor == change && change == end) return 0; // next plan carries the unchanged bases
        if (t >= end) return 1;
        if (t <= precursor) return 0;
        if (precursor == change) return smooth(fraction(t, change, end));
        if (change == end) return smooth(fraction(t, precursor, change));
        return t < change ? .5 * smooth(fraction(t, precursor, change))
                : .5 + .5 * smooth(fraction(t, change, end));
    }

    public Bases bases(long t, boolean automatic) {
        Bases source = Bases.of(from);
        if (curve == Curve.LEGACY) {
            if (!automatic) return source;
            // Only pressure used the old whole-phase curve; retain old humidity and wind until T.
            float pressure = source.toward(Bases.of(target), smooth(fraction(t, start, change))).pressure;
            return new Bases(pressure, source.wetHumidity, source.dryHumidity, source.windMin, source.windMax);
        }
        if (curve == Curve.REBASE && t < anchorEnd) return anchor.toward(source, smooth(fraction(t, anchorAt, anchorEnd)));
        if (!automatic) return source;
        // A zero-length command must hand its pre-event bases to the one next plan.
        if (start == end) return anchor;
        return source.toward(Bases.of(target), weight(t));
    }

    public Stage stage(long t, boolean automatic) {
        if (curve == Curve.REBASE && t < anchorEnd) return Stage.REBASE;
        if (!automatic) return Stage.MANUAL;
        if (t >= change) return Stage.TRANSITION;
        return t >= precursor ? Stage.PRECURSOR : Stage.STABLE;
    }

    public double pressureDelta(long t, boolean automatic) {
        if (curve == Curve.REBASE && t >= anchorAt && t < anchorEnd) return Bases.of(from).pressure - anchor.pressure;
        if (!automatic) return 0;
        if (curve == Curve.LEGACY) return t >= start && t < change ? Bases.of(target).pressure - Bases.of(from).pressure : 0;
        return t >= precursor && t < end ? Bases.of(target).pressure - Bases.of(from).pressure : 0;
    }

    public WeatherPlan shift(long delta) {
        return new WeatherPlan(from, target, Math.addExact(start, delta), Math.addExact(precursor, delta),
                Math.addExact(change, delta), Math.addExact(end, delta), curve,
                Math.addExact(anchorAt, delta), Math.addExact(anchorEnd, delta), anchor);
    }

    public static double fraction(long t, long a, long b) {
        if (b <= a) return t >= b ? 1 : 0;
        if (t <= a) return 0;
        if (t >= b) return 1;
        return (double) Math.subtractExact(t, a) / Math.subtractExact(b, a);
    }
    public static double smooth(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    public static WeatherPlan read(NbtCompound n) {
        return new WeatherPlan(type(n, "from_type"), type(n, "forecast"), time(n, "phase_started_at"),
                time(n, "precursor_at"), time(n, "next_change"), time(n, "transition_end"),
                Curve.valueOf(string(n, "curve_mode")), time(n, "anchor_at"), time(n, "anchor_end"),
                new Bases(number(n, "pressure_anchor"), number(n, "humidity_wet_anchor"), number(n, "humidity_dry_anchor"),
                        number(n, "wind_min_anchor"), number(n, "wind_max_anchor")));
    }
    public void write(NbtCompound n) {
        n.putString("from_type", from.name()); n.putString("forecast", target.name());
        n.putLong("phase_started_at", start); n.putLong("precursor_at", precursor);
        n.putLong("next_change", change); n.putLong("transition_end", end);
        n.putString("curve_mode", curve.name()); n.putLong("anchor_at", anchorAt); n.putLong("anchor_end", anchorEnd);
        n.putFloat("pressure_anchor", anchor.pressure); n.putFloat("humidity_wet_anchor", anchor.wetHumidity);
        n.putFloat("humidity_dry_anchor", anchor.dryHumidity); n.putFloat("wind_min_anchor", anchor.windMin);
        n.putFloat("wind_max_anchor", anchor.windMax);
    }
    public static SkyWeatherType type(NbtCompound n, String key) { return SkyWeatherType.valueOf(string(n, key)); }
    private static String string(NbtCompound n, String key) {
        if (!(n.get(key) instanceof NbtString)) throw new IllegalArgumentException("Invalid " + key);
        return n.getString(key, "");
    }
    public static long time(NbtCompound n, String key) {
        if (!(n.get(key) instanceof NbtLong)) throw new IllegalArgumentException("Invalid " + key);
        return n.getLong(key, 0);
    }
    private static float number(NbtCompound n, String key) {
        if (!(n.get(key) instanceof NbtFloat)) throw new IllegalArgumentException("Invalid " + key);
        return n.getFloat(key, 0);
    }
    public static boolean flag(NbtCompound n, String key) {
        if (!(n.get(key) instanceof NbtByte) || (n.getByte(key, (byte) -1) != 0 && n.getByte(key, (byte) -1) != 1))
            throw new IllegalArgumentException("Invalid " + key);
        return n.getBoolean(key, false);
    }
}
