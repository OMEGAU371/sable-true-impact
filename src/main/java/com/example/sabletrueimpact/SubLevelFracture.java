package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SubLevelFracture {
    private static final Method GET_LEVEL = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getLevel");
    private static final Method GET_HEAT_MAP_MANAGER = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getHeatMapManager");
    private static final Method GET_MASS_TRACKER = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker");
    private static final Method GET_MASS = findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getMass");
    private static final Method GET_CENTER_OF_MASS = findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getCenterOfMass");
    private static final Method ON_SOLID_REMOVED = findMethod("dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager", "onSolidRemoved", BlockPos.class);

    private SubLevelFracture() {
    }

    public static void tryFracture(Object subLevel, Vector3d localPoint, Vector3d normal, double forceAmount) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get()
                || !TrueImpactConfig.ENABLE_SUBLEVEL_FRACTURE.get()
                || subLevel == null
                || forceAmount < TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get()
                || TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_BLOCKS.get() <= 0) {
            return;
        }
        long startedAt = TrueImpactPerformance.start();

        ServerLevel level = level(subLevel);
        if (level == null) {
            return;
        }

        BlockPos center = BlockPos.containing(localPoint.x, localPoint.y, localPoint.z);
        double fracturePower = (forceAmount - TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get())
                * TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.get()
                * structureMultiplier(subLevel, localPoint);
        if (fracturePower <= 0.0) {
            return;
        }

        Vector3d planeNormal = new Vector3d(normal);
        if (planeNormal.lengthSquared() < 1.0E-8) {
            planeNormal.set(0.0, 1.0, 0.0);
        } else {
            planeNormal.normalize();
        }

        CandidateScan scan = candidates(level, center, planeNormal, fracturePower);
        List<Candidate> candidates = scan.candidates();
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());

        int removed = 0;
        int maxBlocks = TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_BLOCKS.get();
        Object heatMapManager = heatMapManager(subLevel);
        for (Candidate candidate : candidates) {
            if (removed >= maxBlocks) {
                break;
            }
            BlockState current = level.getBlockState(candidate.pos());
            if (current.isAir() || current.is(Blocks.BEDROCK) || current.getDestroySpeed(level, candidate.pos()) < 0.0f) {
                continue;
            }
            boolean brokeFromFatigue = BlockDamageAccumulator.apply(
                    level,
                    candidate.pos(),
                    candidate.fatigueDamage() * TrueImpactConfig.SUBLEVEL_FRACTURE_FATIGUE_SCALE.get(),
                    candidate.breakThreshold(),
                    candidate.pos().hashCode() * 23
            );
            if (brokeFromFatigue) {
                notifyRemoved(heatMapManager, candidate.pos());
                removed++;
                continue;
            }
            if (!passesFractureChance(level, candidate)) {
                continue;
            }
            level.destroyBlock(candidate.pos(), true);
            notifyRemoved(heatMapManager, candidate.pos());
            removed++;
        }
        TrueImpactPerformance.recordFracture(startedAt, scan.checkedBlocks(), candidates.size(), removed);
    }

    private static CandidateScan candidates(ServerLevel level, BlockPos center, Vector3d normal, double fracturePower) {
        List<Candidate> result = new ArrayList<>();
        int radius = (int) Math.ceil(TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get());
        double radiusSquared = TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get() * TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get();
        int checked = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distanceSquared = x * x + y * y + z * z;
                    if (distanceSquared > radiusSquared) {
                        continue;
                    }
                    checked++;
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.is(Blocks.BEDROCK) || state.getDestroySpeed(level, pos) < 0.0f
                            || StructuralStrengthAnalyzer.isAdhesiveBlock(state)) {
                        continue;
                    }
                    StructuralStrengthAnalyzer.Result structure = StructuralStrengthAnalyzer.analyze(level, pos, state, normal);
                    if (structure.seamWeakness() <= 0.0) {
                        continue;
                    }
                    double hardness = Math.max(0.05, state.getDestroySpeed(level, pos));
                    double blast = Math.max(0.0, state.getBlock().getExplosionResistance());
                    double resistance = (TrueImpactConfig.BASE_STRENGTH.get()
                            + hardness * TrueImpactConfig.HARDNESS_STRENGTH_FACTOR.get()
                            + blast * TrueImpactConfig.BLAST_STRENGTH_FACTOR.get())
                            * structure.connectionStrength();
                    double impactFocus = impactFocus(distanceSquared);
                    double fatigueDamage = fracturePower * structure.seamWeakness() * impactFocus;
                    double breakThreshold = Math.max(resistance, 1.0);
                    double crackRatio = BlockDamageAccumulator.damageRatio(level, pos, breakThreshold);
                    double crackBonus = 1.0 + crackRatio * TrueImpactConfig.SUBLEVEL_FRACTURE_CRACK_BONUS_SCALE.get();
                    double spreadBonus = 1.0 + structure.weakPlaneSpread() * TrueImpactConfig.SUBLEVEL_FRACTURE_WEAK_PLANE_SPREAD.get();
                    double score = fatigueDamage * crackBonus * spreadBonus / breakThreshold;
                    result.add(new Candidate(pos.immutable(), score, fatigueDamage, breakThreshold));
                }
            }
        }
        return new CandidateScan(result, checked);
    }

    private static double impactFocus(double distanceSquared) {
        double radius = Math.max(0.001, TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get());
        double normalizedDistance = Math.min(1.0, Math.sqrt(distanceSquared) / radius);
        double focus = 1.0 - normalizedDistance;
        return Math.pow(Math.max(0.0, focus), TrueImpactConfig.SUBLEVEL_FRACTURE_IMPACT_FOCUS_EXPONENT.get());
    }

    private static boolean passesFractureChance(ServerLevel level, Candidate candidate) {
        if (candidate.score() >= 1.0) {
            return true;
        }
        double chance = 1.0 - Math.exp(-candidate.score() * TrueImpactConfig.SUBLEVEL_FRACTURE_CHANCE_SCALE.get());
        return level.getRandom().nextDouble() < chance;
    }

    private static double structureMultiplier(Object subLevel, Vector3d localPoint) {
        Object massTracker = massTracker(subLevel);
        double mass = mass(massTracker);
        double massReference = Math.max(1.0, TrueImpactConfig.SUBLEVEL_FRACTURE_MASS_REFERENCE.get());
        double massRatio = Math.max(0.0, mass / massReference);
        double massBonus = 1.0 + Math.log1p(massRatio) * TrueImpactConfig.SUBLEVEL_FRACTURE_MASS_BONUS_SCALE.get();

        Vector3d centerOfMass = centerOfMass(massTracker);
        if (centerOfMass == null) {
            return massBonus;
        }
        double characteristicLength = Math.max(1.0, Math.cbrt(Math.max(mass, 1.0)));
        double offCenter = localPoint.distance(centerOfMass) / characteristicLength;
        double imbalanceBonus = 1.0 + offCenter * TrueImpactConfig.SUBLEVEL_FRACTURE_IMBALANCE_BONUS_SCALE.get();
        imbalanceBonus = Math.min(TrueImpactConfig.SUBLEVEL_FRACTURE_IMBALANCE_MAX_MULTIPLIER.get(), imbalanceBonus);
        return massBonus * imbalanceBonus;
    }

    private static Object massTracker(Object subLevel) {
        try {
            return GET_MASS_TRACKER.invoke(subLevel);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static double mass(Object massTracker) {
        if (massTracker == null) {
            return 1.0;
        }
        try {
            return Math.max(1.0, ((Number) GET_MASS.invoke(massTracker)).doubleValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1.0;
        }
    }

    private static Vector3d centerOfMass(Object massTracker) {
        if (massTracker == null) {
            return null;
        }
        try {
            Object center = GET_CENTER_OF_MASS.invoke(massTracker);
            if (center == null) {
                return null;
            }
            Method x = center.getClass().getMethod("x");
            Method y = center.getClass().getMethod("y");
            Method z = center.getClass().getMethod("z");
            return new Vector3d(((Number) x.invoke(center)).doubleValue(), ((Number) y.invoke(center)).doubleValue(), ((Number) z.invoke(center)).doubleValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static ServerLevel level(Object subLevel) {
        try {
            Object level = GET_LEVEL.invoke(subLevel);
            return level instanceof ServerLevel serverLevel ? serverLevel : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object heatMapManager(Object subLevel) {
        try {
            return GET_HEAT_MAP_MANAGER.invoke(subLevel);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static void notifyRemoved(Object heatMapManager, BlockPos pos) {
        if (heatMapManager == null) {
            return;
        }
        try {
            ON_SOLID_REMOVED.invoke(heatMapManager, pos);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Method findMethod(String className, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = Class.forName(className).getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing Sable method " + className + "#" + methodName, e);
        }
    }

    private record Candidate(BlockPos pos, double score, double fatigueDamage, double breakThreshold) {
    }

    private record CandidateScan(List<Candidate> candidates, int checkedBlocks) {
    }
}
