package io.github.omegau371.trueimpact.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import io.github.omegau371.trueimpact.client.TrueImpactConfigurationSectionScreen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mirrors Sable's own OptionsScreenMixin (which stacks a "Sub-Level Settings..." button
 * below the difficulty/online button on the right), but on the LEFT side under the FOV
 * button -- restoring visual balance instead of piling both mods' buttons on the same side.
 *
 * Sable hooks a dedicated createOnlineButton() method via @Inject + @At("RETURN"). No such
 * dedicated method exists for the FOV button (it's built inline), so this redirects the
 * specific LinearLayout.addChild(LayoutElement) call that adds it instead. Within
 * OptionsScreen.init(), that overload is invoked exactly 3 times in order: building the
 * horizontal row itself (0), the FOV button (1), the difficulty/online button (2).
 */
@Mixin(OptionsScreen.class)
public abstract class TrueImpactOptionsScreenMixin extends Screen {

    protected TrueImpactOptionsScreenMixin(Component title) {
        super(title);
    }

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/layouts/LinearLayout;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;",
                    ordinal = 1
            )
    )
    @Unique
    private LayoutElement trueImpact$addFovButtonWithSettings(LinearLayout linearLayout, LayoutElement fovButton) {
        // Server config is only actually editable by the host (NeoForge's own ConfigurationScreen
        // already disables editing when connected to someone else's server or a published LAN
        // world); hasSingleplayerServer() additionally hides the ENTRY POINT itself for guests,
        // matching Sable's own visibility gate on its "Sub-Level Settings..." button.
        if (this.minecraft.level == null || !this.minecraft.hasSingleplayerServer()) {
            return linearLayout.addChild(fovButton);
        }
        LinearLayout stack = LinearLayout.vertical().spacing(5);
        stack.addChild(fovButton);
        stack.addChild(
                Button.builder(Component.translatable("true_impact.optionsButton"),
                                button -> ModList.get().getModContainerById("true_impact").ifPresent(container ->
                                        this.minecraft.setScreen(new ConfigurationScreen(container, this, TrueImpactConfigurationSectionScreen::new))))
                        .size(150, 20)
                        .build());
        return linearLayout.addChild(stack);
    }
}
