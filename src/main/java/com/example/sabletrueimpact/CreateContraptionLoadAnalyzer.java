package com.example.sabletrueimpact;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class CreateContraptionLoadAnalyzer {
    private static final String CREATE_CONTRAPTION_ENTITY = "com.simibubi.create.content.contraptions.AbstractContraptionEntity";
    private static final Method GET_CONTRAPTION = findMethod(CREATE_CONTRAPTION_ENTITY, "getContraption");
    private static final Method GET_BLOCKS = findMethod("com.simibubi.create.content.contraptions.Contraption", "getBlocks");
    private static final Method TO_GLOBAL_VECTOR = findMethod(CREATE_CONTRAPTION_ENTITY, "toGlobalVector", Vec3.class, Float.TYPE);
    private static final Field CONTROLLER_POS = findField("com.simibubi.create.content.contraptions.ControlledContraptionEntity", "controllerPos");

    private CreateContraptionLoadAnalyzer() {
    }

    public static Result analyzeNearest(ServerLevel level, Vector3d impactPoint, double impactLoad) {
        if (!((Boolean)TrueImpactConfig.ENABLE_CREATE_CONTRAPTION_LOAD_FAILURE.get()).booleanValue() || impactLoad <= 0.0 || GET_CONTRAPTION == null || GET_BLOCKS == null) {
            return Result.none();
        }
        double range = Math.max(1.0, (Double)TrueImpactConfig.CREATE_CONTRAPTION_LOAD_SCAN_RANGE.get());
        AABB box = new AABB(impactPoint.x - range, impactPoint.y - range, impactPoint.z - range, impactPoint.x + range, impactPoint.y + range, impactPoint.z + range);
        Result nearest = Result.none();
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getEntities((Entity)null, box, entity -> isCreateContraptionEntity(entity))) {
            Result result = analyze(level, entity, impactLoad, impactPoint);
            if (!result.found()) continue;
            double distance = result.nearestBlockDistanceSq();
            if (!(distance < nearestDistance)) continue;
            nearestDistance = distance;
            nearest = result;
        }
        return nearest;
    }

    private static Result analyze(ServerLevel level, Entity entity, double impactLoad, Vector3d impactPoint) {
        try {
            Object contraption = GET_CONTRAPTION.invoke(entity);
            if (contraption == null) {
                return Result.none();
            }
            Object blocksObject = GET_BLOCKS.invoke(contraption);
            if (!(blocksObject instanceof Map<?, ?> blocks) || blocks.isEmpty()) {
                return Result.none();
            }
            int limit = Math.max(1, (Integer)TrueImpactConfig.CREATE_CONTRAPTION_LOAD_SCAN_LIMIT.get());
            int scanned = 0;
            double capacity = 0.0;
            double mass = 0.0;
            double nearestDistanceSq = Double.MAX_VALUE;
            BlockPos nearestLocalPos = null;
            BlockPos nearestWorldPos = null;
            BlockState nearestState = null;
            Collection<?> values = blocks.values();
            for (Object value : values) {
                if (scanned++ >= limit) break;
                BlockState state = blockState(value);
                if (state == null || state.isAir()) continue;
                double baseStrength = MaterialImpactProperties.baseStrength((BlockGetter)level, entity.blockPosition(), state);
                double strength = MaterialImpactProperties.displayStrength(state, baseStrength);
                double toughness = MaterialImpactProperties.displayToughness(state, baseStrength);
                double blockMass = Math.max(0.05, MaterialImpactProperties.getMass(state, 1.0));
                capacity += (strength + toughness * 0.65) * Math.sqrt(blockMass);
                mass += blockMass;
                BlockPos localPos = blockPos(value);
                Vec3 worldCenter = worldCenter(entity, localPos);
                if (localPos == null || worldCenter == null) continue;
                double distanceSq = worldCenter.distanceToSqr(impactPoint.x, impactPoint.y, impactPoint.z);
                if (!(distanceSq < nearestDistanceSq)) continue;
                nearestDistanceSq = distanceSq;
                nearestLocalPos = localPos.immutable();
                nearestWorldPos = BlockPos.containing(worldCenter);
                nearestState = state;
            }
            if (capacity <= 0.0) {
                return Result.none();
            }
            if (nearestState == null) {
                nearestDistanceSq = entity.distanceToSqr(impactPoint.x, impactPoint.y, impactPoint.z);
                nearestWorldPos = entity.blockPosition();
            }
            double scaledCapacity = capacity * (Double)TrueImpactConfig.CREATE_CONTRAPTION_LOAD_CAPACITY_SCALE.get();
            double threshold = scaledCapacity * (Double)TrueImpactConfig.CREATE_CONTRAPTION_LOAD_SAFETY_FACTOR.get();
            boolean overloaded = impactLoad > threshold;
            double overloadRatio = overloaded ? impactLoad / Math.max(threshold, 1.0E-6) : 0.0;
            return new Result(true, overloaded, scaledCapacity, threshold, overloadRatio, mass, Math.min(scanned, values.size()), entity, controllerPos(entity), entity.getClass().getName(), contraption.getClass().getName(), nearestLocalPos, nearestWorldPos, nearestState, nearestDistanceSq);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return Result.none();
        }
    }

    private static BlockState blockState(Object value) {
        if (value instanceof StructureTemplate.StructureBlockInfo info) {
            return info.state();
        }
        try {
            Method stateMethod = value.getClass().getMethod("state");
            Object state = stateMethod.invoke(value);
            return state instanceof BlockState blockState ? blockState : null;
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static BlockPos blockPos(Object value) {
        if (value instanceof StructureTemplate.StructureBlockInfo info) {
            return info.pos();
        }
        try {
            Method posMethod = value.getClass().getMethod("pos");
            Object pos = posMethod.invoke(value);
            return pos instanceof BlockPos blockPos ? blockPos : null;
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Vec3 worldCenter(Entity entity, BlockPos localPos) {
        if (localPos == null) {
            return null;
        }
        Vec3 localCenter = Vec3.atCenterOf(localPos);
        if (TO_GLOBAL_VECTOR != null) {
            try {
                Object value = TO_GLOBAL_VECTOR.invoke(entity, localCenter, 1.0f);
                if (value instanceof Vec3 vec) {
                    return vec;
                }
            }
            catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return entity.position().add(localCenter);
    }

    private static boolean isCreateContraptionEntity(Entity entity) {
        return classNamed(entity.getClass(), CREATE_CONTRAPTION_ENTITY);
    }

    private static BlockPos controllerPos(Entity entity) {
        if (CONTROLLER_POS == null) {
            return null;
        }
        try {
            Object value = CONTROLLER_POS.get(entity);
            return value instanceof BlockPos pos ? pos.immutable() : null;
        }
        catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }

    private static boolean classNamed(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            if (name.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static Method findMethod(String className, String methodName, Class<?> ... parameterTypes) {
        try {
            Method method = Class.forName(className).getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Field findField(String className, String fieldName) {
        try {
            Field field = Class.forName(className).getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    public record Result(boolean found, boolean overloaded, double capacity, double threshold, double overloadRatio, double mass, int scannedBlocks, Entity entity, BlockPos controllerPos, String entityClass, String contraptionClass, BlockPos nearestLocalPos, BlockPos nearestWorldPos, BlockState nearestState, double nearestBlockDistanceSq) {
        public static Result none() {
            return new Result(false, false, 0.0, 0.0, 0.0, 0.0, 0, null, null, "", "", null, null, null, Double.MAX_VALUE);
        }
    }
}
