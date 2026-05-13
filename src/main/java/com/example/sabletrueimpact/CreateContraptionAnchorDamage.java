package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.Set;

public final class CreateContraptionAnchorDamage {
    private static final Set<String> CREATE_ANCHOR_PATHS = Set.of(
            "mechanical_bearing",
            "windmill_bearing",
            "clockwork_bearing",
            "mechanical_piston",
            "sticky_mechanical_piston",
            "gantry_carriage",
            "gantry_shaft",
            "rope_pulley",
            "cart_assembler",
            "mechanical_drill",
            "mechanical_saw",
            "deployer",
            "contraption_controls"
    );

    private CreateContraptionAnchorDamage() {
    }

    public static void apply(ServerLevel level, Vector3d impactPoint, double impactEnergy) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get()
                || !TrueImpactConfig.ENABLE_CREATE_CONTRAPTION_ANCHOR_DAMAGE.get()
                || !TrueImpactConfig.ENABLE_WORLD_DESTRUCTION.get()
                || impactEnergy <= 0.0) {
            return;
        }

        int radius = TrueImpactConfig.CREATE_CONTRAPTION_ANCHOR_DAMAGE_RADIUS.get();
        if (radius <= 0) {
            return;
        }

        BlockPos center = BlockPos.containing(impactPoint.x, impactPoint.y, impactPoint.z);
        double scale = TrueImpactConfig.CREATE_CONTRAPTION_ANCHOR_DAMAGE_SCALE.get();
        double radiusSq = (radius + 0.75) * (radius + 0.75);

        for (BlockPos scan : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(scan);
            if (!isCreateAnchor(state)) {
                continue;
            }

            double distanceSq = scan.distToCenterSqr(impactPoint.x, impactPoint.y, impactPoint.z);
            if (distanceSq > radiusSq) {
                continue;
            }

            double falloff = 1.0 - Math.sqrt(distanceSq) / (radius + 0.75);
            double anchorDamage = impactEnergy * scale * Math.max(0.15, falloff);
            damageBlock(level, scan.immutable(), state, anchorDamage, 1.25);
            damageSupports(level, scan.immutable(), anchorDamage * 0.45);
        }
    }

    private static void damageSupports(ServerLevel level, BlockPos anchorPos, double damage) {
        for (Direction direction : Direction.values()) {
            BlockPos supportPos = anchorPos.relative(direction);
            BlockState supportState = level.getBlockState(supportPos);
            if (supportState.isAir() || isCreateAnchor(supportState)) {
                continue;
            }
            damageBlock(level, supportPos, supportState, damage, 1.75);
        }
    }

    private static void damageBlock(ServerLevel level, BlockPos pos, BlockState state, double damage, double thresholdMultiplier) {
        if (state.isAir()
                || state.is(Blocks.BEDROCK)
                || state.getDestroySpeed(level, pos) < 0.0f
                || !MaterialImpactProperties.isDestructible(state, true)) {
            return;
        }
        double baseStrength = MaterialImpactProperties.baseStrength(level, pos, state);
        double threshold = MaterialImpactProperties.breakThreshold(state, baseStrength) * thresholdMultiplier;
        BlockDamageAccumulator.apply(level, pos, MaterialImpactProperties.fatigueDamage(state, damage), threshold, pos.hashCode() ^ 0x5171C0DE);
    }

    private static boolean isCreateAnchor(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return "create".equals(id.getNamespace()) && CREATE_ANCHOR_PATHS.contains(id.getPath());
    }
}
