package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConfinementFactor (D-3 围压效应).
 * No Minecraft runtime required.
 *
 * Reference values (stone crack ≈ 46J, CRACK_MIN = 3J):
 *   Suspended block (0 solid neighbors)        → confinement = 0.0   → effective = 1.0×
 *   Surface stone (1 stone below)              → confinement ≈ 0.17  → effective ≈ 1.17×
 *   Stone core (6 stone neighbors)             → confinement = 1.0   → effective = 2.0×
 *   Dirt in stone mountain (6 stone neighbors) → confinement = 3.0   → effective = 4.0×
 */
class ConfinementFactorTest {

    // -- suspended block (all air / zero) ------------------------------------------

    @Test
    void all_air_neighbors_gives_zero_confinement() {
        assertEquals(0.0, ConfinementFactor.compute(new double[6], 46.0), 1e-9);
    }

    @Test
    void zero_array_always_zero_regardless_of_victim() {
        assertEquals(0.0, ConfinementFactor.compute(new double[6], 3.0), 1e-9);
        assertEquals(0.0, ConfinementFactor.compute(new double[6], 500.0), 1e-9);
    }

    // -- stone victim in stone mountain -------------------------------------------

    @Test
    void stone_in_stone_mountain_confinement_is_one() {
        // ratio = 46/46 = 1.0 per face, 6 faces, sum/6 = 1.0
        double cf = ConfinementFactor.compute(new double[]{46, 46, 46, 46, 46, 46}, 46.0);
        assertEquals(1.0, cf, 0.01);
    }

    @Test
    void stone_in_mountain_effective_multiplier_is_two() {
        double cf = ConfinementFactor.compute(new double[]{46, 46, 46, 46, 46, 46}, 46.0);
        assertEquals(2.0, 1.0 + cf, 0.05, "effectiveThreshold = base × (1 + cf) ≈ 2× for stone core");
    }

    // -- surface block (one solid neighbor) ----------------------------------------

    @Test
    void surface_stone_gives_one_sixth_confinement() {
        // Only the block below is solid; 5 neighbors are air (0)
        double cf = ConfinementFactor.compute(new double[]{0, 46, 0, 0, 0, 0}, 46.0);
        assertEquals(1.0 / 6.0, cf, 0.01);
    }

    // -- weak victim, strong neighbors (dirt buried in stone) ----------------------

    @Test
    void weak_victim_strong_neighbors_caps_at_per_face_cap() {
        // dirt crack ≈ 9.9J (blastResist=0.5); stone crack = 46J
        // ratio = 46/9.9 ≈ 4.6 → capped at PER_FACE_CAP=3.0
        // sum = 6 × 3.0, result = 18/6 = 3.0
        double dirtCrack = BlockHardnessProfile.crackThresholdJ(0.5f, 0.5f);
        double cf = ConfinementFactor.compute(
                new double[]{46, 46, 46, 46, 46, 46}, dirtCrack);
        assertEquals(ConfinementFactor.PER_FACE_CAP, cf, 0.05,
                "Dirt victim capped by PER_FACE_CAP when neighbors >> victim");
    }

    @Test
    void weak_victim_in_stone_effective_multiplier_is_four() {
        double dirtCrack = BlockHardnessProfile.crackThresholdJ(0.5f, 0.5f);
        double cf = ConfinementFactor.compute(
                new double[]{46, 46, 46, 46, 46, 46}, dirtCrack);
        assertEquals(4.0, 1.0 + cf, 0.1, "Effective multiplier ≈ 4× for dirt in stone");
    }

    // -- bedrock neighbor (Double.MAX_VALUE) ---------------------------------------

    @Test
    void bedrock_neighbor_is_capped_at_per_face_cap() {
        // One MAX_VALUE neighbor → ratio = ∞, capped to PER_FACE_CAP; rest air
        double cf = ConfinementFactor.compute(new double[]{Double.MAX_VALUE, 0, 0, 0, 0, 0}, 46.0);
        assertEquals(ConfinementFactor.PER_FACE_CAP / 6.0, cf, 0.001);
    }

