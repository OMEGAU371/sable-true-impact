/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 *  org.joml.Vector3d
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.BlockDamageAccumulator;
import com.example.sabletrueimpact.MaterialImpactProperties;
import com.example.sabletrueimpact.TrueImpactConfig;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3d;

public final class CreateContraptionAnchorDamage {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Set<String> CREATE_ANCHOR_PATHS = Set.of("mechanical_bearing", "windmill_bearing", "clockwork_bearing", "mechanical_piston", "sticky_mechanical_piston", "gantry_carriage", "gantry_shaft", "rope_pulley", "elevator_pulley", "hose_pulley", "cart_assembler", "mechanical_drill", "mechanical_saw", "deployer", "contraption_controls");
    private static final Set<String> CREATE_TRACK_PATHS = Set.of("track", "fake_track");
    private static final Set<String> CREATE_ROPE_PATHS = Set.of("rope", "pulley_magnet");
    private static final Set<String> CREATE_GANTRY_PATHS = Set.of("gantry_carriage", "gantry_shaft");
    private static final Set<String> CREATE_ACTIVE_PART_PATHS = Set.of("mechanical_drill", "mechanical_saw", "deployer", "contraption_controls");
    private static final double MIN_LOAD_TRANSFER_RATIO = 0.25;

    private CreateContraptionAnchorDamage() {
    }

