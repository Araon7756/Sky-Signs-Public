package cn.skysigns.client.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.function.Consumer;

/** In-memory preferences. Only explicit save points write; no file watcher or server synchronization. */
public final class ClientConfig {
    private static final String ENABLED = "wind_precipitation_enabled";
    private static final String STRENGTH = "wind_precipitation_strength";
    private final Path path;
    private final Consumer<String> diagnostic;
    private boolean enabled = true;
    private float strength = 1.0F;
    private boolean dirty;

    public ClientConfig(Path path, Consumer<String> diagnostic) {
        this.path = path.toAbsolutePath();
        this.diagnostic = diagnostic;
        Properties properties;
        try {
            properties = read();
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            diagnostic.accept("Could not read client weather config: " + exception);
            properties = new Properties();
        }
        String toggle = properties.getProperty(ENABLED);
        if ("true".equals(toggle) || "false".equals(toggle)) {
            enabled = Boolean.parseBoolean(toggle);
        } else {
            invalid(ENABLED);
        }
        try {
            float parsed = Float.parseFloat(properties.getProperty(STRENGTH, ""));
            if (!Float.isFinite(parsed)) throw new NumberFormatException("non-finite");
            strength = Math.clamp(parsed, 0.0F, 1.0F);
            if (strength != parsed) invalid(STRENGTH);
        } catch (NumberFormatException exception) {
            invalid(STRENGTH);
        }
    }

    private Properties read() throws IOException {
        Properties properties = new Properties();
        if (Files.exists(path)) {
            // Parse into a temporary object; malformed Unicode must not leak partially read keys.
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    private void invalid(String key) {
        dirty = true;
        diagnostic.accept("Missing or invalid " + key + "; using default or bounded value");
    }

    public boolean enabled() { return enabled; }
    public float strength() { return strength; }
    public boolean dirty() { return dirty; }
    public void setEnabled(boolean enabled) { dirty |= this.enabled != enabled; this.enabled = enabled; }
    public void setStrength(float strength) {
        if (!Float.isFinite(strength)) throw new IllegalArgumentException("Non-finite visual strength");
        float bounded = Math.clamp(strength, 0.0F, 1.0F);
        dirty |= this.strength != bounded;
        this.strength = bounded;
    }
    public void restoreDefaults() { setEnabled(true); setStrength(1.0F); }

    public boolean save() {
        if (!dirty) return true;
        Path temporary = null;
        try {
            // Re-read to preserve even unknown keys added since startup. Unreadable/malformed files are protected.
            Properties properties = read();
            properties.setProperty(ENABLED, Boolean.toString(enabled));
            properties.setProperty(STRENGTH, Float.toString(strength));
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), "skysigns-client-", ".tmp");
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Sky Signs client preferences. External edits require a restart.");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                // Safe fallback is to retain the original, not a potentially destructive non-atomic replacement.
                throw new IOException("Atomic replacement unavailable; original config retained", exception);
            }
            dirty = false;
            return true;
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            diagnostic.accept("Client settings are active but could not be saved: " + exception);
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException | SecurityException exception) { diagnostic.accept("Could not remove config temporary file: " + exception); }
            }
        }
    }
}
