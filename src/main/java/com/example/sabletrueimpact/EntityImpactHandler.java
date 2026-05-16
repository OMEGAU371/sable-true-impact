/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.neoforge.event.tick.LevelTickEvent$Post
 *  org.joml.Vector3d
 *  org.joml.Vector3dc
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.TrueImpactConfig;
import com.example.sabletrueimpact.TrueImpactPerformance;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class EntityImpactHandler {
    private static final Method GET_CONTAINER = EntityImpactHandler.findMethod("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", "getContainer", Level.class);
    private static final Method GET_ALL_SUBLEVELS = EntityImpactHandler.findMethod("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer", "getAllSubLevels", new Class[0]);
    private static final Method GET_MASS_TRACKER = EntityImpactHandler.findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker", new Class[0]);
    private static final Method GET_MASS = EntityImpactHandler.findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getMass", new Class[0]);
    private static final Method BOUNDING_BOX = EntityImpactHandler.findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "boundingBox", new Class[0]);
    private static final Field LATEST_LINEAR_VELOCITY = EntityImpactHandler.findField("dev.ryanhcode.sable.sublevel.ServerSubLevel", "latestLinearVelocity");
    private static final Map<EntityHitKey, Long> LAST_HIT_TICK = new HashMap<EntityHitKey, Long>();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level;
        if (!(((Boolean)TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue() && ((Boolean)TrueImpactConfig.ENABLE_ENTITY_IMPACT_DAMAGE.get()).booleanValue() && (level = event.getLevel()) instanceof ServerLevel)) {
            return;
        }
        ServerLevel level2 = (ServerLevel)level;
        int interval = (Integer)TrueImpactConfig.ENTITY_IMPACT_SCAN_INTERVAL_TICKS.get();
        if (interval > 1 && level2.getGameTime() % (long)interval != 0L) {
            return;
        }
        long startedAt = TrueImpactPerformance.start();
        int scannedSubLevels = 0;
        int candidateEntities = 0;
        int hits = 0;
        try {
            Object container = GET_CONTAINER.invoke(null, level2);
            if (container == null) {
                return;
            }
            Object allSubLevels = GET_ALL_SUBLEVELS.invoke(container, new Object[0]);
            if (!(allSubLevels instanceof Iterable)) {
                return;
            }
            Iterable subLevels = (Iterable)allSubLevels;
            int maxSubLevels = (Integer)TrueImpactConfig.ENTITY_IMPACT_MAX_SUBLEVELS_PER_SCAN.get();
            for (Object subLevel : subLevels) {
                if (scannedSubLevels >= maxSubLevels) break;
                ++scannedSubLevels;
                EntityScanResult result = EntityImpactHandler.damageEntitiesNearSubLevel(level2, subLevel);
                candidateEntities += result.candidates();
                hits += result.hits();
            }
            EntityImpactHandler.cleanup(level2.getGameTime());
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
        }
        finally {
            TrueImpactPerformance.recordEntityScan(startedAt, scannedSubLevels, candidateEntities, hits);
            TrueImpactPerformance.maybeLog(level2);
        }
    }

    private static EntityScanResult damageEntitiesNearSubLevel(ServerLevel level, Object subLevel) throws ReflectiveOperationException {
        Vector3d velocity = EntityImpactHandler.velocity(subLevel);
        double speed = velocity.length();
        if (speed < (Double)TrueImpactConfig.ENTITY_IMPACT_MIN_SPEED.get()) {
            return EntityScanResult.EMPTY;
        }
        double mass = Math.min((Double)TrueImpactConfig.MAX_EFFECTIVE_MASS.get(), Math.pow(Math.max(EntityImpactHandler.mass(subLevel), 1.0), (Double)TrueImpactConfig.MASS_EXPONENT.get()));
        AABB bounds = EntityImpactHandler.bounds(subLevel);
        AABB contactBounds = bounds.inflate(((Double)TrueImpactConfig.ENTITY_MOVING_IMPACT_CONTACT_MARGIN.get()).doubleValue());
        int candidates = 0;
        int hits = 0;
        for (LivingEntity entity2 : level.getEntitiesOfClass(LivingEntity.class, contactBounds, entity -> entity.isAlive() && !entity.isSpectator())) {
            double impactSpeed;
            double baseDamage;
            double closingSpeed;
            ++candidates;
            if (EntityImpactHandler.isStandingOnSubLevel(entity2.getBoundingBox(), bounds, velocity)) continue;
            Vec3 entityVelocity = entity2.getDeltaMovement();
            Vector3d relativeVelocity = new Vector3d(velocity.x - entityVelocity.x, velocity.y - entityVelocity.y, velocity.z - entityVelocity.z);
            double relativeSpeed = relativeVelocity.length();
            if (relativeSpeed < (Double)TrueImpactConfig.ENTITY_MOVING_IMPACT_MIN_RELATIVE_SPEED.get() || (closingSpeed = EntityImpactHandler.closingSpeed(entity2.getBoundingBox(), bounds, relativeVelocity)) < (Double)TrueImpactConfig.ENTITY_MOVING_IMPACT_MIN_CLOSING_SPEED.get() || (baseDamage = (impactSpeed = Math.min(relativeSpeed, Math.max(closingSpeed, relativeSpeed * 0.35))) * impactSpeed * mass * (Double)TrueImpactConfig.ENTITY_MOVING_IMPACT_DAMAGE_SCALE.get()) < (Double)TrueImpactConfig.ENTITY_IMPACT_MIN_DAMAGE.get()) continue;
            EntityHitKey key = new EntityHitKey(entity2.getUUID(), System.identityHashCode(subLevel));
            long now = level.getGameTime();
            if (LAST_HIT_TICK.getOrDefault(key, Long.MIN_VALUE) + (long)((Integer)TrueImpactConfig.ENTITY_IMPACT_COOLDOWN_TICKS.get()).intValue() > now) continue;
            double maxDamage = (Double)TrueImpactConfig.ENTITY_MOVING_IMPACT_MAX_DAMAGE.get();
            double d = maxDamage <= 0.0 ? baseDamage : Math.min(maxDamage, baseDamage);
            float damage = (float)d;
            if (!((double)damage >= (Double)TrueImpactConfig.ENTITY_IMPACT_MIN_DAMAGE.get()) || !entity2.hurt(level.damageSources().cramming(), damage)) continue;
            LAST_HIT_TICK.put(key, now);
            ++hits;
        }
        return new EntityScanResult(candidates, hits);
    }

    private static boolean isStandingOnSubLevel(AABB entityBounds, AABB subLevelBounds, Vector3d subLevelVelocity) {
        if (subLevelVelocity.y > (Double)TrueImpactConfig.ENTITY_STANDING_MAX_UPWARD_SPEED.get()) {
            return false;
        }
        double footDelta = entityBounds.minY - subLevelBounds.maxY;
        if (footDelta < -0.05 || footDelta > (Double)TrueImpactConfig.ENTITY_STANDING_SUPPORT_TOLERANCE.get()) {
            return false;
        }
        double xOverlap = Math.min(entityBounds.maxX, subLevelBounds.maxX) - Math.max(entityBounds.minX, subLevelBounds.minX);
        double zOverlap = Math.min(entityBounds.maxZ, subLevelBounds.maxZ) - Math.max(entityBounds.minZ, subLevelBounds.minZ);
        return xOverlap > 0.05 && zOverlap > 0.05;
    }

    private static double closingSpeed(AABB entityBounds, AABB subLevelBounds, Vector3d relativeVelocity) {
        double closestZ;
        double closestY;
        Vec3 entityCenter = entityBounds.getCenter();
        double closestX = EntityImpactHandler.clamp(entityCenter.x, subLevelBounds.minX, subLevelBounds.maxX);
        Vector3d normal = new Vector3d(entityCenter.x - closestX, entityCenter.y - (closestY = EntityImpactHandler.clamp(entityCenter.y, subLevelBounds.minY, subLevelBounds.maxY)), entityCenter.z - (closestZ = EntityImpactHandler.clamp(entityCenter.z, subLevelBounds.minZ, subLevelBounds.maxZ)));
        if (normal.lengthSquared() < 1.0E-6) {
            normal.set(entityCenter.x - (subLevelBounds.minX + subLevelBounds.maxX) * 0.5, entityCenter.y - (subLevelBounds.minY + subLevelBounds.maxY) * 0.5, entityCenter.z - (subLevelBounds.minZ + subLevelBounds.maxZ) * 0.5);
        }
        if (normal.lengthSquared() < 1.0E-6) {
            return 0.0;
        }
        normal.normalize();
        return Math.max(0.0, relativeVelocity.dot((Vector3dc)normal));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Vector3d velocity(Object subLevel) throws ReflectiveOperationException {
        if (LATEST_LINEAR_VELOCITY == null) {
            throw new ReflectiveOperationException("latestLinearVelocity field not available");
        }
        Object velocity = LATEST_LINEAR_VELOCITY.get(subLevel);
        if (velocity == null) {
            return new Vector3d(0.0, 0.0, 0.0);
        }
        Method x = velocity.getClass().getMethod("x", new Class[0]);
        Method y = velocity.getClass().getMethod("y", new Class[0]);
        Method z = velocity.getClass().getMethod("z", new Class[0]);
        return new Vector3d(((Number)x.invoke(velocity, new Object[0])).doubleValue(), ((Number)y.invoke(velocity, new Object[0])).doubleValue(), ((Number)z.invoke(velocity, new Object[0])).doubleValue());
    }

    private static double mass(Object subLevel) throws ReflectiveOperationException {
        Object massTracker = GET_MASS_TRACKER.invoke(subLevel, new Object[0]);
        return ((Number)GET_MASS.invoke(massTracker, new Object[0])).doubleValue();
    }

    private static AABB bounds(Object subLevel) throws ReflectiveOperationException {
        Object bounds = BOUNDING_BOX.invoke(subLevel, new Object[0]);
        return new AABB(EntityImpactHandler.number(bounds, "minX"), EntityImpactHandler.number(bounds, "minY"), EntityImpactHandler.number(bounds, "minZ"), EntityImpactHandler.number(bounds, "maxX"), EntityImpactHandler.number(bounds, "maxY"), EntityImpactHandler.number(bounds, "maxZ"));
    }

    private static double number(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, new Class[0]);
        return ((Number)method.invoke(target, new Object[0])).doubleValue();
    }

    private static void cleanup(long gameTime) {
        if (gameTime % 200L != 0L) {
            return;
        }
        LAST_HIT_TICK.entrySet().removeIf(entry -> gameTime - (Long)entry.getValue() > 1200L);
    }

    private static Field findField(String className, String fieldName) {
        try {
            Field field = Class.forName(className).getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        }
        catch (Exception e) {
            return null;
        }
    }

    private static Method findMethod(String className, String methodName, Class<?> ... parameterTypes) {
        try {
            Method method = Class.forName(className).getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        }
        catch (Exception e) {
            // May fail on dedicated server if the class references client-only types (e.g. ClientLevel).
            // Return null; callers are already wrapped in try/catch(RuntimeException).
            return null;
        }
    }

    private record EntityScanResult(int candidates, int hits) {
        private static final EntityScanResult EMPTY = new EntityScanResult(0, 0);
    }

    private record EntityHitKey(UUID entityId, int subLevelId) {
    }
}