    public static void apply(ServerLevel level, Vector3d impactPoint, double impactEnergy) {
        if (!((Boolean)TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue() || !((Boolean)TrueImpactConfig.ENABLE_CREATE_CONTRAPTION_ANCHOR_DAMAGE.get()).booleanValue() || !((Boolean)TrueImpactConfig.ENABLE_WORLD_DESTRUCTION.get()).booleanValue() || impactEnergy <= 0.0) {
            return;
        }
        int radius = (Integer)TrueImpactConfig.CREATE_CONTRAPTION_ANCHOR_DAMAGE_RADIUS.get();
        if (radius <= 0) {
            return;
        }
        BlockPos center = BlockPos.containing((double)impactPoint.x, (double)impactPoint.y, (double)impactPoint.z);
        double scale = (Double)TrueImpactConfig.CREATE_CONTRAPTION_ANCHOR_DAMAGE_SCALE.get();
        CreateContraptionLoadAnalyzer.Result load = CreateContraptionLoadAnalyzer.analyzeNearest(level, impactPoint, impactEnergy);
        if (load.found()) {
            debug("Create load: class={}, contraption={}, blocks={}, mass={}, load={}, capacity={}, threshold={}, overloaded={}, controller={}", load.entityClass(), load.contraptionClass(), load.scannedBlocks(), fmt(load.mass()), fmt(impactEnergy), fmt(load.capacity()), fmt(load.threshold()), load.overloaded(), load.controllerPos());
            CreateContraptionAnchorDamage.applyDynamicStructureImpulse(impactPoint, impactEnergy, load);
            if (!load.overloaded()) {
                double loadRatio = impactEnergy / Math.max(load.threshold(), 1.0E-6);
                if (loadRatio >= MIN_LOAD_TRANSFER_RATIO) {
                    CreateContraptionAnchorDamage.damageDynamicStructureTargets(level, impactPoint, impactEnergy * scale * Math.max(0.2, loadRatio), load, false);
                }
            }
        } else {
            debug("Create load: no Create contraption found near impact at {}", BlockPos.containing((double)impactPoint.x, (double)impactPoint.y, (double)impactPoint.z));
        }
        if (load.found() && load.overloaded()) {
            double overloadScale = (Double)TrueImpactConfig.CREATE_CONTRAPTION_OVERLOAD_ANCHOR_DAMAGE_SCALE.get();
            scale *= Math.max(1.0, load.overloadRatio() * overloadScale);
            debug("Create overload: ratio={}, finalAnchorScale={}", fmt(load.overloadRatio()), fmt(scale));
            boolean targeted = CreateContraptionAnchorDamage.damageRuleTargets(level, impactPoint, impactEnergy * scale, load);
            targeted |= CreateContraptionAnchorDamage.damageDynamicStructureTargets(level, impactPoint, impactEnergy * scale, load, true);
            if (targeted) {
                return;
            }
        }
        double radiusSq = ((double)radius + 0.75) * ((double)radius + 0.75);
        for (BlockPos scan : BlockPos.betweenClosed((BlockPos)center.offset(-radius, -radius, -radius), (BlockPos)center.offset(radius, radius, radius))) {
            double distanceSq;
            BlockState state = level.getBlockState(scan);
            if (!CreateContraptionAnchorDamage.isCreateAnchor(state) || (distanceSq = scan.distToCenterSqr(impactPoint.x, impactPoint.y, impactPoint.z)) > radiusSq) continue;
            double falloff = 1.0 - Math.sqrt(distanceSq) / ((double)radius + 0.75);
            double anchorDamage = impactEnergy * scale * Math.max(0.15, falloff);
                CreateContraptionAnchorDamage.damageBlock(level, scan.immutable(), state, anchorDamage, 1.25);
                CreateContraptionAnchorDamage.damageSupports(level, scan.immutable(), anchorDamage * 0.45);
                debug("Create fallback target: {} at {}, damage={}", blockPath(state), scan.immutable(), fmt(anchorDamage));
            if (blockPath(state).equals("cart_assembler")) {
                CreateContraptionAnchorDamage.damageNearbyMinecarts(level, scan.immutable(), anchorDamage);
            }
        }
    }

    private static boolean damageRuleTargets(ServerLevel level, Vector3d impactPoint, double damage, CreateContraptionLoadAnalyzer.Result load) {
        boolean damaged = false;
        String entityClass = load.entityClass();
        String contraptionClass = load.contraptionClass();
        if (entityClass.contains("CarriageContraptionEntity") || contraptionClass.contains("CarriageContraption") || contraptionClass.contains("Train")) {
            damaged |= CreateContraptionAnchorDamage.damageNearestCreateTrack(level, impactPoint, damage);
        }
        BlockPos controller = load.controllerPos();
        if (controller != null && level.isLoaded(controller)) {
            BlockState state = level.getBlockState(controller);
            String path = blockPath(state);
            if (isCreateAnchor(state) || path.contains("pulley") || path.contains("gantry") || path.contains("piston") || path.contains("bearing")) {
                CreateContraptionAnchorDamage.damageBlock(level, controller, state, damage, 0.85);
                CreateContraptionAnchorDamage.damageSupports(level, controller, damage * 0.55);
                debug("Create controller target: {} at {}, damage={}", path, controller, fmt(damage));
                damaged = true;
            }
            if (path.contains("pulley")) {
                damaged |= CreateContraptionAnchorDamage.damagePulleyLine(level, controller, impactPoint, damage * 0.65);
            }
            if (path.contains("gantry")) {
                damaged |= CreateContraptionAnchorDamage.damageNearbyCreateBlocks(level, controller, CREATE_GANTRY_PATHS, damage * 0.65, 4);
            }
            if (path.equals("cart_assembler")) {
                damaged |= CreateContraptionAnchorDamage.damageNearbyMinecarts(level, controller, damage);
            }
        }
        return damaged;
    }

    private static boolean damageDynamicStructureTargets(ServerLevel level, Vector3d impactPoint, double damage, CreateContraptionLoadAnalyzer.Result load, boolean overloaded) {
        boolean damaged = false;
        BlockPos nearestWorld = load.nearestWorldPos();
        BlockState nearestState = load.nearestState();
        BlockPos controller = load.controllerPos();
        if (nearestWorld != null && nearestState != null && !nearestState.isAir()) {
            String path = blockPath(nearestState);
            double localDamage = overloaded ? damage * 0.7 : damage * 0.35;
            if (CREATE_ACTIVE_PART_PATHS.contains(path)) {
                damaged |= CreateContraptionAnchorDamage.damageNearbyCreateBlocks(level, nearestWorld, Set.of(path), localDamage, 2);
            }
            if (path.contains("pulley") || path.contains("rope")) {
                damaged |= CreateContraptionAnchorDamage.damagePulleyLine(level, nearestWorld, impactPoint, localDamage);
            }
            debug("Create nearest moving part: {} local={}, world={}, damageIntent={}", path, load.nearestLocalPos(), nearestWorld, fmt(localDamage));
        }
        if (controller != null && level.isLoaded(controller)) {
            BlockState controllerState = level.getBlockState(controller);
            if (!controllerState.isAir()) {
                double distanceScale = nearestWorld == null ? 1.0 : Math.max(0.35, 1.0 / (1.0 + Math.sqrt(controller.distSqr(nearestWorld)) * 0.2));
                double controllerDamage = damage * distanceScale;
                CreateContraptionAnchorDamage.damageBlock(level, controller, controllerState, controllerDamage, overloaded ? 0.85 : 1.15);
                CreateContraptionAnchorDamage.damageSupports(level, controller, controllerDamage * 0.45);
                debug("Create dynamic controller target: {} at {}, nearestPart={}, damage={}", blockPath(controllerState), controller, nearestWorld, fmt(controllerDamage));
                damaged = true;
                String path = blockPath(controllerState);
                if (path.contains("pulley")) {
                    damaged |= CreateContraptionAnchorDamage.damagePulleyLine(level, controller, impactPoint, damage * 0.65);
                }
                if (path.contains("gantry")) {
                    damaged |= CreateContraptionAnchorDamage.damageNearbyCreateBlocks(level, controller, CREATE_GANTRY_PATHS, damage * 0.65, 4);
                }
                if (path.equals("cart_assembler")) {
                    damaged |= CreateContraptionAnchorDamage.damageNearbyMinecarts(level, controller, damage);
                }
            }
        }
        return damaged;
    }

    private static void applyDynamicStructureImpulse(Vector3d impactPoint, double impactEnergy, CreateContraptionLoadAnalyzer.Result load) {
        Entity entity = load.entity();
        if (entity == null || impactEnergy <= 0.0) {
            return;
        }
        BlockPos target = load.nearestWorldPos() != null ? load.nearestWorldPos() : entity.blockPosition();
        Vec3 direction = new Vec3((double)target.getX() + 0.5 - impactPoint.x, (double)target.getY() + 0.5 - impactPoint.y, (double)target.getZ() + 0.5 - impactPoint.z);
        double lengthSq = direction.lengthSqr();
        if (!Double.isFinite(lengthSq) || lengthSq < 1.0E-6) {
            direction = new Vec3(entity.getX() - impactPoint.x, entity.getY() - impactPoint.y, entity.getZ() - impactPoint.z);
            lengthSq = direction.lengthSqr();
        }
        if (!Double.isFinite(lengthSq) || lengthSq < 1.0E-6) {
            return;
        }
        double loadRatio = load.overloaded() ? Math.max(load.overloadRatio(), 1.0) : impactEnergy / Math.max(load.threshold(), 1.0);
        double mass = Math.max(load.mass(), 1.0);
        double delta = Math.sqrt(Math.max(impactEnergy, 0.0)) / (mass + 12.0) * 0.04 * Math.min(4.0, Math.max(0.2, loadRatio));
        double maxDelta = Math.max(0.0, (Double)TrueImpactConfig.PAIR_REACTION_MAX_VELOCITY_CHANGE.get()) * 0.35;
        if (maxDelta > 0.0) {
            delta = Math.min(delta, maxDelta);
        }
        if (!Double.isFinite(delta) || delta <= 0.0) {
            return;
        }
        Vec3 impulse = direction.normalize().scale(delta);
        entity.setDeltaMovement(entity.getDeltaMovement().add(impulse));
        entity.hurt(entity.level().damageSources().generic(), (float)Math.min(12.0, Math.max(0.25, delta * 6.0)));
        debug("Create dynamic impulse: entity={}, impulse={}, loadRatio={}, mass={}", load.entityClass(), impulse, fmt(loadRatio), fmt(mass));
    }

    private static boolean damageNearestCreateTrack(ServerLevel level, Vector3d impactPoint, double damage) {
        BlockPos center = BlockPos.containing((double)impactPoint.x, (double)impactPoint.y, (double)impactPoint.z);
        for (int y = 0; y >= -4; --y) {
            for (BlockPos scan : BlockPos.betweenClosed((BlockPos)center.offset(-2, y, -2), (BlockPos)center.offset(2, y, 2))) {
                BlockState state = level.getBlockState(scan);
                if (!isCreatePath(state, CREATE_TRACK_PATHS)) continue;
                CreateContraptionAnchorDamage.damageBlock(level, scan.immutable(), state, damage, 0.75);
                debug("Create train track target: {} at {}, damage={}", blockPath(state), scan.immutable(), fmt(damage));
                return true;
            }
        }
        return false;
    }

    private static boolean damagePulleyLine(ServerLevel level, BlockPos controller, Vector3d impactPoint, double damage) {
        int direction = impactPoint.y < (double)controller.getY() ? -1 : 1;
        boolean damaged = false;
        int distance = Math.min(32, Math.max(1, (int)Math.abs(impactPoint.y - (double)controller.getY()) + 2));
        for (int i = 1; i <= distance; ++i) {
            BlockPos scan = controller.offset(0, i * direction, 0);
            BlockState state = level.getBlockState(scan);
            if (!isCreatePath(state, CREATE_ROPE_PATHS) && !blockPath(state).contains("rope")) continue;
            CreateContraptionAnchorDamage.damageBlock(level, scan, state, damage, 0.65);
            debug("Create pulley line target: {} at {}, damage={}", blockPath(state), scan, fmt(damage));
            damaged = true;
        }
        return damaged;
    }

    private static boolean damageNearbyCreateBlocks(ServerLevel level, BlockPos center, Set<String> paths, double damage, int radius) {
        boolean damaged = false;
        for (BlockPos scan : BlockPos.betweenClosed((BlockPos)center.offset(-radius, -radius, -radius), (BlockPos)center.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(scan);
            if (!isCreatePath(state, paths)) continue;
            CreateContraptionAnchorDamage.damageBlock(level, scan.immutable(), state, damage, 0.85);
            debug("Create nearby target: {} at {}, damage={}", blockPath(state), scan.immutable(), fmt(damage));
            damaged = true;
        }
        return damaged;
    }

    private static boolean damageNearbyMinecarts(ServerLevel level, BlockPos center, double damage) {
        AABB box = new AABB(center).inflate(3.0);
        boolean damaged = false;
        for (Entity entity : level.getEntities((Entity)null, box, entity -> entity instanceof AbstractMinecart)) {
            float minecartDamage = (float)Math.min(40.0, Math.max(2.0, damage * 0.02));
            entity.hurt(level.damageSources().generic(), minecartDamage);
            debug("Create minecart target: {} at {}, damage={}", entity.getType(), entity.blockPosition(), fmt(minecartDamage));
            damaged = true;
        }
        return damaged;
    }

    private static void damageSupports(ServerLevel level, BlockPos anchorPos, double damage) {
        for (Direction direction : Direction.values()) {
            BlockPos supportPos = anchorPos.relative(direction);
            BlockState supportState = level.getBlockState(supportPos);
            if (supportState.isAir() || CreateContraptionAnchorDamage.isCreateAnchor(supportState)) continue;
            CreateContraptionAnchorDamage.damageBlock(level, supportPos, supportState, damage, 1.75);
        }
    }

    private static void damageBlock(ServerLevel level, BlockPos pos, BlockState state, double damage, double thresholdMultiplier) {
        if (state.isAir() || state.is(Blocks.BEDROCK) || state.getDestroySpeed((BlockGetter)level, pos) < 0.0f || !MaterialImpactProperties.isDestructible(state, true)) {
            return;
        }
        double baseStrength = MaterialImpactProperties.baseStrength((BlockGetter)level, pos, state);
        double threshold = MaterialImpactProperties.breakThreshold(state, baseStrength) * thresholdMultiplier;
        BlockDamageAccumulator.apply(level, pos, MaterialImpactProperties.fatigueDamage(state, damage), threshold, pos.hashCode() ^ 0x5171C0DE);
    }

    private static boolean isCreateAnchor(BlockState state) {
        return isCreatePath(state, CREATE_ANCHOR_PATHS);
    }

    private static boolean isCreatePath(BlockState state, Set<String> paths) {
        if (state.isAir()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return "create".equals(id.getNamespace()) && paths.contains(id.getPath());
    }

    private static String blockPath(BlockState state) {
        if (state.isAir()) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return "create".equals(id.getNamespace()) ? id.getPath() : "";
    }

    private static void debug(String message, Object ... args) {
        if (((Boolean)TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()).booleanValue()) {
            LOGGER.info("[TrueImpact] " + message, args);
        }
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
