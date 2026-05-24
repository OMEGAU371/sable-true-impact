/*
 *  dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle
 */
package com.example.sabletrueimpact;

import java.lang.reflect.Field;

// beta.7 helper — reads the protected `handle` (long) field off any RapierConstraintHandle
// subclass. The 4 constraint mixins call this at RETURN of create() to get the just-allocated
// native handle ID, which is the key we use in RopeBindingRegistry's constraint-anchor map.
// Reflected once and cached; failures are silent (returns 0L) so a missing field never
// crashes the constraint creation path.
public final class ConstraintHandleAccess {

    private static volatile Field HANDLE_FIELD;
    private static volatile boolean RESOLVED = false;

    private ConstraintHandleAccess() {
    }

    public static long getHandle(Object constraintHandle) {
        if (constraintHandle == null) {
            return 0L;
        }
        Field f = HANDLE_FIELD;
        if (f == null && !RESOLVED) {
            synchronized (ConstraintHandleAccess.class) {
                f = HANDLE_FIELD;
                if (f == null && !RESOLVED) {
                    try {
                        Class<?> cls = Class.forName(
                            "dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle");
                        Field found = cls.getDeclaredField("handle");
                        found.setAccessible(true);
                        HANDLE_FIELD = found;
                        f = found;
                    } catch (Throwable t) {
                        // Sable internal layout changed or class missing — give up silently.
                    }
                    RESOLVED = true;
                }
            }
        }
        if (f == null) {
            return 0L;
        }
        try {
            return f.getLong(constraintHandle);
        } catch (Throwable t) {
            return 0L;
        }
    }
}
