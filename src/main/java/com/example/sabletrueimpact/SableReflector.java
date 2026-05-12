package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SableReflector {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    // ServerSubLevel
    private static final MethodHandle RUNTIME_ID_GETTER;
    private static final MethodHandle GET_LEVEL;
    private static final MethodHandle GET_MASS_TRACKER;
    private static final MethodHandle GET_HEAT_MAP_MANAGER;

    // MassData
    private static final MethodHandle GET_MASS;
    private static final MethodHandle GET_CENTER_OF_MASS;

    // SubLevel / Pose3d
    private static final MethodHandle LOGICAL_POSE;
    private static final MethodHandle ROTATION_POINT;
    private static final MethodHandle BOUNDING_BOX;
    private static final MethodHandle GET_PLOT;

    // HeatMapManager
    private static final MethodHandle ON_SOLID_REMOVED;

    // Containers
    private static final MethodHandle GET_CONTAINER;
    private static final MethodHandle GET_ALL_SUBLEVELS;

    // System
    private static final MethodHandle GET_SYSTEM_SUBLEVELS;

    // Bounding Box / Vector Accessors Cache
    private static final Map<Class<?>, MethodHandle> X_ACCESSORS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, MethodHandle> Y_ACCESSORS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, MethodHandle> Z_ACCESSORS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, MethodHandle>> BOUNDS_ACCESSORS = new ConcurrentHashMap<>();

    static {
        try {
            Class<?> serverSubLevel = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
            Class<?> subLevel = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Class<?> massData = Class.forName("dev.ryanhcode.sable.api.physics.mass.MassData");
            Class<?> pose3d = Class.forName("dev.ryanhcode.sable.companion.math.Pose3d");
            Class<?> heatMapManager = Class.forName("dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager");
            Class<?> containerApi = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Class<?> serverContainerApi = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            Class<?> physicsSystem = Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");

            RUNTIME_ID_GETTER = findFieldGetter(serverSubLevel, "runtimeId");
            GET_LEVEL = findMethod(serverSubLevel, "getLevel", ServerLevel.class);
            GET_MASS_TRACKER = findMethod(serverSubLevel, "getMassTracker", massData);
            GET_HEAT_MAP_MANAGER = findMethod(serverSubLevel, "getHeatMapManager", heatMapManager);

            GET_MASS = findMethod(massData, "getMass", double.class);
            GET_CENTER_OF_MASS = findMethod(massData, "getCenterOfMass", Object.class);

            LOGICAL_POSE = findMethod(subLevel, "logicalPose", pose3d);
            ROTATION_POINT = findMethod(pose3d, "rotationPoint", Object.class);
            BOUNDING_BOX = findMethod(subLevel, "boundingBox", Object.class);
            GET_PLOT = findMethod(subLevel, "getPlot", Object.class);

            ON_SOLID_REMOVED = findMethod(heatMapManager, "onSolidRemoved", void.class, BlockPos.class);

            GET_CONTAINER = findStaticMethod(containerApi, "getContainer", Object.class, Level.class);
            GET_ALL_SUBLEVELS = findMethod(serverContainerApi, "getAllSubLevels", Iterable.class);

            GET_SYSTEM_SUBLEVELS = findMethod(physicsSystem, "getSubLevels", Object[].class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SableReflector", e);
        }
    }

    private SableReflector() {}

    public static int getRuntimeId(Object subLevel) {
        try { return (int) RUNTIME_ID_GETTER.invoke(subLevel); } catch (Throwable t) { return -1; }
    }

    public static ServerLevel getLevel(Object subLevel) {
        try { return (ServerLevel) GET_LEVEL.invoke(subLevel); } catch (Throwable t) { return null; }
    }

    public static Object getMassTracker(Object subLevel) {
        try { return GET_MASS_TRACKER.invoke(subLevel); } catch (Throwable t) { return null; }
    }

    public static double getMass(Object subLevel) {
        Object tracker = getMassTracker(subLevel);
        if (tracker == null) return 1.0;
        try { return (double) GET_MASS.invoke(tracker); } catch (Throwable t) { return 1.0; }
    }

    public static Vector3d getCenterOfMass(Object subLevel) {
        Object tracker = getMassTracker(subLevel);
        if (tracker == null) return null;
        try {
            Object com = GET_CENTER_OF_MASS.invoke(tracker);
            return com == null ? null : new Vector3d(getX(com), getY(com), getZ(com));
        } catch (Throwable t) { return null; }
    }

    public static Vector3d getRotationPoint(Object subLevel) {
        try {
            Object pose = LOGICAL_POSE.invoke(subLevel);
            Object rp = ROTATION_POINT.invoke(pose);
            return rp == null ? null : new Vector3d(getX(rp), getY(rp), getZ(rp));
        } catch (Throwable t) { return null; }
    }

    public static Vector3d getLinearVelocity(Object subLevel) {
        try {
            // Need to find getPhysics first
            Method m = subLevel.getClass().getMethod("getPhysics");
            m.setAccessible(true);
            Object phys = m.invoke(subLevel);
            if (phys == null) return null;
            Method v = phys.getClass().getMethod("getLinearVelocity");
            v.setAccessible(true);
            return (Vector3d) v.invoke(phys);
        } catch (Throwable t) { return null; }
    }

    public static Object getBoundingBox(Object subLevel) {
        try { return BOUNDING_BOX.invoke(subLevel); } catch (Throwable t) { return null; }
    }

    public static Object getPlot(Object subLevel) {
        try { return GET_PLOT.invoke(subLevel); } catch (Throwable t) { return null; }
    }

    public static Object plotBounds(Object subLevel) {
        Object plot = getPlot(subLevel);
        if (plot == null) return null;
        Map<String, MethodHandle> accessors = BOUNDS_ACCESSORS.computeIfAbsent(plot.getClass(), cl -> new ConcurrentHashMap<>());
        MethodHandle mh = accessors.computeIfAbsent("getBoundingBox", n -> {
            try { return findMethod(plot.getClass(), n, Object.class); } catch (Exception e) { return null; }
        });
        if (mh == null) return null;
        try { return mh.invoke(plot); } catch (Throwable t) { return null; }
    }

    public static Object getHeatMapManager(Object subLevel) {
        try { return GET_HEAT_MAP_MANAGER.invoke(subLevel); } catch (Throwable t) { return null; }
    }

    public static void onSolidRemoved(Object heatMapManager, BlockPos pos) {
        if (heatMapManager == null) return;
        try { ON_SOLID_REMOVED.invoke(heatMapManager, pos); } catch (Throwable ignored) {}
    }

    public static Object getContainer(Level level) {
        try { return GET_CONTAINER.invoke(level); } catch (Throwable t) { return null; }
    }

    @SuppressWarnings("unchecked")
    public static Iterable<Object> getAllSubLevels(Object container) {
        try { return (Iterable<Object>) GET_ALL_SUBLEVELS.invoke(container); } catch (Throwable t) { return Collections.emptyList(); }
    }

    public static Object[] getSystemSubLevels(Object system) {
        try { return (Object[]) GET_SYSTEM_SUBLEVELS.invoke(system); } catch (Throwable t) { return null; }
    }

    public static double getX(Object vec) { return getCoord(vec, X_ACCESSORS, "x"); }
    public static double getY(Object vec) { return getCoord(vec, Y_ACCESSORS, "y"); }
    public static double getZ(Object vec) { return getCoord(vec, Z_ACCESSORS, "z"); }

    public static double getMinX(Object box) { return getBound(box, "minX"); }
    public static double getMinY(Object box) { return getBound(box, "minY"); }
    public static double getMinZ(Object box) { return getBound(box, "minZ"); }
    public static double getMaxX(Object box) { return getBound(box, "maxX"); }
    public static double getMaxY(Object box) { return getBound(box, "maxY"); }
    public static double getMaxZ(Object box) { return getBound(box, "maxZ"); }

    private static double getCoord(Object obj, Map<Class<?>, MethodHandle> cache, String name) {
        if (obj == null) return 0;
        MethodHandle mh = cache.computeIfAbsent(obj.getClass(), cl -> {
            try { return findMethod(cl, name, double.class); } catch (Exception e) { return null; }
        });
        if (mh == null) return 0;
        try { return (double) mh.invoke(obj); } catch (Throwable t) { return 0; }
    }

    private static double getBound(Object obj, String name) {
        if (obj == null) return 0;
        Map<String, MethodHandle> accessors = BOUNDS_ACCESSORS.computeIfAbsent(obj.getClass(), cl -> new ConcurrentHashMap<>());
        MethodHandle mh = accessors.computeIfAbsent(name, n -> {
            try { return findMethod(obj.getClass(), n, double.class); } catch (Exception e) {
                // Try int return type if double fails
                try {
                    MethodHandle intMh = findMethod(obj.getClass(), n, int.class);
                    return MethodHandles.explicitCastArguments(intMh, intMh.type().changeReturnType(double.class));
                } catch (Exception e2) { return null; }
            }
        });
        if (mh == null) return 0;
        try { return (double) mh.invoke(obj); } catch (Throwable t) { return 0; }
    }

    private static MethodHandle findMethod(Class<?> cl, String name, Class<?> rtype, Class<?>... ptypes) throws Exception {
        Method m = null;
        Class<?> current = cl;
        while (current != null) {
            try {
                m = current.getDeclaredMethod(name, ptypes);
                break;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        if (m == null) {
            m = cl.getMethod(name, ptypes);
        }
        m.setAccessible(true);
        return LOOKUP.unreflect(m);
    }

    private static MethodHandle findStaticMethod(Class<?> cl, String name, Class<?> rtype, Class<?>... ptypes) throws Exception {
        Method m = cl.getMethod(name, ptypes);
        m.setAccessible(true);
        return LOOKUP.unreflect(m);
    }

    private static MethodHandle findFieldGetter(Class<?> cl, String name) throws Exception {
        Field f = cl.getDeclaredField(name);
        f.setAccessible(true);
        return LOOKUP.unreflectGetter(f);
    }
}
