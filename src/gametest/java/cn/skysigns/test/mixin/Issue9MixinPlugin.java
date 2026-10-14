package cn.skysigns.test.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** No measurement hooks are woven into the baseline or ordinary regression run. */
public final class Issue9MixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("P1ObservationMixin") || mixinClassName.endsWith("P1ReadingsMixin"))
            return "1".equals(System.getenv("SKYSIGNS_P1_ONLY"));
        if (mixinClassName.endsWith("NetworkWaitGuardMixin")) {
            return "1".equals(System.getenv("SKYSIGNS_NETWORK_WAIT_GUARD"))
                    && System.getProperty("fabric.client.gametest") != null;
        }
        if (mixinClassName.endsWith("NetworkDiagnosticMixin")) {
            return "1".equals(System.getenv("SKYSIGNS_NETWORK_DIAGNOSTICS"));
        }
        String mode = System.getenv("SKYSIGNS_ISSUE9_MODE");
        return "preflight".equals(mode) || "detailed".equals(mode);
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo info) { }
}