    // -- indestructible victim (bedrock as victim) ---------------------------------

    @Test
    void indestructible_victim_returns_zero() {
        // MAX_VALUE victimCrack → !isFinite → 0
        double cf = ConfinementFactor.compute(new double[]{46, 46, 46, 46, 46, 46}, Double.MAX_VALUE);
        assertEquals(0.0, cf, 1e-9);
    }

    @Test
    void zero_victim_crack_returns_zero() {
        double cf = ConfinementFactor.compute(new double[]{46, 46, 46, 46, 46, 46}, 0.0);
        assertEquals(0.0, cf, 1e-9);
    }

    // -- ordering: more / stronger neighbors = more confinement -------------------

    @Test
    void more_solid_neighbors_gives_higher_confinement() {
        double v = 46.0;
        double cf1 = ConfinementFactor.compute(new double[]{46, 0, 0, 0, 0, 0}, v);
        double cf3 = ConfinementFactor.compute(new double[]{46, 46, 46, 0, 0, 0}, v);
        double cf6 = ConfinementFactor.compute(new double[]{46, 46, 46, 46, 46, 46}, v);
        assertTrue(cf1 < cf3, "1 neighbor < 3 neighbors");
        assertTrue(cf3 < cf6, "3 neighbors < 6 neighbors");
    }

    @Test
    void stronger_neighbors_give_higher_confinement_than_weaker() {
        double victim = 46.0;
        // All-glass neighbors (crack ≈ 7J) vs all-obsidian neighbors (crack = 500J)
        double cfGlass = ConfinementFactor.compute(new double[]{7, 7, 7, 7, 7, 7}, victim);
        double cfObsidian = ConfinementFactor.compute(new double[]{500, 500, 500, 500, 500, 500}, victim);
        assertTrue(cfGlass < cfObsidian, "Obsidian neighbors confine more than glass neighbors");
    }

    // -- dynamicRadius ------------------------------------------------------------

    @Test
    void dynamic_radius_is_1_for_low_energy() {
        assertEquals(1, ConfinementFactor.dynamicRadius(0));
        assertEquals(1, ConfinementFactor.dynamicRadius(-50));
        assertEquals(1, ConfinementFactor.dynamicRadius(49.9));
        assertEquals(1, ConfinementFactor.dynamicRadius(100));
    }

    @Test
    void dynamic_radius_is_5_for_high_energy() {
        assertEquals(5, ConfinementFactor.dynamicRadius(1600));
        assertEquals(5, ConfinementFactor.dynamicRadius(3200));
        assertEquals(5, ConfinementFactor.dynamicRadius(100_000));
    }

    @Test
    void dynamic_radius_increases_with_energy() {
        int r200 = ConfinementFactor.dynamicRadius(200);
        int r800 = ConfinementFactor.dynamicRadius(800);
        assertTrue(r800 >= r200, "Higher energy → radius must not shrink");
        assertTrue(r200 >= 1, "Radius always ≥ 1");
        assertTrue(r800 <= 5, "Radius always ≤ 5");
    }

    // ---- directional weighting --------------------------------------------------
    // neighborCrackJ order: [above, below, north, south, east, west]
    // impactDir = normalized velocity of impactor = direction force acts on victim

    @Test
    void nan_direction_falls_back_to_isotropic() {
        double[] neighbors = {46, 46, 46, 46, 46, 46};
        double iso         = ConfinementFactor.compute(neighbors, 46.0);
        double nanDir      = ConfinementFactor.compute(neighbors, 46.0,
                Double.NaN, Double.NaN, Double.NaN);
        assertEquals(iso, nanDir, 1e-9, "NaN direction must equal isotropic result");
    }

    @Test
    void full_mountain_directional_equals_isotropic() {
        // Fully surrounded block: confinement should be 1.0 regardless of direction
        double[] neighbors = {46, 46, 46, 46, 46, 46};
        double cf_iso  = ConfinementFactor.compute(neighbors, 46.0);
        double cf_down = ConfinementFactor.compute(neighbors, 46.0, 0, -1, 0);
        double cf_side = ConfinementFactor.compute(neighbors, 46.0, 1,  0, 0);
        assertEquals(cf_iso,  cf_down, 0.01, "Full mountain: directional (↓) ≈ isotropic");
        assertEquals(cf_iso,  cf_side, 0.01, "Full mountain: directional (→) ≈ isotropic");
    }

