package cn.skysigns.weather;

import cn.skysigns.storage.SaveDataMigrations;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public final class WindState extends PersistentState {
    private static final String[] COMPASS_DIRECTIONS = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
    public static final PersistentStateType<WindState> TYPE = new PersistentStateType<>(
            "skysigns_wind", WindState::new,
            NbtCompound.CODEC.xmap(WindState::fromNbt, WindState::toNbt),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private float directionDegrees;
    private float strength;
    private float targetDirectionDegrees;
    private float targetStrength;
    private long nextAutomaticChange;
    private boolean automatic = true;
    private boolean initialized;
    private NbtCompound storedData = new NbtCompound();
    private boolean readOnly;

    public boolean isReadOnly() {
        return readOnly;
    }

    public WindState() {
    }

    public WindState(float directionDegrees, float strength, long nextAutomaticChange) {
        set(directionDegrees, strength);
        this.nextAutomaticChange = nextAutomaticChange;
    }

    public void initialize(float directionDegrees, float strength, long nextAutomaticChange) {
        set(directionDegrees, strength);
        scheduleNextChange(nextAutomaticChange);
        initialized = true;
    }

    public boolean initialized() {
        return initialized;
    }

    public float directionDegrees() {
        return directionDegrees;
    }

    public float strength() {
        return strength;
    }

    public long nextAutomaticChange() {
        return nextAutomaticChange;
    }

    public boolean automatic() {
        return automatic;
    }

    public void set(float directionDegrees, float strength) {
        SaveDataMigrations.requireWritable(readOnly, SaveDataMigrations.Kind.WIND);
        this.directionDegrees = normalizeDegrees(directionDegrees);
        this.strength = MathHelper.clamp(strength, 0.0F, 1.0F);
        this.targetDirectionDegrees = this.directionDegrees;
        this.targetStrength = this.strength;
        this.initialized = true;
        markDirty();
    }

    public void setTarget(float directionDegrees, float strength) {
        SaveDataMigrations.requireWritable(readOnly, SaveDataMigrations.Kind.WIND);
        this.targetDirectionDegrees = normalizeDegrees(directionDegrees);
        this.targetStrength = MathHelper.clamp(strength, 0.0F, 1.0F);
        markDirty();
    }

    public void advanceOneTick() {
        SaveDataMigrations.requireWritable(readOnly, SaveDataMigrations.Kind.WIND);
        float difference = MathHelper.wrapDegrees(targetDirectionDegrees - directionDegrees);
        float maximumTurn = 0.08F + strength * 0.22F;
        directionDegrees = normalizeDegrees(directionDegrees
                + MathHelper.clamp(difference, -maximumTurn, maximumTurn));
        strength += MathHelper.clamp(targetStrength - strength, -0.0025F, 0.0025F);
        if (difference != 0.0F || strength != targetStrength) {
            markDirty();
        }
    }

    public void scheduleNextChange(long gameTime) {
        SaveDataMigrations.requireWritable(readOnly, SaveDataMigrations.Kind.WIND);
        this.nextAutomaticChange = gameTime;
        markDirty();
    }

    public void setAutomatic(boolean automatic) {
        SaveDataMigrations.requireWritable(readOnly, SaveDataMigrations.Kind.WIND);
        this.automatic = automatic;
        markDirty();
    }

    public Text compassDirection() {
        int index = Math.round(directionDegrees / 45.0F) % COMPASS_DIRECTIONS.length;
        return Text.translatable("wind.skysigns.direction." + COMPASS_DIRECTIONS[index]);
    }

    public Text forceName() {
        if (strength < 0.15F) return Text.translatable("wind.skysigns.calm");
        if (strength < 0.35F) return Text.translatable("wind.skysigns.light");
        if (strength < 0.60F) return Text.translatable("wind.skysigns.moderate");
        if (strength < 0.82F) return Text.translatable("wind.skysigns.strong");
        return Text.translatable("wind.skysigns.gale");
    }

    private static float normalizeDegrees(float degrees) {
        if (!Float.isFinite(degrees)) return 0.0F;
        float normalized = degrees % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    private static WindState fromNbt(NbtCompound nbt) {
        SaveDataMigrations.Result migration = SaveDataMigrations.upgrade(nbt, SaveDataMigrations.Kind.WIND);
        nbt = migration.data();
        WindState state = new WindState();
        state.storedData = nbt.copy();
        state.readOnly = migration.readOnly();
        state.directionDegrees = normalizeDegrees(nbt.getFloat("direction", 0.0F));
        float savedStrength = nbt.getFloat("strength", 0.0F);
        state.strength = Float.isFinite(savedStrength) ? MathHelper.clamp(savedStrength, 0.0F, 1.0F) : 0.0F;
        state.targetDirectionDegrees = normalizeDegrees(nbt.getFloat("target_direction", state.directionDegrees));
        float savedTargetStrength = nbt.getFloat("target_strength", state.strength);
        state.targetStrength = Float.isFinite(savedTargetStrength)
                ? MathHelper.clamp(savedTargetStrength, 0.0F, 1.0F) : state.strength;
        state.nextAutomaticChange = nbt.getLong("next_change", 0L);
        state.automatic = nbt.getBoolean("automatic", true);
        state.initialized = nbt.getBoolean("initialized", true);
        if (migration.migrated()) state.markDirty();
        return state;
    }

    private NbtCompound toNbt() {
        NbtCompound nbt = storedData.copy();
        if (readOnly) return nbt;
        nbt.putInt(SaveDataMigrations.VERSION_KEY, SaveDataMigrations.Kind.WIND.currentVersion());
        nbt.putFloat("direction", directionDegrees);
        nbt.putFloat("strength", strength);
        nbt.putFloat("target_direction", targetDirectionDegrees);
        nbt.putFloat("target_strength", targetStrength);
        nbt.putLong("next_change", nextAutomaticChange);
        nbt.putBoolean("automatic", automatic);
        nbt.putBoolean("initialized", initialized);
        return nbt;
    }
}
