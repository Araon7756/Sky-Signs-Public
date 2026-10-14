package cn.skysigns.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/** Product-free entrypoint: do not instantiate the product test class or its UI signatures. */
@SuppressWarnings("UnstableApiUsage")
public final class Inf01FrameworkControl implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        ChunkReadiness.require(!FabricLoader.getInstance().isModLoaded("skysigns"), "Framework control loaded Sky Signs");
        ChunkReadiness.prepare(context);
        Inf01ReadinessAssertions.nativeFailure(context);
        ChunkReadiness.event("selftestPassed", java.util.Map.of("case", "framework-native", "nativeSquareFixed", false));
    }
}
