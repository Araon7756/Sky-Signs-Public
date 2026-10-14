package cn.skysigns.block.entity;

import cn.skysigns.block.ObservationInstrumentBlock;
import cn.skysigns.registry.ModBlockEntities;
import cn.skysigns.weather.AtmosphericReadings;
import cn.skysigns.storage.SaveDataMigrations;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.Objects;

public final class MeterBlockEntity extends BlockEntity implements HeldItemContext {
    private int readingPercent = 50;
    private NbtCompound storedData = new NbtCompound();
    private boolean readOnly;
    private boolean migrationPending;
    private int legacyReading = 50;

    public boolean isReadOnly() {
        return readOnly;
    }

    public MeterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.METER, pos, state);
    }

    public int readingPercent() {
        return readingPercent;
    }

    @Override
    public World getEntityWorld() {
        return Objects.requireNonNull(getWorld(), "Meter is not attached to a world");
    }

    @Override
    public Vec3d getEntityPos() {
        return Vec3d.ofCenter(getPos());
    }

    @Override
    public float getBodyYaw() {
        return 0.0F;
    }

    public void sample(ServerWorld world) {
        if (readOnly) return;
        if (migrationPending) {
            // readData can run before the entity is attached to a world. Persist
            // the migration on the first server sample even if the reading is unchanged.
            migrationPending = false;
            markDirty();
        }
        ObservationInstrumentBlock block = (ObservationInstrumentBlock) getCachedState().getBlock();
        int measured = block.kind() == ObservationInstrumentBlock.Kind.BAROMETER
                ? MathHelper.clamp(Math.round((AtmosphericReadings.pressureHpa(world) - 980.0F) * 2.0F), 0, 100)
                : AtmosphericReadings.humidityPercent(world, pos);
        if (readingPercent != measured) {
            readingPercent = measured;
            markDirty();
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        legacyReading = view.getInt("reading_percent", 50);
        NbtCompound legacy = new NbtCompound();
        legacy.putInt("reading_percent", legacyReading);
        NbtCompound source = view.read(SaveDataMigrations.METER_DATA_KEY, NbtCompound.CODEC).orElse(legacy);
        SaveDataMigrations.Result migration = SaveDataMigrations.upgrade(source, SaveDataMigrations.Kind.METER);
        storedData = migration.data();
        readOnly = migration.readOnly();
        migrationPending = migration.migrated();
        readingPercent = MathHelper.clamp(storedData.getInt("reading_percent", 50), 0, 100);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        NbtCompound data = storedData.copy();
        if (!readOnly) {
            data.putInt(SaveDataMigrations.VERSION_KEY, SaveDataMigrations.Kind.METER.currentVersion());
            data.putInt("reading_percent", readingPercent);
        }
        view.put(SaveDataMigrations.METER_DATA_KEY, NbtCompound.CODEC, data);
        // Retain the legacy field for older readers, but never downgrade nested future data.
        view.putInt("reading_percent", readOnly ? legacyReading : readingPercent);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }
}
