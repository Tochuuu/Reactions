package me.tochuuu.reactions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class PlayerEyeRenderLayer extends RenderLayer {
    private static final float SKIN_SIZE = 64.0F;
    private static final float HEAD_FRONT_U = 8.0F;
    private static final float HEAD_FRONT_V = 8.0F;
    private static final float HEAD_FACE_Z = -4.004F / 16.0F;
    private static final float MOUTH_COVER_FACE_Z = -4.018F / 16.0F;
    private static final float MOUTH_FACE_Z = -4.026F / 16.0F;
    private static final float MOUTH_UV_INSET = 0.125F;
    private static final float ADVANCEMENT_MOUTH_TOP_EXTENSION = 0.001F;
    private static final int NORMAL_COLOR = 0xFFFFFFFF;
    private static final int EYELID_DARKEN_COLOR = 0xFFB0B0B0;
    private static final int IDLE_LOOK_DELAY_TICKS = 240;
    private static final int IDLE_LOOK_STEP_TICKS = 14;
    private static final int IDLE_LOOK_ANIMATION_TICKS = IDLE_LOOK_STEP_TICKS * 3;
    private static final int IDLE_LOOK_CYCLE_TICKS = IDLE_LOOK_DELAY_TICKS + IDLE_LOOK_ANIMATION_TICKS;
    private static final int BOW_FULL_CHARGE_TICKS = 20;
    private static final float SQUINT_VISIBLE_EYE_COVERAGE = 0.5F;
    private static final EyeSettings DEFAULT_EYES = new EyeSettings(9, 12, 13, 12, false, 11, 14, 12, 14, 10, 11, 2, 1);
    private static final java.util.Map<Integer, Float> IDLE_STARTED_AT = new java.util.HashMap<>();

    public PlayerEyeRenderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int light, Entity entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        ReactionsClientConfig config = ReactionsClientConfig.get();
        if (!config.enabled || player.isInvisible()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean isSelf = minecraft.player != null && minecraft.player.getId() == player.getId();
        RemoteEyeConfig remoteConfig = isSelf ? null : ReactionsNetworking.remoteConfig(player.getId());
        ResourceLocation texture = player.getSkin().texture();
        boolean canSyncWithServer = ReactionsNetworking.canSyncWithServer();
        ReactionsClientConfig.PlayerOverride playerOverride = !isSelf && remoteConfig == null && !canSyncWithServer
            ? config.playerOverride(playerName(player))
            : null;
        boolean useDefaultOfflineEyes = !isSelf && remoteConfig == null && playerOverride == null && !canSyncWithServer && isDefaultPlayerSkin(texture);
        if (!isSelf && remoteConfig == null && !useDefaultOfflineEyes && (playerOverride == null || !playerOverride.enabled)) {
            if (config.showMouth) {
                renderMouthOnly(poseStack, bufferSource, light, player, renderType(texture), EyeSettings.local(config));
            }
            return;
        }
        EyeSettings eyes = isSelf ? EyeSettings.local(config) : remoteConfig != null ? EyeSettings.remote(remoteConfig) : useDefaultOfflineEyes ? EyeSettings.defaults() : EyeSettings.override(playerOverride);

        RenderType renderType = renderType(texture);
        boolean animationsEnabled = isSelf ? config.animateSelf : config.animateOthers;
        boolean sleeping = player.isSleeping();
        boolean blinking = !sleeping && animationsEnabled && isBlinking(player, ageInTicks, config);
        int mirroredEye = animationsEnabled && !blinking ? mirroredIdleEye(player, ageInTicks) : 0;
        HumanoidArm spyglassArm = spyglassUseArm(player);
        HumanoidArm bowArm = bowUseArm(player);
        boolean bowSquint = config.animateBowShooting && isBowFullyDrawn(player, bowArm);
        EyeExpression leftEye = eyeExpression(sleeping, animationsEnabled, blinking, spyglassArm == HumanoidArm.LEFT, bowSquint);
        EyeExpression rightEye = eyeExpression(sleeping, animationsEnabled, blinking, spyglassArm == HumanoidArm.RIGHT, bowSquint);

        poseStack.pushPose();
        ((PlayerModel<AbstractClientPlayer>) getParentModel()).head.translateAndRotate(poseStack);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        submitEye(poseStack, consumer, light, overlay, eyes.leftEyeX, eyes.leftEyeY, eyes.eyelidColorX, eyes.eyelidColorY, eyes.eyeWidth, eyes.eyeHeight, leftEye, mirroredEye == -1);
        submitEye(poseStack, consumer, light, overlay, eyes.rightEyeX, eyes.rightEyeY, eyes.eyelidColorX, eyes.eyelidColorY, eyes.eyeWidth, eyes.eyeHeight, rightEye, mirroredEye == 1);
        if (isSelf && AdvancementMouthReaction.active()) {
            submitAdvancementMouth(poseStack, consumer, light, overlay, eyes);
        } else if (eyes.mouthEnabled || config.showMouth) {
            submitMouth(poseStack, consumer, light, overlay, eyes);
        }
        poseStack.popPose();
    }

    private void renderMouthOnly(PoseStack poseStack, MultiBufferSource bufferSource, int light, AbstractClientPlayer player, RenderType renderType, EyeSettings eyes) {
        poseStack.pushPose();
        ((PlayerModel<AbstractClientPlayer>) getParentModel()).head.translateAndRotate(poseStack);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        submitMouth(poseStack, consumer, light, overlay, eyes);
        poseStack.popPose();
    }

    private static void submitEye(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int skinX, int skinY, int eyelidColorX, int eyelidColorY, int eyeWidth, int eyeHeight, EyeExpression expression, boolean mirrored) {
        int clampedSkinX = clamp(skinX, 0, (int) SKIN_SIZE - eyeWidth);
        int clampedSkinY = clamp(skinY, 0, (int) SKIN_SIZE - eyeHeight);
        int clampedEyelidSkinX = clamp(eyelidColorX, 0, (int) SKIN_SIZE - 1);
        int clampedEyelidSkinY = clamp(eyelidColorY, 0, (int) SKIN_SIZE - 1);
        float dstX1 = skinX - HEAD_FRONT_U - 4.0F;
        float dstY1 = skinY - HEAD_FRONT_V - 8.0F;
        float dstY2 = dstY1 + eyeHeight;

        if (expression == EyeExpression.SQUINT) {
            submitSquintEye(poseStack, consumer, light, overlay, clampedSkinX, clampedSkinY, clampedEyelidSkinX, clampedEyelidSkinY, eyeWidth, eyeHeight, dstX1, dstY1, dstY2, mirrored);
            return;
        }

        if (mirrored && expression != EyeExpression.CLOSED) {
            for (int column = 0; column < eyeWidth; column++) {
                int sourceX = clampedSkinX + eyeWidth - 1 - column;
                int sourceY = clampedSkinY;
                float columnDstX1 = dstX1 + column;
                float columnDstX2 = columnDstX1 + 1.0F;
                float u1 = sourceX / SKIN_SIZE;
                float v1 = sourceY / SKIN_SIZE;
                float u2 = (sourceX + 1) / SKIN_SIZE;
                float v2 = (sourceY + eyeHeight) / SKIN_SIZE;
                quad(consumer, poseStack.last(), columnDstX1, dstY1, columnDstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR);
            }
            return;
        }

        int sourceX = clampedSkinX;
        int sourceY;

        if (expression == EyeExpression.CLOSED) {
            sourceX = clampedEyelidSkinX;
            sourceY = clampedEyelidSkinY;
        } else {
            sourceY = clampedSkinY;
        }

        float dstX2 = dstX1 + eyeWidth;

        float u1 = sourceX / SKIN_SIZE;
        float v1 = sourceY / SKIN_SIZE;
        float u2 = (sourceX + (expression == EyeExpression.CLOSED ? 1 : eyeWidth)) / SKIN_SIZE;
        float v2 = (sourceY + (expression == EyeExpression.CLOSED ? 1 : eyeHeight)) / SKIN_SIZE;

        int color = expression == EyeExpression.CLOSED ? EYELID_DARKEN_COLOR : NORMAL_COLOR;
        quad(consumer, poseStack.last(), dstX1, dstY1, dstX2, dstY2, u1, v1, u2, v2, light, overlay, color);
    }

    private record EyeSettings(int leftEyeX, int leftEyeY, int rightEyeX, int rightEyeY, boolean mouthEnabled, int leftMouthX, int leftMouthY, int rightMouthX, int rightMouthY, int eyelidColorX, int eyelidColorY, int eyeWidth, int eyeHeight) {
        private static EyeSettings local(ReactionsClientConfig config) {
            return new EyeSettings(config.leftEyeX, config.leftEyeY, config.rightEyeX, config.rightEyeY, config.showMouth, config.leftMouthX, config.leftMouthY, config.rightMouthX, config.rightMouthY, config.eyelidColorX, config.eyelidColorY, config.eyeWidth, config.eyeHeight);
        }

        private static EyeSettings remote(RemoteEyeConfig config) {
            return new EyeSettings(config.leftEyeX(), config.leftEyeY(), config.rightEyeX(), config.rightEyeY(), config.mouthEnabled(), config.leftMouthX(), config.leftMouthY(), config.rightMouthX(), config.rightMouthY(), config.eyelidColorX(), config.eyelidColorY(), config.eyeWidth(), config.eyeHeight());
        }

        private static EyeSettings override(ReactionsClientConfig.PlayerOverride config) {
            return new EyeSettings(config.leftEyeX, config.leftEyeY, config.rightEyeX, config.rightEyeY, config.showMouth, config.leftMouthX, config.leftMouthY, config.rightMouthX, config.rightMouthY, config.eyelidColorX, config.eyelidColorY, config.eyeWidth, config.eyeHeight);
        }

        private static EyeSettings defaults() {
            return DEFAULT_EYES;
        }
    }

    private enum EyeExpression {
        OPEN,
        SQUINT,
        CLOSED
    }


    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int light, int overlay, int color) {
        vertex(consumer, pose, x1, y2, u1, v2, light, overlay, color);
        vertex(consumer, pose, x2, y2, u2, v2, light, overlay, color);
        vertex(consumer, pose, x2, y1, u2, v1, light, overlay, color);
        vertex(consumer, pose, x1, y1, u1, v1, light, overlay, color);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int light, int overlay, int color) {
        consumer.addVertex(pose, x / 16.0F, y / 16.0F, HEAD_FACE_Z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, 0.0F, 0.0F, -1.0F);
    }

    private static void submitMouth(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, EyeSettings eyes) {
        float dstX1 = eyes.leftMouthX - HEAD_FRONT_U - 4.0F;
        float dstY1 = eyes.leftMouthY - HEAD_FRONT_V - 8.0F;
        submitMouthPixel(poseStack, consumer, light, overlay, eyes.leftMouthX, eyes.leftMouthY, dstX1, dstY1, dstX1 + 1.0F, dstY1 + 1.0F);
        submitMouthPixel(poseStack, consumer, light, overlay, eyes.rightMouthX, eyes.rightMouthY, dstX1 + 1.0F, dstY1, dstX1 + 2.0F, dstY1 + 1.0F);
    }

    private static void submitAdvancementMouth(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, EyeSettings eyes) {
        float coverX = eyes.leftMouthX - HEAD_FRONT_U - 4.0F;
        float coverY = eyes.leftMouthY - HEAD_FRONT_V - 8.0F;
        submitMouthCover(poseStack, consumer, light, overlay, eyes, coverX, coverY, 2.0F, 1.0F);

        float centerX = ((eyes.leftMouthX + 0.5F) + (eyes.rightMouthX + 0.5F)) * 0.5F;
        float centerY = ((eyes.leftMouthY + 0.5F) + (eyes.rightMouthY + 0.5F)) * 0.5F;
        float width = 1.25F;
        float height = 1.25F;
        float dstX1 = centerX - HEAD_FRONT_U - 4.0F - width * 0.5F;
        float dstY1 = centerY - HEAD_FRONT_V - 8.0F - 0.5F - ADVANCEMENT_MOUTH_TOP_EXTENSION;
        float splitX = dstX1 + width * 0.5F;
        submitMouthPixel(poseStack, consumer, light, overlay, eyes.leftMouthX, eyes.leftMouthY, 1.25F, dstX1, dstY1, splitX, dstY1 + height + ADVANCEMENT_MOUTH_TOP_EXTENSION);
        submitMouthPixel(poseStack, consumer, light, overlay, eyes.rightMouthX, eyes.rightMouthY, 1.25F, splitX, dstY1, dstX1 + width, dstY1 + height + ADVANCEMENT_MOUTH_TOP_EXTENSION);
    }

    private static void submitMouthCover(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, EyeSettings eyes, float dstX1, float dstY1, float width, float height) {
        int sourceX = clamp(eyes.leftMouthX - 1, 0, (int) SKIN_SIZE - 1);
        int sourceY = clamp(eyes.leftMouthY, 0, (int) SKIN_SIZE - 1);
        float u1 = (sourceX + MOUTH_UV_INSET) / SKIN_SIZE;
        float v1 = (sourceY + MOUTH_UV_INSET) / SKIN_SIZE;
        float u2 = (sourceX + 1.0F - MOUTH_UV_INSET) / SKIN_SIZE;
        float v2 = (sourceY + 1.0F - MOUTH_UV_INSET) / SKIN_SIZE;
        mouthCoverQuad(consumer, poseStack.last(), dstX1, dstY1, dstX1 + width, dstY1 + height, u1, v1, u2, v2, light, overlay, NORMAL_COLOR);
    }

    private static void submitMouthPixel(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int skinX, int skinY, float dstX1, float dstY1, float dstX2, float dstY2) {
        submitMouthPixel(poseStack, consumer, light, overlay, skinX, skinY, 1.0F, dstX1, dstY1, dstX2, dstY2);
    }

    private static void submitMouthPixel(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int skinX, int skinY, float sourceHeight, float dstX1, float dstY1, float dstX2, float dstY2) {
        int sourceX = clamp(skinX, 0, (int) SKIN_SIZE - 1);
        int sourceY = clamp(skinY, 0, (int) SKIN_SIZE - 1);
        float clampedSourceHeight = Math.min(sourceHeight, SKIN_SIZE - sourceY);
        float u1 = (sourceX + MOUTH_UV_INSET) / SKIN_SIZE;
        float v1 = (sourceY + MOUTH_UV_INSET) / SKIN_SIZE;
        float u2 = (sourceX + 1.0F - MOUTH_UV_INSET) / SKIN_SIZE;
        float v2 = (sourceY + clampedSourceHeight - MOUTH_UV_INSET) / SKIN_SIZE;
        mouthQuad(consumer, poseStack.last(), dstX1, dstY1, dstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR);
    }

    private static void mouthQuad(VertexConsumer consumer, PoseStack.Pose pose, float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int light, int overlay, int color) {
        mouthVertex(consumer, pose, x1, y2, u1, v2, light, overlay, color);
        mouthVertex(consumer, pose, x2, y2, u2, v2, light, overlay, color);
        mouthVertex(consumer, pose, x2, y1, u2, v1, light, overlay, color);
        mouthVertex(consumer, pose, x1, y1, u1, v1, light, overlay, color);
    }

    private static void mouthCoverQuad(VertexConsumer consumer, PoseStack.Pose pose, float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int light, int overlay, int color) {
        mouthCoverVertex(consumer, pose, x1, y2, u1, v2, light, overlay, color);
        mouthCoverVertex(consumer, pose, x2, y2, u2, v2, light, overlay, color);
        mouthCoverVertex(consumer, pose, x2, y1, u2, v1, light, overlay, color);
        mouthCoverVertex(consumer, pose, x1, y1, u1, v1, light, overlay, color);
    }

    private static void mouthVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int light, int overlay, int color) {
        consumer.addVertex(pose, x / 16.0F, y / 16.0F, MOUTH_FACE_Z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, 0.0F, 0.0F, -1.0F);
    }

    private static void mouthCoverVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int light, int overlay, int color) {
        consumer.addVertex(pose, x / 16.0F, y / 16.0F, MOUTH_COVER_FACE_Z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, 0.0F, 0.0F, -1.0F);
    }

    private static void submitSquintEye(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int skinX, int skinY, int eyelidX, int eyelidY, int eyeWidth, int eyeHeight, float dstX1, float dstY1, float dstY2, boolean mirrored) {
        float visibleHeight = Math.max(0.333F, (dstY2 - dstY1) * SQUINT_VISIBLE_EYE_COVERAGE);
        float splitY = Math.max(dstY1, dstY2 - visibleHeight);
        float dstX2 = dstX1 + eyeWidth;
        float eyelidU1 = eyelidX / SKIN_SIZE;
        float eyelidV1 = eyelidY / SKIN_SIZE;
        float eyelidU2 = (eyelidX + 1) / SKIN_SIZE;
        float eyelidV2 = (eyelidY + 1) / SKIN_SIZE;
        quad(consumer, poseStack.last(), dstX1, dstY1, dstX2, splitY, eyelidU1, eyelidV1, eyelidU2, eyelidV2, light, overlay, EYELID_DARKEN_COLOR);

        float sourceVisibleHeight = eyeHeight * SQUINT_VISIBLE_EYE_COVERAGE;
        float sourceY1 = skinY + eyeHeight - sourceVisibleHeight;
        if (mirrored) {
            for (int column = 0; column < eyeWidth; column++) {
                int sourceX = skinX + eyeWidth - 1 - column;
                float columnDstX1 = dstX1 + column;
                float columnDstX2 = columnDstX1 + 1.0F;
                float u1 = sourceX / SKIN_SIZE;
                float v1 = sourceY1 / SKIN_SIZE;
                float u2 = (sourceX + 1) / SKIN_SIZE;
                float v2 = (skinY + eyeHeight) / SKIN_SIZE;
                quad(consumer, poseStack.last(), columnDstX1, splitY, columnDstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR);
            }
            return;
        }

        float u1 = skinX / SKIN_SIZE;
        float v1 = sourceY1 / SKIN_SIZE;
        float u2 = (skinX + eyeWidth) / SKIN_SIZE;
        float v2 = (skinY + eyeHeight) / SKIN_SIZE;
        quad(consumer, poseStack.last(), dstX1, splitY, dstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR);
    }

    private static EyeExpression eyeExpression(boolean sleeping, boolean animationsEnabled, boolean blinking, boolean spyglassClosed, boolean bowSquint) {
        if (sleeping || animationsEnabled && (blinking || spyglassClosed)) {
            return EyeExpression.CLOSED;
        }
        if (animationsEnabled && bowSquint) {
            return EyeExpression.SQUINT;
        }
        return EyeExpression.OPEN;
    }

    private static boolean isBlinking(AbstractClientPlayer player, float ageInTicks, ReactionsClientConfig config) {
        int baseInterval = Math.max(20, config.blinkIntervalTicks);
        int randomWindow = Math.max(20, baseInterval / 2);
        int blinkIndex = Math.max(0, (int) ageInTicks / baseInterval);
        int interval = baseInterval + seededOffset(player.getId(), blinkIndex, randomWindow);
        int phase = Math.floorMod((int) ageInTicks + seededOffset(player.getId(), blinkIndex + 31, randomWindow), interval);
        return phase < config.blinkDurationTicks;
    }

    private static HumanoidArm spyglassUseArm(AbstractClientPlayer player) {
        if (!player.isUsingItem()) {
            return null;
        }

        HumanoidArm useArm = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        ItemStack useStack = player.getUseItem();
        if (useStack.is(Items.SPYGLASS)) {
            return useArm;
        }
        return null;
    }

    private static HumanoidArm bowUseArm(AbstractClientPlayer player) {
        if (!player.isUsingItem()) {
            return null;
        }

        HumanoidArm useArm = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        ItemStack useStack = player.getUseItem();
        if (useStack.is(Items.BOW)) {
            return useArm;
        }
        return null;
    }

    private static boolean isBowFullyDrawn(AbstractClientPlayer player, HumanoidArm bowArm) {
        return bowArm != null && player.getTicksUsingItem() >= BOW_FULL_CHARGE_TICKS;
    }

    private static int seededOffset(int entityId, int index, int maxExclusive) {
        int value = entityId * 73428767 ^ index * 912931;
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;
        return Math.floorMod(value, maxExclusive);
    }

    private static int mirroredIdleEye(AbstractClientPlayer player, float ageInTicks) {
        if (player.walkAnimation.speed() > 0.01F) {
            IDLE_STARTED_AT.remove(player.getId());
            return 0;
        }

        float idleStartedAt = IDLE_STARTED_AT.computeIfAbsent(player.getId(), id -> ageInTicks);
        int idleTicks = (int) (ageInTicks - idleStartedAt);
        int cycleTick = Math.floorMod(idleTicks, IDLE_LOOK_CYCLE_TICKS);
        if (cycleTick < IDLE_LOOK_DELAY_TICKS) {
            return 0;
        }

        int phase = cycleTick - IDLE_LOOK_DELAY_TICKS;
        if (phase < IDLE_LOOK_STEP_TICKS) {
            return -1;
        }
        if (phase < IDLE_LOOK_STEP_TICKS * 2) {
            return 1;
        }
        return 0;
    }

    private static RenderType renderType(ResourceLocation texture) {
        return RenderType.entityCutout(texture);
    }

    private static String playerName(AbstractClientPlayer player) {
        return player.getName().getString();
    }

    private static boolean isDefaultPlayerSkin(ResourceLocation texture) {
        String value = texture.toString();
        return value.equals("minecraft:textures/entity/player/wide/steve.png")
            || value.equals("minecraft:textures/entity/player/slim/alex.png")
            || value.startsWith("minecraft:textures/entity/player/wide/")
            || value.startsWith("minecraft:textures/entity/player/slim/");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
