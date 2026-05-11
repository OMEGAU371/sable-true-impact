package com.example.sabletrueimpact.dhcompat;

import com.example.sabletrueimpact.TrueImpactConfig;
import com.example.sabletrueimpact.TrueImpactMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = TrueImpactMod.MODID, value = Dist.CLIENT)
public final class SableDhProxyLodRenderer {
    private static final String DISTANT_HORIZONS_MOD_ID = "distanthorizons";

    private SableDhProxyLodRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !enabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        ClientSubLevelContainer container = ClientSubLevelContainer.getContainer(level);
        if (container == null || container.getLoadedCount() == 0) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (ClientSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) {
                continue;
            }

            BoundingBox3dc bounds = subLevel.boundingBox();
            if (bounds.volume() <= 0.0 || !withinProxyRange(bounds, cameraPos)) {
                continue;
            }

            renderProxyBox(poseStack, lines, bounds);
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static boolean enabled() {
        return TrueImpactConfig.ENABLE_DH_SUBLEVEL_PROXY_LOD.get()
                && ModList.get().isLoaded(DISTANT_HORIZONS_MOD_ID);
    }

    private static boolean withinProxyRange(BoundingBox3dc bounds, Vec3 cameraPos) {
        double centerX = (bounds.minX() + bounds.maxX()) * 0.5;
        double centerY = (bounds.minY() + bounds.maxY()) * 0.5;
        double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5;
        double dx = centerX - cameraPos.x;
        double dy = centerY - cameraPos.y;
        double dz = centerZ - cameraPos.z;
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        double min = TrueImpactConfig.DH_SUBLEVEL_PROXY_MIN_DISTANCE.get();
        double max = TrueImpactConfig.DH_SUBLEVEL_PROXY_MAX_DISTANCE.get();
        return distanceSqr >= min * min && distanceSqr <= max * max;
    }

    private static void renderProxyBox(PoseStack poseStack, VertexConsumer lines, BoundingBox3dc bounds) {
        LevelRenderer.renderLineBox(
                poseStack,
                lines,
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ(),
                0.1F,
                0.8F,
                1.0F,
                0.65F
        );
    }
}
