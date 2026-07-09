package io.github.omegau371.trueimpact.stress;

import io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.MaterialClass;
import io.github.omegau371.trueimpact.stress.StressPropagator.FractureCandidate;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StressPropagator BFS logic.
 * No Minecraft runtime required — uses BlockSampler lambdas to mock block layouts.
 *
 * Threshold reference (MaterialThresholdProfile.threshold):
 *   BRITTLE = 15J   stressLimit = 15 * 0.5  =  7.5 J
 *   WOOD    = 20J   stressLimit = 20 * 1.5  = 30 J
 *   STONE   = 50J   stressLimit = 50 * 2.0  = 100 J
 *   METAL   = 120J  stressLimit = 120 * 3.0 = 360 J
 *
 * Transmission factors:
 *   BRITTLE = 0.80   WOOD = 0.50   STONE = 0.60   METAL = 0.75
 */
class StressPropagatorTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Sampler that holds a single block at the given position. */
    static StressPropagator.BlockSampler single(int x, int y, int z, MaterialClass mc) {
        return (lx, ly, lz) -> (lx == x && ly == y && lz == z) ? mc : null;
    }

    /** Sampler backed by a map of local position → material class. */
    static StressPropagator.BlockSampler map(Map<String, MaterialClass> layout) {
        return (lx, ly, lz) -> layout.get(lx + "," + ly + "," + lz);
    }

    /** Returns the set of (x,y,z) strings for the fracture candidate list. */
    static Set<String> positions(List<FractureCandidate> cs) {
        return cs.stream().map(c -> c.lx() + "," + c.ly() + "," + c.lz())
                .collect(Collectors.toSet());
    }

    // ── Basic transmission / limit values ─────────────────────────────────────

    @Test
    void transmissionFactor_brittle_is_highest() {
        assertTrue(StressPropagator.transmissionFactor(MaterialClass.BRITTLE)
                 > StressPropagator.transmissionFactor(MaterialClass.SOFT_SOIL),
                "brittle transmits more than soft soil");
    }

    @Test
    void stressLimitMultiplier_brittle_is_lowest() {
        assertTrue(StressPropagator.stressLimitMultiplier(MaterialClass.BRITTLE)
                 < StressPropagator.stressLimitMultiplier(MaterialClass.STONE),
                "brittle fails at lower stress multiplier than stone");
    }

    // ── No candidates ────────────────────────────────────────────────────────

    @Test
    void zero_kImpact_returns_empty() {
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 0.0, single(1, 0, 0, MaterialClass.STONE), 5);
        assertTrue(result.isEmpty());
    }

    @Test
    void zero_radius_returns_empty() {
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 500.0, single(1, 0, 0, MaterialClass.STONE), 0);
        assertTrue(result.isEmpty());
    }

    @Test
    void air_neighbors_produce_no_candidates() {
        // Sampler always returns null (all air)
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 1000.0, (lx, ly, lz) -> null, 5);
        assertTrue(result.isEmpty(), "no blocks → no fracture candidates");
    }

    // ── Stone neighbor: fractures above limit ─────────────────────────────────

    @Test
    void stone_neck_fractures_with_high_impact() {
        // Stone at (1,0,0) is a NECK: its only solid neighbor is stone at (2,0,0).
        // solidNeighbors=1 → vulnerabilityMultiplier=0.35 → effectiveLimit=50*2*0.35=35J
        // stress = effectiveSeed(300J)=120J * 0.60 = 72J > 35J → FRACTURE ✓
        // (Without the neck, isolated stone has effectiveLimit=100J and 72J < 100J → no fracture.
        //  The cap of 120J intentionally prevents solid structures from self-destructing at high speed.)
        Map<String, MaterialClass> layout = Map.of(
                "1,0,0", MaterialClass.STONE,
                "2,0,0", MaterialClass.STONE  // gives (1,0,0) solidNeighbors=1
        );
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 300.0, map(layout), 5);

        assertTrue(positions(result).contains("1,0,0"), "stone neck at solidNeighbors=1 should fracture (72J > 35J limit)");
    }

    @Test
    void solid_stone_survives_even_high_impact() {
        // Stone at (1,0,0) fully connected (solidNeighbors=0 in test → effectiveLimit=100J).
        // stress = effectiveSeed(any)=120J * 0.60 = 72J < 100J → survives.
        // This is the fix for "fast-moving sublevel self-destructs in mid-air".
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 10000.0, single(1, 0, 0, MaterialClass.STONE), 5);
        assertTrue(result.isEmpty(), "solid stone must survive even at extreme kImpact (cap=120J prevents cascade)");
    }

    @Test
    void stone_neighbor_survives_low_impact() {
        // stress at (1,0,0) = 100 * 0.60 = 60J < limit(STONE)=100J → NO fracture
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 100.0, single(1, 0, 0, MaterialClass.STONE), 5);

        assertTrue(result.isEmpty(), "60J < 100J limit: stone survives");
    }

    // ── Brittle fractures easily ──────────────────────────────────────────────

    @Test
    void brittle_neighbor_fractures_at_50J() {
        // Glass at (1,0,0). stress = 50 * 0.80 = 40J > limit(BRITTLE)=7.5J
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 50.0, single(1, 0, 0, MaterialClass.BRITTLE), 5);

        assertEquals(1, result.size());
    }

    @Test
    void brittle_fractures_even_at_very_low_impact() {
        // stress = 10 * 0.80 = 8J > limit(BRITTLE)=7.5J
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 10.0, single(1, 0, 0, MaterialClass.BRITTLE), 5);

        assertEquals(1, result.size());
    }

    // ── Metal survives high stress ────────────────────────────────────────────

    @Test
    void metal_neighbor_survives_200J_impact() {
        // stress = 200 * 0.75 = 150J < limit(METAL)=360J → NO fracture
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 200.0, single(1, 0, 0, MaterialClass.METAL), 5);

        assertTrue(result.isEmpty(), "150J < 360J: metal survives");
    }

    // ── Through-propagation ───────────────────────────────────────────────────

    @Test
    void stress_propagates_through_stone_to_fracture_brittle_beyond() {
        // Layout: stone at (1,0,0) well-connected (5 solid neighbours), brittle at (2,0,0).
        // Stone has solid neighbours on all faces except the destroyed block at (0,0,0):
        //   (2,0,0)=brittle, (1,1,0)(1,-1,0)(1,0,1)(1,0,-1)=stone → solidNeighbours=5
        // vulnerabilityMultiplier(5)=1.0 → effectiveLimit(STONE)=50*2.0*1.0=100J
        // kImpact=100J: stress at (1,0,0) = 100*0.60 = 60J < 100J → stone survives
        // Brittle at (2,0,0) solidNeighbours=1 → limit=15*0.5*0.35=2.625J
        // stress at (2,0,0) = 60*0.80 = 48J > 2.625J → FRACTURE
        Map<String, MaterialClass> layout = new HashMap<>();
        layout.put("1,0,0",  MaterialClass.STONE);   // well-connected stone
        layout.put("2,0,0",  MaterialClass.BRITTLE);  // brittle beyond
        layout.put("1,1,0",  MaterialClass.STONE);    // side supports (give stone 5 neighbours)
        layout.put("1,-1,0", MaterialClass.STONE);
        layout.put("1,0,1",  MaterialClass.STONE);
        layout.put("1,0,-1", MaterialClass.STONE);

        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 100.0, map(layout), 5);

        Set<String> pos = positions(result);
        assertTrue(pos.contains("2,0,0"), "brittle beyond well-connected stone should fracture");
        assertFalse(pos.contains("1,0,0"), "well-connected stone (60J < 100J limit) should survive");
    }

    @Test
    void fracture_point_does_not_propagate_further() {
        // Layout: stone at (1,0,0) fractures; stone at (2,0,0) should NOT be visited
        // (fracture stops propagation)
        // 300J: (1,0,0) gets 300*0.60=180J > 100J limit → FRACTURE, stops here
        Map<String, MaterialClass> layout = Map.of(
                "1,0,0", MaterialClass.STONE,
                "2,0,0", MaterialClass.STONE
        );
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 300.0, map(layout), 5);

        Set<String> pos = positions(result);
        assertTrue(pos.contains("1,0,0"), "immediate stone should fracture");
        assertFalse(pos.contains("2,0,0"), "block behind fracture should be unreachable");
    }

    // ── Radius limit ─────────────────────────────────────────────────────────

    @Test
    void blocks_beyond_radius_are_not_reached() {
        // 10 stone blocks in a row. maxRadius=3. Block at (4,0,0) is at dist=4 (outside radius).
        Map<String, MaterialClass> layout = new HashMap<>();
        for (int i = 1; i <= 10; i++) layout.put(i + ",0,0", MaterialClass.STONE);

        // Use low kImpact=200 so only (1,0,0) fractures (180J > 100J), blocking further propagation anyway
        List<FractureCandidate> result1 = StressPropagator.propagate(
                0, 0, 0, 200.0, map(layout), 3);
        Set<String> pos1 = positions(result1);
        assertFalse(pos1.contains("4,0,0"), "block at dist=4 should not be visited (radius=3)");
        assertFalse(pos1.contains("5,0,0"), "blocks far away should not be visited");

        // Use low-stress scenario: 5J → no block fractures, but BFS still stops at radius
        // 5 * 0.60 = 3J < 100J (stone), propagates; 3 * 0.60 = 1.8J, propagates; 1.8 < MIN_STRESS → stops
        List<FractureCandidate> result2 = StressPropagator.propagate(
                0, 0, 0, 5.0, map(layout), 3);
        Set<String> pos2 = positions(result2);
        assertFalse(pos2.contains("4,0,0"), "radius=3 strictly limits BFS reach");
    }

    // ── Multiple components scenario (dumbbell) ───────────────────────────────

    @Test
    void dumbbell_neck_fractures_adjacent_to_impact() {
        // Dumbbell: [metal(-2)=DESTROYED] - [stone(-1)=NECK] - [stone(0)] - [stone(1)] - [metal(2)]
        // Stone at (-1,0,0) has solidNeighbors=1 (stone at (0,0,0) is its only solid neighbor).
        // effectiveSeed = min(400, 120) = 120J
        // stress at (-1,0,0) = 120 * 0.60 = 72J. effectiveLimit(neck, solidNeighbors=1) = 35J.
        // 72J > 35J → FRACTURE ✓
        Map<String, MaterialClass> layout = Map.of(
                "-1,0,0", MaterialClass.STONE,   // neck (solidNeighbors=1 via (0,0,0))
                "0,0,0",  MaterialClass.STONE,   // connector
                "1,0,0",  MaterialClass.STONE,
                "2,0,0",  MaterialClass.METAL
        );
        List<FractureCandidate> result = StressPropagator.propagate(
                -2, 0, 0, 400.0, map(layout), 5);

        assertTrue(positions(result).contains("-1,0,0"),
                "stone neck adjacent to impact fractures (72J > 35J limit)");
    }

    @Test
    void dumbbell_moderate_impact_does_not_fracture_stone_connector() {
        // Same dumbbell, but 100J: stone at (-1,0,0) gets 100*0.60=60J < 100J → survives
        Map<String, MaterialClass> layout = Map.of(
                "-1,0,0", MaterialClass.STONE,
                "1,0,0",  MaterialClass.STONE,
                "2,0,0",  MaterialClass.METAL
        );
        List<FractureCandidate> result = StressPropagator.propagate(
                -2, 0, 0, 100.0, map(layout), 5);

        assertFalse(positions(result).contains("-1,0,0"),
                "60J < 100J stone limit: connector survives at 100J impact");
    }

    // ── Direction weighting ───────────────────────────────────────────────────

    @Test
    void directional_forward_neighbor_gets_higher_stress_than_lateral() {
        // Impact direction +X. Forward (1,0,0) is a NECK (solidNeighbors=1 via stone at (2,0,0)).
        // Lateral (0,1,0) is isolated (solidNeighbors=0).
        // effectiveSeed = min(10000, 120) = 120J (cap applied)
        //   (1,0,0) forward: 120 * 0.60 * 1.0 = 72J. effectiveLimit(neck)=50*2*0.35=35J. 72>35 → FRACTURES ✓
        //   (0,1,0) lateral: 120 * 0.60 * 0.2 = 14.4J. effectiveLimit(isolated)=100J. 14.4<100 → survives ✓
        Map<String, MaterialClass> layout = Map.of(
                "1,0,0", MaterialClass.STONE,   // forward neck
                "2,0,0", MaterialClass.STONE,   // gives (1,0,0) solidNeighbors=1
                "0,1,0", MaterialClass.STONE    // lateral isolated
        );
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 10000.0, map(layout), 5, 1.0, 0.0, 0.0);

        Set<String> pos = positions(result);
        assertTrue(pos.contains("1,0,0"),  "forward stone neck fractures (72J > 35J limit)");
        assertFalse(pos.contains("0,1,0"), "lateral isolated stone survives (14.4J < 100J limit)");
    }

    @Test
    void directional_backward_neighbor_receives_zero_stress() {
        // Impact direction = +X. Block at (-1,0,0) is directly backward.
        // directionWeight for {-1,0,0} against {1,0,0}: cos = -1 → weight = 0.0
        // So even with very high kImpact, the backward block gets 0 stress.
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 10000.0, single(-1, 0, 0, MaterialClass.BRITTLE), 5,
                1.0, 0.0, 0.0);
        assertTrue(result.isEmpty(), "backward block gets zero stress with directional propagation");
    }

    @Test
    void uniform_spread_when_no_direction_given() {
        // No direction (0,0,0) → falls back to uniform spread (weight=1.0 everywhere).
        // Same as original propagate(6-arg).
        List<FractureCandidate> r1 = StressPropagator.propagate(
                0, 0, 0, 300.0, single(1, 0, 0, MaterialClass.STONE), 5);
        List<FractureCandidate> r2 = StressPropagator.propagate(
                0, 0, 0, 300.0, single(1, 0, 0, MaterialClass.STONE), 5, 0, 0, 0);
        assertEquals(r1.size(), r2.size(), "zero-direction == overload with no direction");
    }

    // ── Structural vulnerability (thin neck) ──────────────────────────────────

    @Test
    void single_connection_neck_fractures_at_reduced_threshold() {
        // A stone block at (1,0,0) with only 1 solid neighbor (the block behind it at (2,0,0),
        // since (0,0,0) is the destroyed block = air now).
        // solidNeighbors = 1 → vulnerabilityMultiplier = 0.35
        // effectiveLimit = 50 * 2.0 * 0.35 = 35J
        // Stress received (uniform): 60 * 0.60 = 36J > 35J → FRACTURES
        // Without vulnerability fix: 36J < 100J → would survive
        Map<String, MaterialClass> layout = Map.of(
                "1,0,0", MaterialClass.STONE, // the neck (only 1 solid neighbor: block at 2,0,0)
                "2,0,0", MaterialClass.STONE  // the other side (solid neighbor of neck)
        );
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 60.0, map(layout), 5);

        assertTrue(positions(result).contains("1,0,0"),
                "single-connection stone neck fractures at 36J (limit=35J with vulnerability)");
    }

    @Test
    void fully_embedded_block_uses_full_threshold() {
        // A stone block at (1,0,0) surrounded on ALL OTHER 5 sides by stone.
        // solidNeighbors=5 → vulnerabilityMultiplier=1.0
        // effectiveLimit = 50 * 2.0 * 1.0 = 100J
        // Stress received: 80 * 0.60 = 48J < 100J → survives
        Map<String, MaterialClass> layout = new HashMap<>();
        layout.put("1,0,0", MaterialClass.STONE);  // target
        // 5 solid neighbors of (1,0,0): (2,0,0),(1,1,0),(1,-1,0),(1,0,1),(1,0,-1)
        // Note: (0,0,0) is destroyed = air, so that direction doesn't count
        layout.put("2,0,0",  MaterialClass.STONE);
        layout.put("1,1,0",  MaterialClass.STONE);
        layout.put("1,-1,0", MaterialClass.STONE);
        layout.put("1,0,1",  MaterialClass.STONE);
        layout.put("1,0,-1", MaterialClass.STONE);

        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 80.0, map(layout), 5);

        assertFalse(positions(result).contains("1,0,0"),
                "fully embedded stone survives: 48J < 100J (full threshold, no vulnerability)");
    }

    @Test
    void direction_weight_formula_is_correct() {
        // Forward (cos=1): 0.20 + 0.80*1 = 1.00
        assertEquals(1.00, StressPropagator.directionWeight(new int[]{1,0,0}, 1,0,0), 0.001);
        // Perpendicular (cos=0): 0.20*(1+0) = 0.20
        assertEquals(0.20, StressPropagator.directionWeight(new int[]{0,1,0}, 1,0,0), 0.001);
        // Backward (cos=-1): 0.20*(1-1) = 0.00
        assertEquals(0.00, StressPropagator.directionWeight(new int[]{-1,0,0}, 1,0,0), 0.001);
        // 45-degree (cos=0.707): 0.20 + 0.80*0.707 ≈ 0.766
        double cos45 = 1.0 / Math.sqrt(2);
        assertEquals(0.20 + 0.80 * cos45,
                StressPropagator.directionWeight(new int[]{1,0,0}, cos45, cos45, 0), 0.001);
    }

    @Test
    void vulnerability_multiplier_ordering() {
        // More connections = less vulnerable = higher multiplier
        assertTrue(StressPropagator.vulnerabilityMultiplier(6) >= StressPropagator.vulnerabilityMultiplier(5));
        assertTrue(StressPropagator.vulnerabilityMultiplier(5) >= StressPropagator.vulnerabilityMultiplier(4));
        assertTrue(StressPropagator.vulnerabilityMultiplier(4) >= StressPropagator.vulnerabilityMultiplier(3));
        assertTrue(StressPropagator.vulnerabilityMultiplier(3) >= StressPropagator.vulnerabilityMultiplier(2));
        assertTrue(StressPropagator.vulnerabilityMultiplier(2) >= StressPropagator.vulnerabilityMultiplier(1));
        // Single connection is significantly weaker than fully embedded
        assertTrue(StressPropagator.vulnerabilityMultiplier(1) < 0.5,
                "single-connection neck must be below 50% threshold");
    }

    @Test
    void solid_wood_does_not_staircase_even_at_high_impact() {
        // 2×2×2 wood cube: corner block adjacent to destroyed corner has solidNeighbors=2.
        // WOOD effectiveLimit(solidNeighbors=2) = 20*1.5*0.80 = 24J, stress = 120*0.50 = 60J.
        // Without the fracture-eligibility guard, 60J > 24J → all corner neighbors fracture
        // → remaining 4 blocks form a staircase. With the guard, solidNeighbors=2 is NOT
        // fracture-eligible for WOOD → no staircase.
        Map<String, MaterialClass> cube = Map.of(
                "1,0,0", MaterialClass.WOOD,  // adjacent to destroyed (0,0,0) — solidNeighbors=2
                "0,1,0", MaterialClass.WOOD,  // adjacent to destroyed (0,0,0) — solidNeighbors=2
                "0,0,1", MaterialClass.WOOD,  // adjacent to destroyed (0,0,0) — solidNeighbors=2
                "1,1,0", MaterialClass.WOOD,
                "1,0,1", MaterialClass.WOOD,
                "0,1,1", MaterialClass.WOOD,
                "1,1,1", MaterialClass.WOOD
        );
        List<FractureCandidate> result = StressPropagator.propagate(
                0, 0, 0, 10000.0, map(cube), 5);
        assertTrue(result.isEmpty(),
                "solid wood structure (solidNeighbors≥2) must not cascade-fracture into staircase shape");
    }

    // ── Pack function sanity ──────────────────────────────────────────────────

    @Test
    void pack_produces_unique_keys_for_neighbors() {
        long origin = StressPropagator.pack(0, 0, 0);
        long px = StressPropagator.pack(1, 0, 0);
        long py = StressPropagator.pack(0, 1, 0);
        long pz = StressPropagator.pack(0, 0, 1);
        long nx = StressPropagator.pack(-1, 0, 0);
        long ny = StressPropagator.pack(0, -1, 0);
        long nz = StressPropagator.pack(0, 0, -1);

        Set<Long> keys = Set.of(origin, px, py, pz, nx, ny, nz);
        assertEquals(7, keys.size(), "all 7 positions must produce distinct long keys");
    }

    @Test
    void pack_negative_coords_differ_from_positive() {
        assertNotEquals(StressPropagator.pack(1, 0, 0), StressPropagator.pack(-1, 0, 0));
        assertNotEquals(StressPropagator.pack(0, 1, 0), StressPropagator.pack(0, -1, 0));
        assertNotEquals(StressPropagator.pack(0, 0, 1), StressPropagator.pack(0, 0, -1));
    }

    @Test
    void pack_small_coords_are_bijective() {
        // Verify no collisions for a 5×5×5 cube centered at origin
        Set<Long> keys = new HashSet<>();
        for (int x = -2; x <= 2; x++)
            for (int y = -2; y <= 2; y++)
                for (int z = -2; z <= 2; z++)
                    keys.add(StressPropagator.pack(x, y, z));
        assertEquals(125, keys.size(), "5×5×5 cube must have 125 unique keys");
    }
}
