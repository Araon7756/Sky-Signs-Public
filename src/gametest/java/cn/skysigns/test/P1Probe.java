package cn.skysigns.test;

import cn.skysigns.weather.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import static cn.skysigns.weather.P1Assertions.check;

/** Observation only; does not cancel, redirect, or change vanilla/test waits. */
public final class P1Probe {
    public static WeatherPlan expected;
    public static int boundaries, waterChecks;
    public static void beforeBlockEntities(ServerWorld w) {
        if (expected == null || w.getRegistryKey() != World.OVERWORLD) return;
        long t = w.getTime(); var state = WeatherManager.get(w);
        if (t == expected.change()) {
            check(state.type() == expected.target() && WeatherManager.readVanillaWeather(w) == expected.target(), "T was not committed before block entities");
            var view = WeatherManager.view(w);
            check(view.progressPercent() == 100 && view.secondsRemaining() == 0, "Station v2 T semantics changed");
            check(view.bases().equals(expected.bases(t, true)), "T readings disagree with immutable view");
            boundaries++;
        }
        if (t == expected.end()) {
            check(state.plan() != expected && state.phaseStartedAt() == t && state.type() == expected.target(), "E not completed before block entities");
            boundaries++;
        }
    }
}
