package cn.skysigns.weather;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public final class WindManager {
    private static final int MIN_CHANGE_DELAY = 20 * 20;
    private static final int EXTRA_CHANGE_DELAY = 20 * 25;

    private WindManager() {
    }

    public static WindState get(ServerWorld world) {
        WindState state = world.getPersistentStateManager().get(WindState.TYPE);
        return state != null ? state : new WindState();
    }

    public static void initialize(ServerWorld world) {
        WindState state = world.getPersistentStateManager().getOrCreate(WindState.TYPE);
        if (state.isReadOnly() || state.initialized()) return;
        state.initialize(world.getRandom().nextInt(360),
                0.15F + world.getRandom().nextFloat() * 0.45F, world.getTime());
        chooseNextTarget(world, state);
    }

    public static void precursor(ServerWorld world, long change) {
        WindState state = get(world);
        if (!state.isReadOnly() && state.initialized() && state.automatic() && state.nextAutomaticChange() > change)
            state.scheduleNextChange(world.getTime());
    }

    public static void tick(ServerWorld world) {
        initialize(world);
        WindState state = get(world);
        if (state.isReadOnly()) return;
        if (state.automatic()) {
            state.advanceOneTick();
        }
        if (state.automatic() && world.getTime() >= state.nextAutomaticChange()) {
            chooseNextTarget(world, state);
        }
    }

    public static void setManual(ServerWorld world, float directionDegrees, float strength) {
        initialize(world);
        WindState state = get(world);
        state.set(directionDegrees, strength);
        state.setAutomatic(false);
    }

    public static void enableAutomatic(ServerWorld world) {
        initialize(world);
        WindState state = get(world);
        state.setAutomatic(true);
        state.scheduleNextChange(world.getTime());
    }

    public static Text describe(WindState wind) {
        return Text.translatable("wind.skysigns.description", wind.compassDirection(),
                Math.round(wind.directionDegrees()), wind.forceName(),
                Math.round(wind.strength() * 100.0F));
    }

    private static void chooseNextTarget(ServerWorld world, WindState state) {
        float directionDrift = world.getRandom().nextBetween(-95, 95);
        float targetDirection = state.directionDegrees() + directionDrift;
        var range = WeatherManager.view(world).bases();
        float targetStrength = range.windMin() + world.getRandom().nextFloat() * (range.windMax() - range.windMin());
        state.setTarget(targetDirection, targetStrength);
        state.scheduleNextChange(world.getTime() + MIN_CHANGE_DELAY
                + world.getRandom().nextInt(EXTRA_CHANGE_DELAY + 1));
    }
}
