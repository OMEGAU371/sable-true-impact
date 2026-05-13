package com.example.sabletrueimpact;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TrueImpactClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_GOGGLES_BLOCK_TOOLTIP;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("tooltip");
        ENABLE_GOGGLES_BLOCK_TOOLTIP = BUILDER.comment("When wearing Create: Aeronautics aviator goggles, block item tooltips show Sable and True Impact material data.")
                .define("enableGogglesBlockTooltip", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private TrueImpactClientConfig() {
    }
}