    @Test
    void surface_block_directional_higher_than_isotropic_for_aligned_impact() {
        // Block sitting on stone ground, hit from above (impactDir = 0,-1,0).
        // [above=air, below=stone, north/south/east/west=air]
        // The stone below is exactly in the impact direction → maximum weight → higher cf.
        double[] neighbors = {0, 46, 0, 0, 0, 0};
        double cf_iso = ConfinementFactor.compute(neighbors, 46.0);
        double cf_dir = ConfinementFactor.compute(neighbors, 46.0, 0, -1, 0);
        assertTrue(cf_dir > cf_iso,
                "Surface block hit from above: directional (" + cf_dir +
                ") should exceed isotropic (" + cf_iso + ")");
    }

    @Test
    void impact_from_opposite_side_reduces_confinement_of_single_neighbor() {
        // Block with stone only above (above=46, rest=0).
        // impactDir = (0,+1,0) = hit from below → stone above is in impact direction → weight=1
        // impactDir = (0,-1,0) = hit from above → stone above has weight=0 → no confinement
        double[] neighbors = {46, 0, 0, 0, 0, 0}; // only above is solid
        double cf_up   = ConfinementFactor.compute(neighbors, 46.0,  0,  1, 0); // hit from below
        double cf_down = ConfinementFactor.compute(neighbors, 46.0,  0, -1, 0); // hit from above
        assertTrue(cf_up > cf_down,
                "Stone above: hit-from-below (" + cf_up +
                ") should confine more than hit-from-above (" + cf_down + ")");
        assertEquals(0.0, cf_down, 1e-9,
                "Stone above with impact from above: weight=0, cf must be 0");
    }

    @Test
    void directional_weight_zero_for_impact_origin_face() {
        // Stone only in the impact direction origin face (where impactor came FROM).
        // impactDir = east (+1,0,0): impactor came from the west → west face weight = 0
        double[] neighbors = {0, 0, 0, 0, 0, 46}; // only west=46
        double cf = ConfinementFactor.compute(neighbors, 46.0, 1, 0, 0);
        assertEquals(0.0, cf, 1e-9,
                "Neighbor only on the face the impactor came from: contributes zero confinement");
    }

    @Test
    void directional_weight_max_for_support_face() {
        // Stone only in the support direction (where force is transmitted to).
        // impactDir = east (+1,0,0): force pushes east → east face is support → weight=1
        double[] neighborsEast = {0, 0, 0, 0, 46, 0}; // only east=46
        double cf_east = ConfinementFactor.compute(neighborsEast, 46.0, 1, 0, 0);
        // normFactor = sum of all weights = 1.0(east) + 4*0.5(perp) + 0.0(west) = 3.0
        // sum = 1.0 * clamp(46/46) = 1.0
        // cf = 1.0 / 3.0 ≈ 0.333
        assertEquals(1.0 / 3.0, cf_east, 0.01,
                "Single support-direction neighbor should give cf ≈ 1/3");
    }

    @Test
    void perpendicular_neighbors_give_medium_confinement() {
        // Only two perpendicular neighbors (north and south), impact from above (0,-1,0).
        // North fn=(0,0,-1): dot((0,0,-1),(0,-1,0)) = 0 → weight = 0.5
        // South fn=(0,0,+1): dot((0,0,+1),(0,-1,0)) = 0 → weight = 0.5
        double[] neighbors = {0, 0, 46, 46, 0, 0}; // north and south solid
        double cf = ConfinementFactor.compute(neighbors, 46.0, 0, -1, 0);
        // sum = 0.5 * 1 + 0.5 * 1 = 1.0; normFactor = 3.0; cf = 1/3
        assertEquals(1.0 / 3.0, cf, 0.01,
                "Two perpendicular stone neighbors: cf ≈ 1/3 with impact-direction weighting");
    }
}
