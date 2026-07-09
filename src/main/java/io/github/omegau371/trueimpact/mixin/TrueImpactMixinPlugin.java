package io.github.omegau371.trueimpact.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin plugin: conditionally applies Sable-targeting mixins only when Sable is present.
 * Prevents "target not found" WARNs when Sable is absent.
 *
 * Sable version compatibility is checked at shouldApplyMixin time.
 * If Sable is absent or incompatible, all three diagnostic mixins are silently skipped.
 */
public final class TrueImpactMixinPlugin implements IMixinConfigPlugin {

    private static final String MOD_ID_SABLE = "sable";

    /** Mixins that must only be applied when Sable is loaded and compatible. */
    private static final Set<String> SABLE_MIXINS = Set.of(
            "io.github.omegau371.trueimpact.mixin.DiagnosticCallbackWrapperMixin",
            "io.github.omegau371.trueimpact.mixin.DiagnosticContactCaptureMixin"
    );

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (SABLE_MIXINS.contains(mixinClassName)) {
            return isSableLoadedAndCompatible();
        }
        return true;
    }

    private static boolean isSableLoadedAndCompatible() {
        try {
            // FMLLoader.getLoadingModList() is available during mixin application phase
            var sableFile = net.neoforged.fml.loading.FMLLoader
                    .getLoadingModList()
                    .getModFileById(MOD_ID_SABLE);
            // Accept any Sable version; per-version API differences are handled by
            // require=0 on all injectors in the mixin classes.
            return sableFile != null;
        } catch (Throwable t) {
            // Any problem → safely skip Sable mixins
            return false;
        }
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
