package cn.skysigns.test.infrastructure;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.impl.client.gametest.TestSystemProperties;
import net.fabricmc.fabric.impl.client.gametest.threading.NetworkSynchronizer;

/** Startup certificate only: no packet hooks, wait hooks, world changes, or assertions in product flows. */
public final class NetworkPatchActivation implements ClientModInitializer {
    @Override public void onInitializeClient() {
        if (TestSystemProperties.DISABLE_NETWORK_SYNCHRONIZER || System.getenv("SKYSIGNS_NETWORK_WAIT_GUARD") != null)
            throw new AssertionError("Patched framework requires synchronization enabled and old guard absent");
        String source = NetworkSynchronizer.class.getProtectionDomain().getCodeSource().getLocation().toString();
        if (!source.endsWith("fabric-client-gametest-api-v1-network-synchronizer-test-patch-4.2.13.jar"))
            throw new AssertionError("Patched framework was not loaded: " + source);
        try (InputStream stream = NetworkSynchronizer.class.getResourceAsStream("NetworkSynchronizer.class")) {
            if (stream == null) throw new AssertionError("Missing framework class resource");
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
            if (!actual.equals(System.getProperty("network.patch.classSha256")))
                throw new AssertionError("Loaded framework class hash differs from compiled patch: " + actual);
            System.out.println("NS_PATCH_ACTIVE source=" + source + " classSha256=" + actual + " disabled=false oldGuard=false");
        } catch (Exception error) { throw new AssertionError("Could not verify loaded framework patch", error); }
    }
}
