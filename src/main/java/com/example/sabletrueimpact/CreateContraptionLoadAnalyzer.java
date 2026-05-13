package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CreateContraptionLoadAnalyzer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<UUID, CachedCapacity> CACHE = new ConcurrentHashMap<>();

    private CreateContraptionLoadAnalyzer() {
    }

    public static Result analyze(ServerLevel level, Entity entity) {
        long tick = level.getGameTime();
        CachedCapacity cached = CACHE.get(entity.getUUID());
        if (cached != null && tick - cached.tick() < 40L) {
            return cached.result();
        }

        Result result = compute(level, entity);
        CACHE.put(entity.getUUID(), new CachedCapacity(tick, result));
        return result;
    }

    private static Result compute(ServerLevel level, Entity entity) {
        Object contraption = contraption(entity);
        if (contraption == null) {
            return Result.unknownContraption();
        }

        Scan scan = new Scan(TrueImpactConfig.CREATE_CONTRAPTION_MAX_BLOCKS_SCANNED.get());
        collectBlockStates(level, contraption, scan, new IdentityHashMap<>());

        if (scan.blocks() <= 0) {
            return Result.unknownContraption();
        }

        double capacity = scan.capacity() * TrueImpactConfig.CREATE_CONTRAPTION_LOAD_CAPACITY_SCALE.get();
        Result result = new Result(capacity, scan.blocks(), false);
        if (TrueImpactConfig.CREATE_CONTRAPTION_DEBUG_LOGGING.get()) {
            LOGGER.info("[TrueImpact] Create contraption {} capacity={} blocks={}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                    String.format(java.util.Locale.ROOT, "%.2f", result.capacity()),
                    result.blocks());
        }
        return result;
    }

    private static Object contraption(Entity entity) {
        Object viaMethod = invokeNoArg(entity, "getContraption");
        if (viaMethod != null) {
            return viaMethod;
        }
        return readField(entity, "contraption");
    }

    private static void collectBlockStates(ServerLevel level, Object value, Scan scan, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || scan.isFull() || visited.containsKey(value)) {
            return;
        }
        visited.put(value, Boolean.TRUE);

        if (value instanceof BlockState state) {
            scan.add(level, state);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object entryValue : map.values()) {
                collectBlockStates(level, entryValue, scan, visited);
                if (scan.isFull()) return;
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object entryValue : collection) {
                collectBlockStates(level, entryValue, scan, visited);
                if (scan.isFull()) return;
            }
            return;
        }

        Object state = firstNonNull(
                invokeNoArg(value, "state"),
                invokeNoArg(value, "getState"),
                readField(value, "state"),
                readField(value, "blockState")
        );
        if (state instanceof BlockState blockState) {
            scan.add(level, blockState);
            return;
        }

        Class<?> type = value.getClass();
        if (type.getName().startsWith("java.") || type.isEnum()) {
            return;
        }
        for (Field field : type.getDeclaredFields()) {
            Class<?> fieldType = field.getType();
            if (!(BlockState.class.isAssignableFrom(fieldType)
                    || Map.class.isAssignableFrom(fieldType)
                    || Collection.class.isAssignableFrom(fieldType)
                    || fieldType.getName().startsWith("com.simibubi.create"))) {
                continue;
            }
            try {
                field.setAccessible(true);
                collectBlockStates(level, field.get(value), scan, visited);
                if (scan.isFull()) return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    public record Result(double capacity, int blocks, boolean fallback) {
        public static Result unknownContraption() {
            return new Result(250.0 * TrueImpactConfig.CREATE_CONTRAPTION_LOAD_CAPACITY_SCALE.get(), 0, true);
        }
    }

    private record CachedCapacity(long tick, Result result) {
    }

    private static final class Scan {
        private final int maxBlocks;
        private int blocks;
        private double capacity;

        private Scan(int maxBlocks) {
            this.maxBlocks = Math.max(1, maxBlocks);
        }

        private void add(ServerLevel level, BlockState state) {
            if (isFull() || state.isAir()) {
                return;
            }
            double base = MaterialImpactProperties.baseStrength(level, BlockPos.ZERO, state);
            double strength = Math.max(MaterialImpactProperties.displayStrength(state, base), 1.0);
            double toughness = Math.max(MaterialImpactProperties.displayToughness(state, base), strength);
            double mass = Math.max(0.25, MaterialImpactProperties.getMass(state, 1.0));
            capacity += Math.sqrt(strength * toughness) * (0.75 + Math.sqrt(mass) * 0.25);
            blocks++;
        }

        private boolean isFull() {
            return blocks >= maxBlocks;
        }

        private int blocks() {
            return blocks;
        }

        private double capacity() {
            return capacity;
        }
    }
}
