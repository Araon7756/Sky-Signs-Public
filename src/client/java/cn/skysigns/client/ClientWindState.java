package cn.skysigns.client;

/** Client-thread state, independent of rendering and player preferences. Identity uses object equality. */
public final class ClientWindState {
    public static final ClientWindState INSTANCE = new ClientWindState();
    private Object world;
    private Object connection;
    private boolean valid;
    private float direction;
    private float strength;
    private float smoothDirection;
    private float smoothStrength;

    public void synchronize(Object world, Object connection) {
        if (world == null || connection == null || this.world != world || this.connection != connection) {
            reset();
            this.world = world;
            this.connection = connection;
        }
    }

    public boolean accept(Object world, Object connection, float direction, float strength) {
        synchronize(world, connection);
        if (world == null || connection == null || !Float.isFinite(direction) || !Float.isFinite(strength)) {
            return false;
        }
        this.direction = normalize(direction);
        this.strength = Math.clamp(strength, 0.0F, 1.0F);
        if (!valid) {
            smoothDirection = this.direction;
            smoothStrength = this.strength;
            valid = true;
        }
        return true;
    }

    public void tick(Object world, Object connection, boolean paused) {
        synchronize(world, connection);
        if (!valid || paused) return;
        float difference = normalize(direction - smoothDirection + 180.0F) - 180.0F;
        smoothDirection = normalize(smoothDirection + difference * 0.2F);
        smoothStrength += (strength - smoothStrength) * 0.2F;
    }

    public boolean belongsTo(Object world, Object connection) {
        return valid && world != null && connection != null && this.world == world && this.connection == connection;
    }

    public void reset() {
        world = null;
        connection = null;
        valid = false;
        direction = strength = smoothDirection = smoothStrength = 0.0F;
    }

    private static float normalize(float degrees) {
        float result = degrees % 360.0F;
        if (result < 0.0F) result += 360.0F;
        return result >= 360.0F ? 0.0F : result;
    }

    public boolean valid() { return valid; }
    public float direction() { return direction; }
    public float strength() { return strength; }
    public float smoothDirection() { return smoothDirection; }
    public float smoothStrength() { return smoothStrength; }
}
