package com.example.sabletrueimpact;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EntityImpactHandler {
    private static final Method GET_CONTAINER = findMethod("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", "getContainer", net.minecraft.world.level.Level.class);
    private static final Method GET_ALL_SUBLEVELS = findMethod("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer", "getAllSubLevels");
    private static final Method GET_MASS_TRACKER = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker");
    private static final Method GET_MASS = findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getMass");
    private static final Method BOUNDING_BOX = findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "boundingBox");
    private static final Field LATEST_LINEAR_VELOCITY = findField("dev.ryanhcode.sable.sublevel.ServerSubLevel", "latestLinearVelocity");
    private static final Map<EntityHitKey, Long> LAST_HIT_TICK = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get()
                || !TrueImpactConfig.ENABLE_ENTITY_IMPACT_DAMAGE.get()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        try {
            Object container = GET_CONTAINER.invoke(null, level);
            if (container == null) {
                return;
            }
            Object allSubLevels = GET_ALL_SUBLEVELS.invoke(container);
            if (!(allSubLevels instanceof Iterable<?> subLevels)) {
                return;
            }
            for (Object subLevel : subLevels) {
                damageEntitiesNearSubLevel(level, subLevel);
            }
            cleanup(level.getGameTime());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void damageEntitiesNearSubLevel(ServerLevel level, Object subLevel) throws ReflectiveOperationException {
        Vector3d velocity = velocity(subLevel);
        double speed = velocity.length();
        if (speed < TrueImpactConfig.ENTITY_IMPACT_MIN_SPEED.get()) {
            return;
        }

        double mass = Math.min(TrueImpactConfig.MAX_EFFECTIVE_MASS.get(),
                Math.pow(Math.max(mass(subLevel), 1.0), TrueImpactConfig.MASS_EXPONENT.get()));

        AABB bounds = bounds(subLevel);
        AABB contactBounds = bounds.inflate(TrueImpactConfig.ENTITY_MOVING_IMPACT_CONTACT_MARGIN.get());
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, contactBounds, entity -> entity.isAlive() && !entity.isSpectator())) {
            if (isStandingOnSubLevel(entity.getBoundingBox(), bounds, velocity)) {
                continue;
            }
            Vec3 entityVelocity = entity.getDeltaMovement();
            Vector3d relativeVelocity = new Vector3d(
                    velocity.x - entityVelocity.x,
                    velocity.y - entityVelocity.y,
                    velocity.z - entityVelocity.z
            );
            double relativeSpeed = relativeVelocity.length();
            if (relativeSpeed < TrueImpactConfig.ENTITY_MOVING_IMPACT_MIN_RELATIVE_SPEED.get()) {
                continue;
            }
            double closingSpeed = closingSpeed(entity.getBoundingBox(), bounds, relativeVelocity);
            if (closingSpeed < TrueImpactConfig.ENTITY_MOVING_IMPACT_MIN_CLOSING_SPEED.get()) {
                continue;
            }
            double impactSpeed = Math.min(relativeSpeed, Math.max(closingSpeed, relativeSpeed * 0.35));
            double baseDamage = impactSpeed * impactSpeed * mass * TrueImpactConfig.ENTITY_MOVING_IMPACT_DAMAGE_SCALE.get();
            if (baseDamage < TrueImpactConfig.ENTITY_IMPACT_MIN_DAMAGE.get()) {
                continue;
            }
            EntityHitKey key = new EntityHitKey(entity.getUUID(), System.identityHashCode(subLevel));
            long now = level.getGameTime();
            if (LAST_HIT_TICK.getOrDefault(key, Long.MIN_VALUE) + TrueImpactConfig.ENTITY_IMPACT_COOLDOWN_TICKS.get() > now) {
                continue;
            }
            double maxDamage = TrueImpactConfig.ENTITY_MOVING_IMPACT_MAX_DAMAGE.get();
            float damage = (float) (maxDamage <= 0.0 ? baseDamage : Math.min(maxDamage, baseDamage));
            if (damage >= TrueImpactConfig.ENTITY_IMPACT_MIN_DAMAGE.get() && entity.hurt(level.damageSources().cramming(), damage)) {
                LAST_HIT_TICK.put(key, now);
            }
        }
    }

    private static boolean isStandingOnSubLevel(AABB entityBounds, AABB subLevelBounds, Vector3d subLevelVelocity) {
        if (subLevelVelocity.y > TrueImpactConfig.ENTITY_STANDING_MAX_UPWARD_SPEED.get()) {
            return false;
        }
        double footDelta = entityBounds.minY - subLevelBounds.maxY;
        if (footDelta < -0.05 || footDelta > TrueImpactConfig.ENTITY_STANDING_SUPPORT_TOLERANCE.get()) {
            return false;
        }
        double xOverlap = Math.min(entityBounds.maxX, subLevelBounds.maxX) - Math.max(entityBounds.minX, subLevelBounds.minX);
        double zOverlap = Math.min(entityBounds.maxZ, subLevelBounds.maxZ) - Math.max(entityBounds.minZ, subLevelBounds.minZ);
        return xOverlap > 0.05 && zOverlap > 0.05;
    }

    private static double closingSpeed(AABB entityBounds, AABB subLevelBounds, Vector3d relativeVelocity) {
        Vec3 entityCenter = entityBounds.getCenter();
        double closestX = clamp(entityCenter.x, subLevelBounds.minX, subLevelBounds.maxX);
        double closestY = clamp(entityCenter.y, subLevelBounds.minY, subLevelBounds.maxY);
        double closestZ = clamp(entityCenter.z, subLevelBounds.minZ, subLevelBounds.maxZ);
        Vector3d normal = new Vector3d(entityCenter.x - closestX, entityCenter.y - closestY, entityCenter.z - closestZ);
        if (normal.lengthSquared() < 1.0E-6) {
            normal.set(
                    entityCenter.x - (subLevelBounds.minX + subLevelBounds.maxX) * 0.5,
                    entityCenter.y - (subLevelBounds.minY + subLevelBounds.maxY) * 0.5,
                    entityCenter.z - (subLevelBounds.minZ + subLevelBounds.maxZ) * 0.5
            );
        }
        if (normal.lengthSquared() < 1.0E-6) {
            return 0.0;
        }
        normal.normalize();
        return Math.max(0.0, relativeVelocity.dot(normal));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Vector3d velocity(Object subLevel) throws ReflectiveOperationException {
        Object velocity = LATEST_LINEAR_VELOCITY.get(subLevel);
        Method x = velocity.getClass().getMethod("x");
        Method y = velocity.getClass().getMethod("y");
        Method z = velocity.getClass().getMethod("z");
        return new Vector3d(((Number) x.invoke(velocity)).doubleValue(), ((Number) y.invoke(velocity)).doubleValue(), ((Number) z.invoke(velocity)).doubleValue());
    }

    private static double mass(Object subLevel) throws ReflectiveOperationException {
        Object massTracker = GET_MASS_TRACKER.invoke(subLevel);
        return ((Number) GET_MASS.invoke(massTracker)).doubleValue();
    }

    private static AABB bounds(Object subLevel) throws ReflectiveOperationException {
        Object bounds = BOUNDING_BOX.invoke(subLevel);
        return new AABB(number(bounds, "minX"), number(bounds, "minY"), number(bounds, "minZ"),
                number(bounds, "maxX"), number(bounds, "maxY"), number(bounds, "maxZ"));
    }

    private static double number(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return ((Number) method.invoke(target)).doubleValue();
    }

    private static void cleanup(long gameTime) {
        if (gameTime % 200L != 0L) {
            return;
        }
        LAST_HIT_TICK.entrySet().removeIf(entry -> gameTime - entry.getValue() > 1200L);
    }

    private static Field findField(String className, String fieldName) {
        try {
            Field field = Class.forName(className).getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing Sable field " + className + "#" + fieldName, e);
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

    private record EntityHitKey(UUID entityId, int subLevelId) {
    }
}
