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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PlayerEyeRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float SKIN_SIZE = 64.0F;
    private static final float HEAD_FRONT_U = 8.0F;
    private static final float HEAD_FRONT_V = 8.0F;
    private static final float HEAD_FACE_Z = -4.004F / 16.0F;
    private static final int NORMAL_COLOR = 0xFFFFFFFF;
    private static final int EYELID_DARKEN_COLOR = 0xFFB0B0B0;
    private static final int IDLE_LOOK_DELAY_TICKS = 240;
    private static final int IDLE_LOOK_STEP_TICKS = 14;
    private static final int IDLE_LOOK_ANIMATION_TICKS = IDLE_LOOK_STEP_TICKS * 3;
    private static final int IDLE_LOOK_CYCLE_TICKS = IDLE_LOOK_DELAY_TICKS + IDLE_LOOK_ANIMATION_TICKS;
    private static final EyeSettings DEFAULT_EYES = new EyeSettings(9, 12, 13, 12, 10, 11, 2, 1);
    private static final java.util.Map<Integer, Float> IDLE_STARTED_AT = new java.util.HashMap<>();

    public PlayerEyeRenderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
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
            return;
        }
        EyeSettings eyes = isSelf ? EyeSettings.local(config) : remoteConfig != null ? EyeSettings.remote(remoteConfig) : useDefaultOfflineEyes ? EyeSettings.defaults() : EyeSettings.override(playerOverride);

        RenderType renderType = renderType(texture);
        boolean animationsEnabled = isSelf ? config.animateSelf : config.animateOthers;
        boolean sleeping = player.isSleeping();
        boolean blinking = !sleeping && animationsEnabled && isBlinking(player, ageInTicks, config);
        int mirroredEye = animationsEnabled && !blinking ? mirroredIdleEye(player, ageInTicks) : 0;
        HumanoidArm spyglassArm = spyglassUseArm(player);
        boolean closeLeftEye = sleeping || animationsEnabled && (blinking || spyglassArm == HumanoidArm.LEFT);
        boolean closeRightEye = sleeping || animationsEnabled && (blinking || spyglassArm == HumanoidArm.RIGHT);

        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        submitEye(poseStack, consumer, light, overlay, eyes.leftEyeX, eyes.leftEyeY, eyes.eyelidColorX, eyes.eyelidColorY, eyes.eyeWidth, eyes.eyeHeight, closeLeftEye, mirroredEye == -1);
        submitEye(poseStack, consumer, light, overlay, eyes.rightEyeX, eyes.rightEyeY, eyes.eyelidColorX, eyes.eyelidColorY, eyes.eyeWidth, eyes.eyeHeight, closeRightEye, mirroredEye == 1);
        poseStack.popPose();
    }

    private static void submitEye(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int skinX, int skinY, int eyelidColorX, int eyelidColorY, int eyeWidth, int eyeHeight, boolean blinking, boolean mirrored) {
        int clampedSkinX = clamp(skinX, 0, (int) SKIN_SIZE - eyeWidth);
        int clampedSkinY = clamp(skinY, 0, (int) SKIN_SIZE - eyeHeight);
        int clampedEyelidSkinX = clamp(eyelidColorX, 0, (int) SKIN_SIZE - 1);
        int clampedEyelidSkinY = clamp(eyelidColorY, 0, (int) SKIN_SIZE - 1);
        float dstX1 = skinX - HEAD_FRONT_U - 4.0F;
        float dstY1 = skinY - HEAD_FRONT_V - 8.0F;
        float dstY2 = dstY1 + eyeHeight;

        if (mirrored && !blinking) {
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

        if (blinking) {
            sourceX = clampedEyelidSkinX;
            sourceY = clampedEyelidSkinY;
        } else {
            sourceY = clampedSkinY;
        }

        float dstX2 = dstX1 + eyeWidth;

        float u1 = sourceX / SKIN_SIZE;
        float v1 = sourceY / SKIN_SIZE;
        float u2 = (sourceX + (blinking ? 1 : eyeWidth)) / SKIN_SIZE;
        float v2 = (sourceY + (blinking ? 1 : eyeHeight)) / SKIN_SIZE;

        int color = blinking ? EYELID_DARKEN_COLOR : NORMAL_COLOR;
        quad(consumer, poseStack.last(), dstX1, dstY1, dstX2, dstY2, u1, v1, u2, v2, light, overlay, color);
    }

    private record EyeSettings(int leftEyeX, int leftEyeY, int rightEyeX, int rightEyeY, int eyelidColorX, int eyelidColorY, int eyeWidth, int eyeHeight) {
        private static EyeSettings local(ReactionsClientConfig config) {
            return new EyeSettings(config.leftEyeX, config.leftEyeY, config.rightEyeX, config.rightEyeY, config.eyelidColorX, config.eyelidColorY, config.eyeWidth, config.eyeHeight);
        }

        private static EyeSettings remote(RemoteEyeConfig config) {
            return new EyeSettings(config.leftEyeX(), config.leftEyeY(), config.rightEyeX(), config.rightEyeY(), config.eyelidColorX(), config.eyelidColorY(), config.eyeWidth(), config.eyeHeight());
        }

        private static EyeSettings override(ReactionsClientConfig.PlayerOverride config) {
            return new EyeSettings(config.leftEyeX, config.leftEyeY, config.rightEyeX, config.rightEyeY, config.eyelidColorX, config.eyelidColorY, config.eyeWidth, config.eyeHeight);
        }

        private static EyeSettings defaults() {
            return DEFAULT_EYES;
        }
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
