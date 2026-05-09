package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.Collections;

public final class ExplosionImpactHandler {
    private static final Method GET_CONTAINER = findMethod("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", "getContainer", Level.class);
    private static final Method GET_ALL_SUBLEVELS = findMethod("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer", "getAllSubLevels");
    private static final Method BOUNDING_BOX = findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "boundingBox");

    private ExplosionImpactHandler() {
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get()
                || !TrueImpactConfig.ENABLE_EXPLOSION_IMPACT_FRACTURE.get()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Vec3 center = event.getExplosion().center();
        double radius = Math.max(0.0, event.getExplosion().radius());
        if (radius <= 0.0) {
            return;
        }

        double searchRadius = radius * TrueImpactConfig.EXPLOSION_IMPACT_RADIUS_MULTIPLIER.get();
        double confinement = confinementMultiplier(level, center, radius);
        int processed = 0;
        int maxSubLevels = TrueImpactConfig.EXPLOSION_IMPACT_MAX_SUBLEVELS.get();

        try {
            Object container = GET_CONTAINER.invoke(null, level);
            if (container == null) {
                return;
            }
            for (Object subLevel : subLevels(container)) {
                if (processed >= maxSubLevels) {
                    break;
                }
                AABB bounds = bounds(subLevel);
                Vec3 closest = closestPoint(bounds, center);
                double distance = closest.distanceTo(center);
                if (distance > searchRadius) {
                    continue;
                }

                double falloff = 1.0 - distance / Math.max(searchRadius, 0.001);
                double nearField = 1.0 / (distance * distance + 1.0);
                double force = radius * radius
                        * TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.get()
                        * (0.35 + falloff * 0.65)
                        * (1.0 + nearField)
                        * confinement;
                if (force < TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get()) {
                    continue;
                }

                Vector3d localPoint = new Vector3d(closest.x, closest.y, closest.z);
                Vector3d normal = new Vector3d(closest.x - center.x, closest.y - center.y, closest.z - center.z);
                if (normal.lengthSquared() < 1.0E-8) {
                    normal.set(0.0, 1.0, 0.0);
                }
                SubLevelFracture.tryFracture(subLevel, localPoint, normal, force);
                processed++;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static double confinementMultiplier(ServerLevel level, Vec3 center, double radius) {
        double scale = TrueImpactConfig.EXPLOSION_IMPACT_CONFINEMENT_SCALE.get();
        if (scale <= 0.0) {
            return 1.0;
        }
        int blocked = 0;
        for (Direction direction : Direction.values()) {
            if (isBlocked(level, center, direction, radius)) {
                blocked++;
            }
        }
        double enclosed = blocked / 6.0;
        return 1.0 + enclosed * scale;
    }

    private static boolean isBlocked(ServerLevel level, Vec3 center, Direction direction, double radius) {
        int steps = Math.max(1, (int) Math.ceil(radius));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int step = 1; step <= steps; step++) {
            pos.set(
                    center.x + direction.getStepX() * step,
                    center.y + direction.getStepY() * step,
                    center.z + direction.getStepZ() * step
            );
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0.0f) {
                return true;
            }
        }
        return false;
    }

    private static Iterable<?> subLevels(Object container) throws ReflectiveOperationException {
        Object result = GET_ALL_SUBLEVELS.invoke(container);
        return result instanceof Iterable<?> iterable ? iterable : Collections.emptyList();
    }

    private static AABB bounds(Object subLevel) throws ReflectiveOperationException {
        Object bounds = BOUNDING_BOX.invoke(subLevel);
        return new AABB(number(bounds, "minX"), number(bounds, "minY"), number(bounds, "minZ"),
                number(bounds, "maxX"), number(bounds, "maxY"), number(bounds, "maxZ"));
    }

    private static Vec3 closestPoint(AABB bounds, Vec3 point) {
        return new Vec3(
                clamp(point.x, bounds.minX, bounds.maxX),
                clamp(point.y, bounds.minY, bounds.maxY),
                clamp(point.z, bounds.minZ, bounds.maxZ)
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double number(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return ((Number) method.invoke(target)).doubleValue();
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
}
