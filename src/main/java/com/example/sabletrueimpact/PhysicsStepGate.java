/*
 *  dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
 */
package com.example.sabletrueimpact;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;

// fork_29: the single SD-pattern discipline True Impact lacked — a LIVE "are we inside a
// physics step right now?" gate.
//
// Sable: Destructive never trusts "ServerTickEvent.Post means the step is over"; before every
// world mutation / sub-level operation it re-checks SubLevelPhysicsSystem.getCurrentlyStepping-
// System() and re-defers if a step is somehow running. That live check is what keeps it
// crash-free. True Impact's ImpactBreakQueue drained on Post WITHOUT verifying — this gate adds
// the verification.
//
// getCurrentlySteppingSystem() THROWS IllegalStateException when no system is stepping (it does
// not return null) — so "threw" == safe, "returned" == mid-step.
public final class PhysicsStepGate {

    private PhysicsStepGate() {
    }

    // True if a Sable physics step is currently in progress on any system — i.e. it is NOT safe
    // to free/rebake a collider or mutate a sub-level's blocks right now.
    public static boolean isMidStep() {
        try {
            return SubLevelPhysicsSystem.getCurrentlySteppingSystem() != null;
        } catch (IllegalStateException notStepping) {
            return false;
        } catch (RuntimeException | LinkageError e) {
            // Unknown state — assume mid-step and defer; deferring is always the safe choice.
            return true;
        }
    }
}
