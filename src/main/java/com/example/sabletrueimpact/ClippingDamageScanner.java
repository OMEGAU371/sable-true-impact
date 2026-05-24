package com.example.sabletrueimpact;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

/**
 * Phase C — clipping/penetration damage scanner (穿模硬度).
 *
 * Every {@code clippingScanPeriodTicks} ticks, walks every moving
 * {@link ServerSubLevel} and looks for cells where a non-air sub-level block
 * overlaps a non-air terrain block in world space. When both are present at
 * the same world cell, applies fatigue damage to whichever block has lower
 * material strength; sustained overlap therefore cracks and eventually breaks
 * the weaker side.
 *
 * <p>Designed to fail safe under load:
 * <ul>
 *   <li>A single global {@code clippingMaxBlocksPerScan} budget caps the
 *       total {@link ServerLevel#getBlockState(BlockPos)} lookups per scan
 *       tick. When the budget runs out the remainder of sub-levels is skipped
 *       until the next scan.</li>
 *   <li>Sub-levels with linear speed below
 *       {@code clippingMinSublevelVelocity} are skipped — this stands in for
 *       the assembly-grace window (frozen / freshly-assembled structures
 *       have ≈0 velocity and would otherwise self-destruct on tick one).</li>
 *   <li>Constraint anchor blocks (rope/rotary/fixed/free/generic) are
 *       always immune on both sides — same protection as the impact callback.</li>
 * </ul>
 *
 * Algorithm is intentionally simple for v1 (binary overlap → fatigue add).
 * If the user wants real depth-driven burst-break later we can scan a 1-block
 * neighborhood along the velocity vector to estimate clip depth.
 */
public final class ClippingDamageScanner {
    private static long lastScanTick = Long.MIN_VALUE;

    private ClippingDamageScanner() {}

    public static void maybeScan(Int2ObjectMap<?> activeSubLevels) {
        if (activeSubLevels == null || activeSubLevels.isEmpty()) return;
        if (!((Boolean) TrueImpactConfig.ENABLE_CLIPPING_DAMAGE.get()).booleanValue()) return;

        // Use the level's gameTime as our tick source. Pull it from the first ssl we find.
        long gameTime = -1L;
        for (Object o : activeSubLevels.values()) {
            if (o instanceof ServerSubLevel ssl) {
                ServerLevel lvl = ElasticPairReaction.level(ssl);
                if (lvl != null) { gameTime = lvl.getGameTime(); break; }
            }
        }
        if (gameTime < 0) return;

        int period = ((Integer) TrueImpactConfig.CLIPPING_SCAN_PERIOD_TICKS.get()).intValue();
        if (gameTime - lastScanTick < period) return;
        lastScanTick = gameTime;

        int budget = ((Integer) TrueImpactConfig.CLIPPING_MAX_BLOCKS_PER_SCAN.get()).intValue();
        double minVel = ((Double) TrueImpactConfig.CLIPPING_MIN_SUBLEVEL_VELOCITY.get()).doubleValue();

        for (Object o : activeSubLevels.values()) {
            if (budget <= 0) break;
            if (!(o instanceof ServerSubLevel ssl)) continue;
            try {
                if (ssl.latestLinearVelocity.length() < minVel) continue;
                budget = scanSubLevel(ssl, budget);
            } catch (Throwable t) {
                // Scanner must never disturb the physics step.
            }
        }
    }

    private static int scanSubLevel(ServerSubLevel ssl, int budget) {
        ServerLevel level = ElasticPairReaction.level(ssl);
        if (level == null) return budget;
        AABB bbox = ElasticPairReaction.worldBounds(ssl);
        if (bbox == null) return budget;
        BlockPos plotCenter = ElasticPairReaction.plotCenter(ssl);
        if (plotCenter == null) return budget;

        int minX = (int) Math.floor(bbox.minX);
        int minY = (int) Math.floor(bbox.minY);
        int minZ = (int) Math.floor(bbox.minZ);
        int maxX = (int) Math.ceil(bbox.maxX);
        int maxY = (int) Math.ceil(bbox.maxY);
        int maxZ = (int) Math.ceil(bbox.maxZ);

        BlockPos.MutableBlockPos terrainPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos sublevelEmbedPos = new BlockPos.MutableBlockPos();
        double crackEnergyBase = ((Double) TrueImpactConfig.CLIPPING_CRACK_ENERGY_PER_DEPTH.get()).doubleValue();
        double breakThresholdScale = ((Double) TrueImpactConfig.BREAK_YIELD_THRESHOLD.get()).doubleValue();

        for (int x = minX; x <= maxX && budget > 0; x++) {
            for (int y = minY; y <= maxY && budget > 0; y++) {
                for (int z = minZ; z <= maxZ && budget > 0; z++) {
                    budget--;
                    terrainPos.set(x, y, z);
                    BlockState terrainState = level.getBlockState(terrainPos);
                    if (terrainState.isAir()) continue;
                    if (RopeBindingRegistry.isRopeAnchorBlockType(terrainState)
                            || RopeBindingRegistry.isConstraintAnchorPosition(terrainPos)) continue;

                    // What sub-level block (if any) overlaps this world cell?
                    Vector3d localPoint = ElasticPairReaction.worldToPlotLocal(
                        ssl, x + 0.5, y + 0.5, z + 0.5);
                    if (localPoint == null) continue;
                    int ex = (int) Math.floor(localPoint.x + plotCenter.getX());
                    int ey = (int) Math.floor(localPoint.y + plotCenter.getY());
                    int ez = (int) Math.floor(localPoint.z + plotCenter.getZ());
                    sublevelEmbedPos.set(ex, ey, ez);
                    BlockState sublevelState = level.getBlockState(sublevelEmbedPos);
                    if (sublevelState.isAir()) continue;
                    // Don't damage the terrain block we're checking against itself
                    // (defensive — embedded coords are far away from terrain coords).
                    if (sublevelEmbedPos.equals(terrainPos)) continue;
                    if (RopeBindingRegistry.isRopeAnchorBlockType(sublevelState)
                            || RopeBindingRegistry.isConstraintAnchorPosition(sublevelEmbedPos)) continue;

                    // OVERLAP — pick the weaker block.
                    double terrainStrength = MaterialImpactProperties.baseStrength(level, terrainPos, terrainState);
                    terrainStrength = MaterialImpactProperties.displayStrength(terrainState, terrainStrength);
                    double sublevelStrength = MaterialImpactProperties.baseStrength(level, sublevelEmbedPos, sublevelState);
                    sublevelStrength = MaterialImpactProperties.displayStrength(sublevelState, sublevelStrength);

                    BlockPos victimPos;
                    BlockState victimState;
                    double victimStrength;
                    if (sublevelStrength <= terrainStrength) {
                        if (!MaterialImpactProperties.isDestructible(sublevelState, true)) continue;
                        victimPos = sublevelEmbedPos.immutable();
                        victimState = sublevelState;
                        victimStrength = sublevelStrength;
                    } else {
                        if (!MaterialImpactProperties.isDestructible(terrainState, true)) continue;
                        victimPos = terrainPos.immutable();
                        victimState = terrainState;
                        victimStrength = terrainStrength;
                    }

                    double toughness = Math.max(victimStrength,
                        MaterialImpactProperties.displayToughness(victimState, victimStrength));
                    BlockDamageAccumulator.apply(
                        level, victimPos,
                        MaterialImpactProperties.fatigueDamage(victimState, crackEnergyBase),
                        toughness * breakThresholdScale,
                        ssl.getRuntimeId() ^ victimPos.hashCode());
                }
            }
        }
        return budget;
    }
}
