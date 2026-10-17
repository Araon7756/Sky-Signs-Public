package cn.skysigns.test;

import cn.skysigns.client.ClientWindState;
import cn.skysigns.client.config.ClientConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

/** Also executable with a JDK alone; all fixtures live in a fresh temporary directory. */
public final class P3StateConfigAssertions {
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        wind();
        config();
        System.out.println("P3_STATE_CONFIG_OK first/cardinal/wrap/pause/identity/invalid/config/readonly/retry");
    }

    private static void wind() {
        var wind = new ClientWindState();
        Object world = new Object(), connection = new Object();
        check(!wind.valid(), "New state reused wind");
        for (float direction : new float[]{0, 90, 180, 270}) {
            wind.reset();
            check(wind.accept(world, connection, direction, 0.7F), "Legal wind rejected");
            near(wind.smoothDirection(), direction);
            near(wind.smoothStrength(), 0.7F);
        }
        wind.reset();
        wind.accept(world, connection, 359, 1);
        wind.accept(world, connection, 0, 0);
        wind.tick(world, connection, true);
        near(wind.smoothDirection(), 359);
        near(wind.smoothStrength(), 1);
        wind.tick(world, connection, false);
        near(wind.smoothDirection(), 359.2F);
        near(wind.smoothStrength(), 0.8F);
        wind.reset();
        wind.accept(world, connection, 0, 0);
        wind.accept(world, connection, 359, 1);
        wind.tick(world, connection, false);
        near(wind.smoothDirection(), 359.8F);
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            check(!wind.accept(world, connection, bad, 1), "Invalid direction accepted");
            check(!wind.accept(world, connection, 90, bad), "Invalid strength accepted");
            near(wind.direction(), 359);
            near(wind.strength(), 1);
        }
        wind.accept(world, connection, -450, 9);
        near(wind.direction(), 270); near(wind.strength(), 1);
        wind.accept(world, connection, 720, -9);
        near(wind.direction(), 0); near(wind.strength(), 0);
        wind.accept(world, connection, -Float.MIN_VALUE, 1);
        check(wind.direction() >= 0 && wind.direction() < 360, "Normalization rounded to 360");
        wind.tick(new Object(), connection, false);
        check(!wind.valid() && wind.smoothStrength() == 0, "World switch inherited wind");
        wind.accept(world, connection, 90, 1);
        wind.tick(world, new Object(), false);
        check(!wind.valid(), "Connection switch inherited wind");
        wind.accept(world, connection, 90, 1);
        wind.tick(null, null, true);
        check(!wind.valid() && wind.direction() == 0, "Disconnect while paused retained state");
        wind.accept(world, connection, 180, 0);
        near(wind.smoothStrength(), 0);
        near(wind.smoothDirection(), 180);
    }

    private static void config() throws Exception {
        Path directory = Files.createTempDirectory("skysigns-p3-config-");
        Path path = directory.resolve("skysigns-client.properties");
        var diagnostics = new ArrayList<String>();
        try {
            var missing = new ClientConfig(path, diagnostics::add);
            check(missing.enabled() && missing.strength() == 1 && missing.save(), "Missing-file defaults");
            Files.writeString(path, "wind_precipitation_enabled=false\nwind_precipitation_strength=0.37\nfuture=保留\n");
            var config = new ClientConfig(path, diagnostics::add);
            check(!config.enabled(), "Old boolean changed"); near(config.strength(), 0.37F);
            byte[] original = Files.readAllBytes(path);
            config.setEnabled(true); config.setStrength(0.61F);
            check(Arrays.equals(original, Files.readAllBytes(path)), "Memory update wrote disk");
            config.setEnabled(false);
            near(config.strength(), 0.61F);
            config.restoreDefaults();
            check(config.save(), "Default save failed");
            check(Files.readString(path).contains("future=保留"), "Unknown UTF-8 key lost");
            check(new ClientConfig(path, diagnostics::add).strength() == 1, "Restart lost saved value");
            for (String number : new String[]{"NaN", "Infinity", "-Infinity", "broken", ""}) {
                Files.writeString(path, "wind_precipitation_enabled=not-true\nwind_precipitation_strength=" + number);
                var invalid = new ClientConfig(path, diagnostics::add);
                check(invalid.enabled() && invalid.strength() == 1, "Strict parsing failed: " + number);
            }
            for (String number : new String[]{"-2", "2"}) {
                Files.writeString(path, "wind_precipitation_enabled=true\nwind_precipitation_strength=" + number);
                near(new ClientConfig(path, diagnostics::add).strength(), number.equals("-2") ? 0 : 1);
            }
            Files.writeString(path, "future=value\n");
            check(new ClientConfig(path, diagnostics::add).save(), "Missing keys not repaired");
            // A malformed file cannot be safely rewritten because its unknown keys could not be parsed.
            Files.writeString(path, "future=" + "\\" + "uBAD!\n");
            byte[] malformed = Files.readAllBytes(path);
            var damaged = new ClientConfig(path, diagnostics::add);
            damaged.setStrength(0.42F);
            check(!damaged.save() && damaged.dirty(), "Malformed file reported saved");
            check(Arrays.equals(malformed, Files.readAllBytes(path)), "Malformed original was overwritten");
            Files.writeString(path, "future=repaired\n");
            check(damaged.save(), "Retry after external repair failed");
            near(new ClientConfig(path, diagnostics::add).strength(), 0.42F);
            // Actual permission failure, not a mocked writer. Refuse to count privileged bypass as a pass.
            original = Files.readAllBytes(path);
            var readonly = new ClientConfig(path, diagnostics::add);
            readonly.setStrength(0.23F);
            if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory);
                try {
                    Files.setPosixFilePermissions(directory, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
                    check(!Files.isWritable(directory), "Read-only fixture bypassed by host privileges");
                    check(!readonly.save(), "Read-only directory reported success");
                    near(readonly.strength(), 0.23F);
                    check(readonly.dirty() && Arrays.equals(original, Files.readAllBytes(path)), "Failed save changed original or memory");
                } finally { Files.setPosixFilePermissions(directory, permissions); }
            } else {
                // Portable structural failure; POSIX permission behavior is separately verified in Cloud.
                Path retained = directory.resolveSibling(directory.getFileName() + "-retained");
                Files.move(directory, retained);
                try {
                    Files.writeString(directory, "blocked parent");
                    check(!readonly.save() && readonly.dirty(), "Blocked parent reported saved");
                    check(Arrays.equals(original, Files.readAllBytes(retained.resolve(path.getFileName()))), "Retained original changed");
                } finally {
                    Files.deleteIfExists(directory);
                    Files.move(retained, directory);
                }
                System.out.println("P3_PERMISSION_NOTE non-POSIX host: structural failure tested, read-only permissions unverified");
            }
            check(readonly.save() && !readonly.dirty(), "Permission repair retry failed");
            near(new ClientConfig(path, diagnostics::add).strength(), 0.23F);
            try (var entries = Files.list(directory)) {
                check(entries.count() == 1, "Temporary files leaked");
            }
            check(!diagnostics.isEmpty(), "Invalid config had no diagnostics");
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(directory);
        }
    }

    static void near(float actual, float expected) {
        check(Math.abs(actual - expected) < 0.0001F, "Expected " + expected + ", got " + actual);
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
