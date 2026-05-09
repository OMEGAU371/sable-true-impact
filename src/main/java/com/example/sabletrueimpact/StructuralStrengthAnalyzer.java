package com.example.sabletrueimpact;

import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class StructuralStrengthAnalyzer {
    private static long glueCacheTick = Long.MIN_VALUE;
    private static String glueCacheDimension = "";
    private static final Map<Long, Boolean> GLUE_ENTITY_CACHE = new HashMap<>();

    private StructuralStrengthAnalyzer() {
    }

    public static Result analyze(ServerLevel level, BlockPos pos, BlockState state, Vector3d impactNormal) {
        if (isAdhesiveBlock(state) || hasGlueEntity(level, pos)) {
            return new Result(0.0, TrueImpactConfig.SUBLEVEL_FRACTURE_STICKY_RESISTANCE.get(), 0.0);
        }

        double seamWeakness = 0.0;
        double connectionStrength = 1.0;
        double weakPlaneSpread = 0.0;
        int faceConnections = 0;
        int mixedFaceSeams = 0;
        int sameFaceConnections = 0;
        int beamNeighbors = isBeamLike(state) ? 2 : 0;
        int adhesiveNeighbors = 0;

        Direction strongestWeakDirection = null;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighbor = level.getBlockState(neighborPos);
            if (neighbor.isAir()) {
                continue;
            }
            faceConnections++;
            if (isAdhesiveBlock(neighbor) || hasGlueEntity(level, neighborPos)) {
                adhesiveNeighbors++;
                continue;
            }
            if (isBeamLike(neighbor)) {
                beamNeighbors++;
            }

            boolean sameBlock = neighbor.getBlock() == state.getBlock();
            if (sameBlock) {
                sameFaceConnections++;
            } else {
                mixedFaceSeams++;
            }
            Vector3d dir = new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            double planeFactor = 0.35 + Math.abs(dir.dot(impactNormal)) * 0.65;
            double friction = Math.max(PhysicsBlockPropertyHelper.getFriction(state), PhysicsBlockPropertyHelper.getFriction(neighbor));
            double frictionFactor = 1.0 / (1.0 + friction * TrueImpactConfig.SUBLEVEL_FRACTURE_FRICTION_RESISTANCE.get());
            double materialFactor = sameBlock ? 1.0 / Math.max(TrueImpactConfig.SUBLEVEL_FRACTURE_SAME_BLOCK_RESISTANCE.get(), 1.0) : 1.0;
            double seam = materialFactor * planeFactor * frictionFactor;

            double continuity = continuousSeam(level, pos, state, direction);
            double interlock = interlockAround(level, pos, state, direction);
            seam *= (1.0 + continuity * TrueImpactConfig.SUBLEVEL_FRACTURE_CONTINUOUS_SEAM_WEAKNESS.get());
            seam /= (1.0 + interlock * TrueImpactConfig.SUBLEVEL_FRACTURE_INTERLOCK_STRENGTH.get());
            if (seam > seamWeakness) {
                seamWeakness = seam;
                strongestWeakDirection = direction;
            }
            weakPlaneSpread = Math.max(weakPlaneSpread, continuity * seam);
        }

        connectionStrength += faceConnections * 0.22;
        connectionStrength += sameFaceConnections * 0.18;
        connectionStrength += adhesiveNeighbors * TrueImpactConfig.SUBLEVEL_FRACTURE_STICKY_RESISTANCE.get();
        connectionStrength += beamNeighbors * TrueImpactConfig.SUBLEVEL_FRACTURE_BEAM_STRENGTH.get();
        connectionStrength += diagonalInterlock(level, pos, state) * TrueImpactConfig.SUBLEVEL_FRACTURE_INTERLOCK_STRENGTH.get();
        connectionStrength += crossBracing(level, pos, state, strongestWeakDirection) * 0.75;

        if (mixedFaceSeams == 0 && sameFaceConnections > 0) {
            seamWeakness *= 0.6;
        }
        if (isBeamLike(state)) {
            seamWeakness *= 0.45;
        }
        return new Result(seamWeakness, Math.max(1.0, connectionStrength), weakPlaneSpread);
    }

    private static double continuousSeam(ServerLevel level, BlockPos pos, BlockState state, Direction direction) {
        Direction.Axis axis = direction.getAxis();
        double score = 0.0;
        for (Direction tangent : Direction.values()) {
            if (tangent.getAxis() == axis) {
                continue;
            }
            BlockPos side = pos.relative(tangent);
            BlockState sideState = level.getBlockState(side);
            BlockState sideAcross = level.getBlockState(side.relative(direction));
            if (!sideState.isAir() && !sideAcross.isAir()
                    && sideState.getBlock() == state.getBlock()
                    && sideAcross.getBlock() != state.getBlock()) {
                score += 0.35;
            }
        }
        return Math.min(1.5, score);
    }

    private static double interlockAround(ServerLevel level, BlockPos pos, BlockState state, Direction direction) {
        Direction.Axis axis = direction.getAxis();
        double score = 0.0;
        for (Direction tangent : Direction.values()) {
            if (tangent.getAxis() == axis) {
                continue;
            }
            BlockState diagonalA = level.getBlockState(pos.relative(direction).relative(tangent));
            BlockState diagonalB = level.getBlockState(pos.relative(tangent.getOpposite()));
            if (!diagonalA.isAir() && !diagonalB.isAir()
                    && diagonalA.getBlock() == state.getBlock()
                    && diagonalB.getBlock() != state.getBlock()) {
                score += 0.4;
            }
        }
        return Math.min(1.6, score);
    }

    private static double diagonalInterlock(ServerLevel level, BlockPos pos, BlockState state) {
        double score = 0.0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    int manhattan = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if (manhattan < 2) {
                        continue;
                    }
                    BlockState diagonal = level.getBlockState(pos.offset(x, y, z));
                    if (!diagonal.isAir() && diagonal.getBlock() == state.getBlock()) {
                        score += 0.08;
                    }
                }
            }
        }
        return Math.min(1.5, score);
    }

    private static double crossBracing(ServerLevel level, BlockPos pos, BlockState state, Direction weakDirection) {
        if (weakDirection == null) {
            return 0.0;
        }
        double score = 0.0;
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == weakDirection.getAxis()) {
                continue;
            }
            if (isBeamLike(level.getBlockState(pos.relative(direction)))
                    || isBeamLike(level.getBlockState(pos.relative(direction).relative(weakDirection)))) {
                score += 0.45;
            }
        }
        return Math.min(2.0, score);
    }

    public static boolean isAdhesiveBlock(BlockState state) {
        if (state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK)) {
            return true;
        }
        ResourceLocation id = state.getBlock().builtInRegistryHolder().key().location();
        String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.contains("glue")
                || path.contains("sticky")
                || path.contains("sticker")
                || path.contains("honey")
                || namespace.contains("aeronautics") && path.contains("adhesive");
    }

    public static boolean isBeamLike(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        String path = state.getBlock().builtInRegistryHolder().key().location().getPath().toLowerCase(Locale.ROOT);
        return path.contains("girder")
                || path.contains("beam")
                || path.contains("brace")
                || path.contains("strut")
                || path.contains("support")
                || path.contains("frame")
                || path.contains("chassis");
    }

    private static boolean hasGlueEntity(ServerLevel level, BlockPos pos) {
        resetGlueCacheIfNeeded(level);
        long key = pos.asLong();
        Boolean cached = GLUE_ENTITY_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        boolean result = false;
        AABB box = new AABB(pos).inflate(0.08);
        for (Entity entity : level.getEntities((Entity) null, box, entity -> true)) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (id == null) {
                continue;
            }
            String path = id.getPath().toLowerCase(Locale.ROOT);
            String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
            if (path.contains("glue") || path.contains("adhesive") || namespace.contains("aeronautics") && path.contains("honey")) {
                result = true;
                break;
            }
        }
        GLUE_ENTITY_CACHE.put(key, result);
        return result;
    }

    private static void resetGlueCacheIfNeeded(ServerLevel level) {
        long tick = level.getGameTime();
        String dimension = level.dimension().location().toString();
        if (tick == glueCacheTick && dimension.equals(glueCacheDimension)) {
            return;
        }
        glueCacheTick = tick;
        glueCacheDimension = dimension;
        GLUE_ENTITY_CACHE.clear();
    }

    public record Result(double seamWeakness, double connectionStrength, double weakPlaneSpread) {
    }
}
