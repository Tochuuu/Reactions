package me.tochuuu.reactions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.tochuuu.reactions.mixin.ModelPartAccessor;
import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class PlayerEyeRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final float SKIN_SIZE = 64.0F;
    private static final float HEAD_FRONT_U = 8.0F;
    private static final float HEAD_FRONT_V = 8.0F;
    private static final float HEAD_FACE_Z = -4.004F / 16.0F;
    private static final float PUPIL_FACE_Z = -4.010F / 16.0F;
    private static final float MOUTH_COVER_FACE_Z = -4.018F / 16.0F;
    private static final float MOUTH_FACE_Z = -4.026F / 16.0F;
    private static final float EYE_UV_INSET = 0.24F;
    private static final float MOUTH_UV_INSET = 0.125F;
    private static final float ADVANCEMENT_MOUTH_TOP_EXTENSION = 0.001F;
    private static final int NORMAL_COLOR = 0xFFFFFFFF;
    private static final int EYELID_DARKEN_COLOR = 0xFFB0B0B0;
    private static final int LARGE_EYELID_DARKEN_COLOR = 0xFFD0D0D0;
    private static final float EYELID_COLUMN_SHADE_RANGE = 0.08F;
    private static final float EYELID_TOP_SUBTLE_DARK_FACTOR = 0.97F;
    private static final int IDLE_LOOK_DELAY_TICKS = 240;
    private static final int IDLE_LOOK_STEP_TICKS = 14;
    private static final int IDLE_LOOK_ANIMATION_TICKS = IDLE_LOOK_STEP_TICKS * 3;
    private static final int IDLE_LOOK_CYCLE_TICKS = IDLE_LOOK_DELAY_TICKS + IDLE_LOOK_ANIMATION_TICKS;
    private static final int DIRECT_BLOCK_FOCUS_DOWN_SIGNAL = 101;
    private static final int DIRECT_BLOCK_FOCUS_UP_SIGNAL = -101;
    private static final float BOW_FULL_CHARGE_TICKS = 20.0F;
    private static final float SQUINT_VISIBLE_EYE_COVERAGE = 0.5F;
    private static final float HURT_SCLERA_EXTENSION = 0.5F;
    private static final float LOOK_DOWN_SCLERA_BOTTOM_COVERAGE = 0.16F;
    private static final float BLOCK_FOCUS_EYE_THRESHOLD = 0.25F;
    private static final float MOUNTED_BACK_LOOK_THRESHOLD = 75.0F;
    private static final EyeSettings DEFAULT_EYES = new EyeSettings(9, 12, 13, 12, false, 11, 14, 12, 14, 10, 11, 2, 1);
    private static final java.util.Map<Integer, Float> IDLE_STARTED_AT = new java.util.HashMap<>();
    private static final java.util.Map<Integer, DamageEyeReaction> DAMAGE_REACTIONS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, DamageEyeReaction> LAST_DAMAGE_REACTIONS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Integer> DAMAGE_REACTION_STREAKS = new java.util.HashMap<>();

    public PlayerEyeRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float limbSwing, float limbSwingAmount) {
        ReactionsClientConfig config = ReactionsClientConfig.get();
        if (!config.enabled || state.skin == null || state.isInvisible) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean isSelf = minecraft.player != null && minecraft.player.getId() == state.id;
        RemoteEyeConfig remoteConfig = isSelf ? null : ReactionsNetworking.remoteConfig(state.id);
        Identifier texture = state.skin.body().texturePath();
        boolean canSyncWithServer = ReactionsNetworking.canSyncWithServer();
        ReactionsClientConfig.PlayerOverride playerOverride = !isSelf && remoteConfig == null && !canSyncWithServer
            ? config.playerOverride(playerName(state))
            : null;
        boolean useDefaultOfflineEyes = !isSelf && remoteConfig == null && playerOverride == null && !canSyncWithServer && isDefaultPlayerSkin(texture);
        if (!isSelf && remoteConfig == null && !useDefaultOfflineEyes && (playerOverride == null || !playerOverride.enabled)) {
            if (config.showMouth) {
                submitMouthOnly(poseStack, collector, light, state, renderType(texture), config.animateOthers && config.animateMouth, EyeSettings.local(config));
            }
            return;
        }
        EyeSettings eyes = isSelf ? EyeSettings.local(config) : remoteConfig != null ? EyeSettings.remote(remoteConfig) : useDefaultOfflineEyes ? EyeSettings.defaults() : EyeSettings.override(playerOverride);

        RenderType renderType = renderType(texture);
        boolean animationsEnabled = isSelf ? config.animateSelf : config.animateOthers;
        boolean mouthAnimationsEnabled = animationsEnabled && config.animateMouth;
        boolean sleeping = state.hasPose(Pose.SLEEPING);
        PlayerActionAnimationState.Snapshot actionState = PlayerActionAnimationState.snapshot(state.id);
        boolean blinking = !sleeping && animationsEnabled && (isBlinking(state, config) || actionState.landingBlink());
        EyeLook blockFocusEye = animationsEnabled && !blinking ? blockFocusEye(state.id, isSelf) : EyeLook.CENTER;
        EyeLook mountedEyeLook = animationsEnabled && !blinking ? mountedBackLook(actionState) : EyeLook.CENTER;
        EyeLook eyeLook = animationsEnabled && !blinking ? blockFocusEye != EyeLook.CENTER ? blockFocusEye : mountedEyeLook != EyeLook.CENTER ? mountedEyeLook : idleEyeLook(state) : EyeLook.CENTER;
        HumanoidArm spyglassArm = spyglassUseArm(state);
        HumanoidArm bowArm = bowUseArm(state);
        boolean bowSquint = config.animateBowShooting && isBowFullyDrawn(state, bowArm);
        DamageEyeReaction damageReaction = animationsEnabled ? damageReaction(state.id, state.hasRedOverlay, state.ageInTicks) : DamageEyeReaction.NONE;
        boolean fallingSurprise = actionState.fallingSurprise();
        boolean hurtSclera = damageReaction == DamageEyeReaction.SCLERA || fallingSurprise;
        EyeExpression leftEye = eyeExpression(sleeping, animationsEnabled, blinking, spyglassArm == HumanoidArm.LEFT, bowSquint);
        EyeExpression rightEye = eyeExpression(sleeping, animationsEnabled, blinking, spyglassArm == HumanoidArm.RIGHT, bowSquint);
        if (damageReaction == DamageEyeReaction.CLOSED) {
            leftEye = EyeExpression.CLOSED;
            rightEye = EyeExpression.CLOSED;
            eyeLook = EyeLook.CENTER;
        }

        poseStack.pushPose();
        translateToFacePose(poseStack);
        int overlay = config.cleanEyelidColor ? OverlayTexture.NO_OVERLAY : OverlayTexture.pack(0.0F, state.hasRedOverlay);
        int eyelidColor = eyelidColor(config.cleanEyelidColor, eyes.eyeHeight);
        submitEye(poseStack, collector, renderType, light, overlay, eyes.leftEyeX, eyes.leftEyeY, eyes.eyelidColorX, eyes.eyelidColorY, eyes.eyeWidth, eyes.eyeHeight, leftEye, eyeLook, EyeSide.LEFT, hurtSclera, fallingSurprise, eyelidColor);
        submitEye(poseStack, collector, renderType, light, overlay, eyes.rightEyeX, eyes.rightEyeY, eyes.eyelidColorX, eyes.eyelidColorY, eyes.eyeWidth, eyes.eyeHeight, rightEye, eyeLook, EyeSide.RIGHT, hurtSclera, fallingSurprise, eyelidColor);
        if (mouthAnimationsEnabled && AdvancementMouthReaction.active(state.id)) {
            submitAdvancementMouth(poseStack, collector, renderType, light, overlay, eyes);
        } else if (eyes.mouthEnabled || config.showMouth) {
            if (mouthAnimationsEnabled && actionState.mouthUseAnimation() != PlayerActionAnimationState.MouthUseAnimation.NONE) {
                submitUseMouth(poseStack, collector, renderType, light, overlay, eyes, actionState.mouthUseAnimation(), state.ageInTicks, state.id);
            } else {
                submitMouth(poseStack, collector, renderType, light, overlay, eyes);
            }
        }
        poseStack.popPose();
    }

    private void submitMouthOnly(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, RenderType renderType, boolean animationsEnabled, EyeSettings eyes) {
        poseStack.pushPose();
        translateToFacePose(poseStack);
        int overlay = ReactionsClientConfig.get().cleanEyelidColor ? OverlayTexture.NO_OVERLAY : OverlayTexture.pack(0.0F, state.hasRedOverlay);
        if (animationsEnabled && AdvancementMouthReaction.active(state.id)) {
            submitAdvancementMouth(poseStack, collector, renderType, light, overlay, eyes);
        } else {
            PlayerActionAnimationState.Snapshot actionState = PlayerActionAnimationState.snapshot(state.id);
            if (animationsEnabled && actionState.mouthUseAnimation() != PlayerActionAnimationState.MouthUseAnimation.NONE) {
                submitUseMouth(poseStack, collector, renderType, light, overlay, eyes, actionState.mouthUseAnimation(), state.ageInTicks, state.id);
            } else {
                submitMouth(poseStack, collector, renderType, light, overlay, eyes);
            }
        }
        poseStack.popPose();
    }

    private void translateToFacePose(PoseStack poseStack) {
        PlayerModel model = getParentModel();
        if (EMFCompatibility.translateToReplacementHeadFace(model, poseStack)) {
            return;
        }
        if (EMFCompatibility.translateToHeadFace(model, poseStack)) {
            return;
        }

        model.head.translateAndRotate(poseStack);
    }

    private static void submitEye(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, int skinX, int skinY, int eyelidColorX, int eyelidColorY, int eyeWidth, int eyeHeight, EyeExpression expression, EyeLook eyeLook, EyeSide side, boolean hurtSclera, boolean fallingSurprise, int eyelidColor) {
        int clampedSkinX = clamp(skinX, 0, (int) SKIN_SIZE - eyeWidth);
        int clampedSkinY = clamp(skinY, 0, (int) SKIN_SIZE - eyeHeight);
        float dstX1 = skinX - HEAD_FRONT_U - 4.0F;
        float dstY1 = skinY - HEAD_FRONT_V - 8.0F;
        float dstY2 = dstY1 + eyeHeight;
        float dstX2 = dstX1 + eyeWidth;

        if (expression == EyeExpression.CLOSED) {
            submitEyelidTexture(poseStack, collector, renderType, light, overlay, eyelidColorX, eyelidColorY, eyeWidth, eyeHeight, dstX1, dstY1, dstX2, dstY2, eyelidColor);
            return;
        }

        if (expression == EyeExpression.OPEN && shouldExtendSclera(eyeWidth, eyeHeight, fallingSurprise)) {
            submitHurtScleraEye(poseStack, collector, renderType, light, overlay, clampedSkinX, clampedSkinY, eyeWidth, eyeHeight, dstX1, dstY1, dstY2, side, shouldMirrorEyeColumns(eyeLook, side));
            return;
        }

        if (expression == EyeExpression.OPEN && canUseBlockEyeAnimation(eyeWidth, eyeHeight)) {
            submitBlockEye(poseStack, collector, renderType, light, overlay, clampedSkinX, clampedSkinY, eyeWidth, eyeHeight, dstX1, dstY1, side, eyeLook, hurtSclera);
            return;
        }

        if (expression == EyeExpression.SQUINT) {
            submitSquintEye(poseStack, collector, renderType, light, overlay, clampedSkinX, clampedSkinY, eyelidColorX, eyelidColorY, eyeWidth, eyeHeight, dstX1, dstY1, dstY2, eyeLook, side, eyelidColor);
            return;
        }

        if (expression == EyeExpression.OPEN && shouldExtendSclera(eyeWidth, eyeHeight, hurtSclera)) {
            submitHurtScleraEye(poseStack, collector, renderType, light, overlay, clampedSkinX, clampedSkinY, eyeWidth, eyeHeight, dstX1, dstY1, dstY2, side, false);
            return;
        }

        boolean mirrored = shouldMirrorEyeColumns(eyeLook, side);
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
                collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> quad(vertexConsumer, pose, columnDstX1, dstY1, columnDstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR));
            }
            return;
        }

        int sourceX = clampedSkinX;
        int sourceY = clampedSkinY;

        float u1 = sourceX / SKIN_SIZE;
        float v1 = sourceY / SKIN_SIZE;
        float u2 = (sourceX + eyeWidth) / SKIN_SIZE;
        float v2 = (sourceY + eyeHeight) / SKIN_SIZE;

        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> quad(vertexConsumer, pose, dstX1, dstY1, dstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR));
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

    private enum EyeLook {
        LEFT,
        CENTER,
        RIGHT,
        UP,
        DOWN
    }

    private enum DamageEyeReaction {
        NONE,
        SCLERA,
        CLOSED
    }

    private enum EyeSide {
        LEFT,
        RIGHT
    }

    private static final class EMFCompatibility {
        private static final String EMF_MODEL_CLASS = "traben.entity_model_features.models.IEMFModel";
        private static final String EMF_CUSTOM_PART_CLASS = "traben.entity_model_features.models.parts.EMFModelPartCustom";

        private static Method isEmfModelMethod;
        private static Method rootModelMethod;
        private static Method allVanillaPartsMethod;
        private static Field nameField;
        private static Field idField;
        private static Field attachField;
        private static Field partToBeAttachedField;
        private static boolean methodsUnavailable;

        private static ModelPart headPart(Object root, ModelPart fallback) throws ReflectiveOperationException {
            Method partsMethod = allVanillaPartsMethod;
            if (partsMethod == null) {
                partsMethod = root.getClass().getMethod("getAllVanillaPartsEMF");
                allVanillaPartsMethod = partsMethod;
            }
            Object parts = partsMethod.invoke(root);
            if (!(parts instanceof Collection<?> collection)) {
                return fallback;
            }

            for (Object part : collection) {
                if (part instanceof ModelPart modelPart && "head".equals(partName(part))) {
                    return modelPart;
                }
            }
            return fallback;
        }

        private static boolean translateToReplacementHeadFace(PlayerModel model, PoseStack poseStack) {
            try {
                ModelPart rootPart = rootPart(model);
                if (rootPart == null) {
                    return false;
                }

                ModelPart headPart = headPart(rootPart, null);
                if (headPart == null) {
                    return false;
                }

                FaceAnchor faceAnchor = replacementHeadFaceAnchor(headPart);
                if (faceAnchor == null) {
                    return false;
                }

                rootPart.translateAndRotate(poseStack);
                headPart.translateAndRotate(poseStack);
                for (ModelPart part : faceAnchor.path()) {
                    part.translateAndRotate(poseStack);
                }
                faceAnchor.bounds().applyToVanillaFace(poseStack);
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
            return false;
        }

        private static boolean translateToHeadFace(PlayerModel model, PoseStack poseStack) {
            try {
                ModelPart rootPart = rootPart(model);
                if (rootPart == null) {
                    return false;
                }

                ModelPart headPart = headPart(rootPart, null);
                if (headPart == null) {
                    return false;
                }

                rootPart.translateAndRotate(poseStack);
                headPart.translateAndRotate(poseStack);
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
            return false;
        }

        private static ModelPart rootPart(PlayerModel model) throws ReflectiveOperationException {
            Object root = rootModel(model);
            return root instanceof ModelPart modelPart ? modelPart : null;
        }

        private static FaceAnchor replacementHeadFaceAnchor(ModelPart headPart) throws ReflectiveOperationException {
            Map<?, ?> children = ((ModelPartAccessor) (Object) headPart).reactions$children();
            if (children == null || children.isEmpty()) {
                return null;
            }

            FaceAnchor best = null;
            for (Object child : children.values()) {
                if (!(child instanceof ModelPart modelPart) || !isReplacementHeadChild(child)) {
                    continue;
                }

                FaceAnchor anchor = FaceAnchor.find(modelPart);
                if (anchor == null && "head".equals(customPartId(child))) {
                    anchor = FaceAnchor.virtualHead(modelPart);
                }
                best = FaceAnchor.better(best, anchor);
            }
            return best;
        }

        private static boolean isReplacementHeadChild(Object child) throws ReflectiveOperationException {
            Class<?> customPartClass;
            try {
                customPartClass = Class.forName(EMF_CUSTOM_PART_CLASS, false, child.getClass().getClassLoader());
            } catch (ClassNotFoundException ignored) {
                return false;
            }
            if (!customPartClass.isInstance(child)) {
                return false;
            }

            Field attach = attachField;
            if (attach == null) {
                attach = findField(customPartClass, "attach");
                attach.setAccessible(true);
                attachField = attach;
            }
            if (Boolean.TRUE.equals(attach.get(child))) {
                return false;
            }

            Field partToBeAttached = partToBeAttachedField;
            if (partToBeAttached == null) {
                partToBeAttached = findField(customPartClass, "partToBeAttached");
                partToBeAttached.setAccessible(true);
                partToBeAttachedField = partToBeAttached;
            }
            return "head".equals(partToBeAttached.get(child));
        }

        private static String customPartId(Object part) throws ReflectiveOperationException {
            Field field = idField;
            if (field == null) {
                field = findField(part.getClass(), "id");
                field.setAccessible(true);
                idField = field;
            }
            Object value = field.get(part);
            return value instanceof String id ? id : null;
        }

        private static Object rootModel(PlayerModel model) throws ReflectiveOperationException {
            if (methodsUnavailable) {
                return null;
            }

            Class<?> emfModelClass;
            try {
                emfModelClass = Class.forName(EMF_MODEL_CLASS, false, model.getClass().getClassLoader());
            } catch (ClassNotFoundException ignored) {
                methodsUnavailable = true;
                return null;
            }
            if (!emfModelClass.isInstance(model)) {
                return null;
            }

            Method isMethod = isEmfModelMethod;
            if (isMethod == null) {
                isMethod = emfModelClass.getMethod("emf$isEMFModel");
                isEmfModelMethod = isMethod;
            }
            if (!Boolean.TRUE.equals(isMethod.invoke(model))) {
                return null;
            }

            Method rootMethod = rootModelMethod;
            if (rootMethod == null) {
                rootMethod = emfModelClass.getMethod("emf$getEMFRootModel");
                rootModelMethod = rootMethod;
            }
            return rootMethod.invoke(model);
        }

        private static String partName(Object part) throws ReflectiveOperationException {
            Field field = nameField;
            if (field == null) {
                field = findField(part.getClass(), "name");
                field.setAccessible(true);
                nameField = field;
            }
            Object value = field.get(part);
            return value instanceof String name ? name : null;
        }

        private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
            Class<?> current = type;
            while (current != null) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        }

        private record FaceAnchor(List<ModelPart> path, FaceBounds bounds, float score) {
            private static FaceAnchor find(ModelPart root) {
                return find(root, new ArrayList<>(), 0);
            }

            private static FaceAnchor virtualHead(ModelPart root) {
                return new FaceAnchor(List.of(root), FaceBounds.VIRTUAL_HEAD, 100.0F);
            }

            private static FaceAnchor find(ModelPart part, List<ModelPart> path, int depth) {
                path.add(part);
                FaceAnchor best = null;

                List<ModelPart.Cube> cubes = ((ModelPartAccessor) (Object) part).reactions$cubes();
                if (cubes != null) {
                    for (ModelPart.Cube cube : cubes) {
                        FaceBounds bounds = FaceBounds.fromNorthFace(cube);
                        if (bounds != null) {
                            best = better(best, new FaceAnchor(List.copyOf(path), bounds, bounds.score(depth)));
                        }
                    }
                }

                Map<?, ?> children = ((ModelPartAccessor) (Object) part).reactions$children();
                if (children != null) {
                    for (Object child : children.values()) {
                        if (child instanceof ModelPart childPart) {
                            best = better(best, find(childPart, path, depth + 1));
                        }
                    }
                }

                path.remove(path.size() - 1);
                return best;
            }

            private static FaceAnchor better(FaceAnchor current, FaceAnchor candidate) {
                if (candidate == null) {
                    return current;
                }
                return current == null || candidate.score < current.score ? candidate : current;
            }
        }

        private record FaceBounds(float minX, float minY, float minZ, float maxX, float maxY) {
            private static FaceBounds fromNorthFace(ModelPart.Cube cube) {
                float minX = Math.min(cube.minX, cube.maxX);
                float minY = Math.min(cube.minY, cube.maxY);
                float minZ = Math.min(cube.minZ, cube.maxZ);
                float maxX = Math.max(cube.minX, cube.maxX);
                float maxY = Math.max(cube.minY, cube.maxY);
                float width = maxX - minX;
                float height = maxY - minY;
                if (width < 4.0F || height < 4.0F) {
                    return null;
                }
                return new FaceBounds(minX, minY, minZ, maxX, maxY);
            }

            private float score(int depth) {
                float width = maxX - minX;
                float height = maxY - minY;
                float centerX = (minX + maxX) * 0.5F;
                float centerY = (minY + maxY) * 0.5F;
                return Math.abs(width - 8.0F) * 4.0F
                    + Math.abs(height - 8.0F) * 4.0F
                    + Math.abs(centerX) * 1.5F
                    + Math.abs(centerY + 4.0F) * 1.5F
                    + Math.max(0.0F, 6.0F - width) * 8.0F
                    + Math.max(0.0F, 6.0F - height) * 8.0F
                    + depth * 0.25F;
            }

            private void applyToVanillaFace(PoseStack poseStack) {
                float xScale = Math.max((maxX - minX) / 8.0F, 1.0E-4F);
                float yScale = Math.max((maxY - minY) / 8.0F, 1.0E-4F);
                float xOffset = minX + 4.0F * xScale;
                float yOffset = minY + 8.0F * yScale;
                float zOffset = minZ + 4.0F;
                poseStack.translate(xOffset / 16.0F, yOffset / 16.0F, zOffset / 16.0F);
                poseStack.scale(xScale, yScale, 1.0F);
            }

            private static final FaceBounds VIRTUAL_HEAD = new FaceBounds(-4.0F, -32.0F, -4.0F, 4.0F, -24.0F);
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

    private static void pupilQuad(VertexConsumer consumer, PoseStack.Pose pose, float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int light, int overlay, int color) {
        pupilVertex(consumer, pose, x1, y2, u1, v2, light, overlay, color);
        pupilVertex(consumer, pose, x2, y2, u2, v2, light, overlay, color);
        pupilVertex(consumer, pose, x2, y1, u2, v1, light, overlay, color);
        pupilVertex(consumer, pose, x1, y1, u1, v1, light, overlay, color);
    }

    private static void pupilVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int light, int overlay, int color) {
        consumer.addVertex(pose, x / 16.0F, y / 16.0F, PUPIL_FACE_Z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, 0.0F, 0.0F, -1.0F);
    }

    private static void submitMouth(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, EyeSettings eyes) {
        float dstX1 = eyes.leftMouthX - HEAD_FRONT_U - 4.0F;
        float dstY1 = eyes.leftMouthY - HEAD_FRONT_V - 8.0F;
        submitMouthPixel(poseStack, collector, renderType, light, overlay, eyes.leftMouthX, eyes.leftMouthY, dstX1, dstY1, dstX1 + 1.0F, dstY1 + 1.0F);
        submitMouthPixel(poseStack, collector, renderType, light, overlay, eyes.rightMouthX, eyes.rightMouthY, dstX1 + 1.0F, dstY1, dstX1 + 2.0F, dstY1 + 1.0F);
    }

    private static void submitAdvancementMouth(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, EyeSettings eyes) {
        float coverX = eyes.leftMouthX - HEAD_FRONT_U - 4.0F;
        float coverY = eyes.leftMouthY - HEAD_FRONT_V - 8.0F;
        submitMouthCover(poseStack, collector, renderType, light, overlay, eyes, coverX, coverY, 2.0F, 1.0F);

        float centerX = ((eyes.leftMouthX + 0.5F) + (eyes.rightMouthX + 0.5F)) * 0.5F;
        float centerY = ((eyes.leftMouthY + 0.5F) + (eyes.rightMouthY + 0.5F)) * 0.5F;
        float width = 1.25F;
        float height = 1.25F;
        float dstX1 = centerX - HEAD_FRONT_U - 4.0F - width * 0.5F;
        float dstY1 = centerY - HEAD_FRONT_V - 8.0F - 0.5F - ADVANCEMENT_MOUTH_TOP_EXTENSION;
        float splitX = dstX1 + width * 0.5F;
        submitMouthPixel(poseStack, collector, renderType, light, overlay, eyes.leftMouthX, eyes.leftMouthY, 1.25F, dstX1, dstY1, splitX, dstY1 + height + ADVANCEMENT_MOUTH_TOP_EXTENSION);
        submitMouthPixel(poseStack, collector, renderType, light, overlay, eyes.rightMouthX, eyes.rightMouthY, 1.25F, splitX, dstY1, dstX1 + width, dstY1 + height + ADVANCEMENT_MOUTH_TOP_EXTENSION);
    }

    private static void submitUseMouth(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, EyeSettings eyes, PlayerActionAnimationState.MouthUseAnimation animation, float ageInTicks, int entityId) {
        float coverX = eyes.leftMouthX - HEAD_FRONT_U - 4.0F;
        float coverY = eyes.leftMouthY - HEAD_FRONT_V - 8.0F;
        submitMouthCover(poseStack, collector, renderType, light, overlay, eyes, coverX, coverY, 2.0F, 1.0F);

        float offsetTicks = ageInTicks + seededOffset(entityId, 41, 6);
        float wave = 0.5F + 0.5F * (float) Math.sin(offsetTicks * Math.PI * 0.35D);
        float width = animation == PlayerActionAnimationState.MouthUseAnimation.DRINKING ? 1.15F + wave * 0.15F : 1.45F + wave * 0.35F;
        float height = animation == PlayerActionAnimationState.MouthUseAnimation.DRINKING ? 1.15F + wave * 0.25F : 1.0F + wave * 0.45F;
        float centerX = ((eyes.leftMouthX + 0.5F) + (eyes.rightMouthX + 0.5F)) * 0.5F;
        float dstX1 = centerX - HEAD_FRONT_U - 4.0F - width * 0.5F;
        float dstY1 = coverY + 0.05F;
        float splitX = dstX1 + width * 0.5F;
        submitMouthPixel(poseStack, collector, renderType, light, overlay, eyes.leftMouthX, eyes.leftMouthY, 1.0F, dstX1, dstY1, splitX, dstY1 + height);
        submitMouthPixel(poseStack, collector, renderType, light, overlay, eyes.rightMouthX, eyes.rightMouthY, 1.0F, splitX, dstY1, dstX1 + width, dstY1 + height);
    }

    private static void submitMouthCover(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, EyeSettings eyes, float dstX1, float dstY1, float width, float height) {
        int sourceX = clamp(eyes.leftMouthX - 1, 0, (int) SKIN_SIZE - 1);
        int sourceY = clamp(eyes.leftMouthY, 0, (int) SKIN_SIZE - 1);
        float u1 = (sourceX + MOUTH_UV_INSET) / SKIN_SIZE;
        float v1 = (sourceY + MOUTH_UV_INSET) / SKIN_SIZE;
        float u2 = (sourceX + 1.0F - MOUTH_UV_INSET) / SKIN_SIZE;
        float v2 = (sourceY + 1.0F - MOUTH_UV_INSET) / SKIN_SIZE;
        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> mouthCoverQuad(vertexConsumer, pose, dstX1, dstY1, dstX1 + width, dstY1 + height, u1, v1, u2, v2, light, overlay, NORMAL_COLOR));
    }

    private static void submitMouthPixel(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, int skinX, int skinY, float dstX1, float dstY1, float dstX2, float dstY2) {
        submitMouthPixel(poseStack, collector, renderType, light, overlay, skinX, skinY, 1.0F, dstX1, dstY1, dstX2, dstY2);
    }

    private static void submitMouthPixel(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, int skinX, int skinY, float sourceHeight, float dstX1, float dstY1, float dstX2, float dstY2) {
        int sourceX = clamp(skinX, 0, (int) SKIN_SIZE - 1);
        int sourceY = clamp(skinY, 0, (int) SKIN_SIZE - 1);
        float clampedSourceHeight = Math.min(sourceHeight, SKIN_SIZE - sourceY);
        float u1 = (sourceX + MOUTH_UV_INSET) / SKIN_SIZE;
        float v1 = (sourceY + MOUTH_UV_INSET) / SKIN_SIZE;
        float u2 = (sourceX + 1.0F - MOUTH_UV_INSET) / SKIN_SIZE;
        float v2 = (sourceY + clampedSourceHeight - MOUTH_UV_INSET) / SKIN_SIZE;
        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> mouthQuad(vertexConsumer, pose, dstX1, dstY1, dstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR));
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

    private static boolean canUseBlockEyeAnimation(int eyeWidth, int eyeHeight) {
        return eyeWidth == 2 && eyeHeight >= 1 && eyeHeight <= 3 || eyeWidth == 3 && eyeHeight >= 1 && eyeHeight <= 2;
    }

    private static void submitBlockEye(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, int skinX, int skinY, int eyeWidth, int eyeHeight, float dstX1, float dstY1, EyeSide side, EyeLook eyeLook, boolean hurtSclera) {
        float eyeDstY1 = dstY1;
        float eyeDstY2 = dstY1 + eyeHeight;

        if (hurtSclera) {
            int externalColumn = externalScleraColumn(side, eyeWidth);
            submitEyePiece(poseStack, collector, renderType, light, overlay, skinX + externalColumn, skinY, 1.0F, eyeHeight, dstX1, eyeDstY1 - HURT_SCLERA_EXTENSION, dstX1 + eyeWidth, eyeDstY1, NORMAL_COLOR);
        }

        submitBlockEyeRow(poseStack, collector, renderType, light, overlay, skinX, skinY, eyeWidth, eyeHeight, eyeHeight, dstX1, eyeDstY1, eyeDstY2, side, eyeLook);
    }

    private static void submitBlockEyeRow(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, int skinX, float sourceY, int eyeWidth, int eyeHeight, float sourceHeight, float dstX1, float dstY1, float dstY2, EyeSide side, EyeLook eyeLook) {
        int scleraSourceColumn = externalScleraColumn(side, eyeWidth);
        for (int column = 0; column < eyeWidth; column++) {
            submitEyePiece(poseStack, collector, renderType, light, overlay, skinX + scleraSourceColumn, sourceY, 1.0F, sourceHeight, dstX1 + column, dstY1, dstX1 + column + 1.0F, dstY2, NORMAL_COLOR);
        }

        int pupilWidth = pupilWidth(eyeWidth, eyeHeight);
        int pupilColumn = pupilDestinationColumn(side, eyeLook, eyeWidth, eyeHeight, pupilWidth);
        int pupilSourceColumn = internalPupilColumn(side, eyeWidth, eyeHeight, pupilWidth);
        float rowHeight = dstY2 - dstY1;
        if (eyeLook == EyeLook.UP || eyeLook == EyeLook.DOWN) {
            float verticalSclera = rowHeight * LOOK_DOWN_SCLERA_BOTTOM_COVERAGE;
            float visibleRowHeight = rowHeight - verticalSclera;
            float visibleSourceHeight = sourceHeight * visibleRowHeight / rowHeight;
            float pupilSourceY = eyeLook == EyeLook.UP ? sourceY + sourceHeight - visibleSourceHeight : sourceY;
            float pupilDstY1 = eyeLook == EyeLook.DOWN ? dstY1 + verticalSclera : dstY1;
            float pupilDstY2 = pupilDstY1 + visibleRowHeight;
            submitPupilPiece(poseStack, collector, renderType, light, overlay, skinX + pupilSourceColumn, pupilSourceY, pupilWidth, visibleSourceHeight, dstX1 + pupilColumn, pupilDstY1, dstX1 + pupilColumn + pupilWidth, pupilDstY2, NORMAL_COLOR);
            return;
        }

        submitPupilPiece(poseStack, collector, renderType, light, overlay, skinX + pupilSourceColumn, sourceY, pupilWidth, sourceHeight, dstX1 + pupilColumn, dstY1, dstX1 + pupilColumn + pupilWidth, dstY2, NORMAL_COLOR);
    }

    private static void submitEyePiece(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, float sourceX, float sourceY, float sourceWidth, float sourceHeight, float dstX1, float dstY1, float dstX2, float dstY2, int color) {
        float insetX = Math.min(EYE_UV_INSET, sourceWidth * 0.25F);
        float insetY = Math.min(EYE_UV_INSET, sourceHeight * 0.25F);
        float u1 = (sourceX + insetX) / SKIN_SIZE;
        float v1 = (sourceY + insetY) / SKIN_SIZE;
        float u2 = (sourceX + sourceWidth - insetX) / SKIN_SIZE;
        float v2 = (sourceY + sourceHeight - insetY) / SKIN_SIZE;
        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> quad(vertexConsumer, pose, dstX1, dstY1, dstX2, dstY2, u1, v1, u2, v2, light, overlay, color));
    }

    private static void submitPupilPiece(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, float sourceX, float sourceY, float sourceWidth, float sourceHeight, float dstX1, float dstY1, float dstX2, float dstY2, int color) {
        float insetX = Math.min(EYE_UV_INSET, sourceWidth * 0.25F);
        float insetY = Math.min(EYE_UV_INSET, sourceHeight * 0.25F);
        float u1 = (sourceX + insetX) / SKIN_SIZE;
        float v1 = (sourceY + insetY) / SKIN_SIZE;
        float u2 = (sourceX + sourceWidth - insetX) / SKIN_SIZE;
        float v2 = (sourceY + sourceHeight - insetY) / SKIN_SIZE;
        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> pupilQuad(vertexConsumer, pose, dstX1, dstY1, dstX2, dstY2, u1, v1, u2, v2, light, overlay, color));
    }

    private static void submitEyelidTexture(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, int eyelidX, int eyelidY, int tileColumns, int tileRows, float dstX1, float dstY1, float dstX2, float dstY2, int color) {
        int sourceX = clamp(eyelidX, 0, (int) SKIN_SIZE - 1);
        int sourceY = clamp(eyelidY, 0, (int) SKIN_SIZE - 1);
        int columns = Math.max(1, tileColumns);
        int rows = Math.max(1, tileRows);
        if (rows == 1) {
            submitStretchedEyelidPixel(poseStack, collector, renderType, light, overlay, sourceX, sourceY, dstX1, dstY1, dstX2, dstY2, color);
            return;
        }

        float tileWidth = (dstX2 - dstX1) / columns;
        float tileHeight = (dstY2 - dstY1) / rows;

        for (int row = 0; row < rows; row++) {
            float tileDstY1 = dstY1 + row * tileHeight;
            float tileDstY2 = row == rows - 1 ? dstY2 : tileDstY1 + tileHeight;
            for (int column = 0; column < columns; column++) {
                int tileColor = eyelidTileColor(color, row, rows, column, columns);
                float tileDstX1 = dstX1 + column * tileWidth;
                float tileDstX2 = column == columns - 1 ? dstX2 : tileDstX1 + tileWidth;
                submitEyePiece(poseStack, collector, renderType, light, overlay, sourceX, sourceY, 1.0F, 1.0F, tileDstX1, tileDstY1, tileDstX2, tileDstY2, tileColor);
            }
        }
    }

    private static void submitStretchedEyelidPixel(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, int sourceX, int sourceY, float dstX1, float dstY1, float dstX2, float dstY2, int color) {
        float u1 = sourceX / SKIN_SIZE;
        float v1 = sourceY / SKIN_SIZE;
        float u2 = (sourceX + 1) / SKIN_SIZE;
        float v2 = (sourceY + 1) / SKIN_SIZE;
        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> quad(vertexConsumer, pose, dstX1, dstY1, dstX2, dstY2, u1, v1, u2, v2, light, overlay, color));
    }

    private static int eyelidColor(boolean cleanEyelidColor, int eyeHeight) {
        if (cleanEyelidColor) {
            return NORMAL_COLOR;
        }
        return eyeHeight > 1 ? LARGE_EYELID_DARKEN_COLOR : EYELID_DARKEN_COLOR;
    }

    private static int eyelidTileColor(int color, int row, int rows, int column, int columns) {
        if (color == NORMAL_COLOR) {
            return color;
        }
        float rowFactor = 1.0F;
        if (rows > 1) {
            float rowProgress = row / (float) (rows - 1);
            rowFactor = EYELID_TOP_SUBTLE_DARK_FACTOR + (1.0F - EYELID_TOP_SUBTLE_DARK_FACTOR) * rowProgress;
        }
        float columnFactor = 1.0F;
        if (columns > 1) {
            float columnProgress = column / (float) (columns - 1);
            columnFactor += (0.5F - Math.abs(columnProgress - 0.5F)) * EYELID_COLUMN_SHADE_RANGE;
            columnFactor -= columnProgress * EYELID_COLUMN_SHADE_RANGE * 0.5F;
        }
        return multiplyColor(color, rowFactor * columnFactor);
    }

    private static int multiplyColor(int color, float factor) {
        int alpha = color & 0xFF000000;
        int red = clamp(Math.round(((color >>> 16) & 0xFF) * factor), 0, 255);
        int green = clamp(Math.round(((color >>> 8) & 0xFF) * factor), 0, 255);
        int blue = clamp(Math.round((color & 0xFF) * factor), 0, 255);
        return alpha | red << 16 | green << 8 | blue;
    }

    private static int pupilWidth(int eyeWidth, int eyeHeight) {
        if (eyeWidth == 3 && eyeHeight == 1) {
            return 1;
        }
        return Math.max(1, eyeWidth - 1);
    }

    private static int internalPupilColumn(EyeSide side, int eyeWidth, int eyeHeight, int pupilWidth) {
        if (eyeWidth == 3 && eyeHeight == 1) {
            return 1;
        }
        return side == EyeSide.LEFT ? eyeWidth - pupilWidth : 0;
    }

    private static int externalScleraColumn(EyeSide side, int eyeWidth) {
        return side == EyeSide.LEFT ? 0 : eyeWidth - 1;
    }

    private static int pupilDestinationColumn(EyeSide side, EyeLook eyeLook, int eyeWidth, int eyeHeight, int pupilWidth) {
        if (eyeLook == EyeLook.LEFT) {
            return 0;
        }
        if (eyeLook == EyeLook.RIGHT) {
            return eyeWidth - pupilWidth;
        }
        return internalPupilColumn(side, eyeWidth, eyeHeight, pupilWidth);
    }

    private static boolean shouldMirrorEyeColumns(EyeLook eyeLook, EyeSide side) {
        return eyeLook == EyeLook.LEFT && side == EyeSide.LEFT || eyeLook == EyeLook.RIGHT && side == EyeSide.RIGHT;
    }

    private static void submitSquintEye(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, int skinX, int skinY, int eyelidX, int eyelidY, int eyeWidth, int eyeHeight, float dstX1, float dstY1, float dstY2, EyeLook eyeLook, EyeSide side, int eyelidColor) {
        float visibleHeight = Math.max(0.333F, (dstY2 - dstY1) * SQUINT_VISIBLE_EYE_COVERAGE);
        float splitY = Math.max(dstY1, dstY2 - visibleHeight);
        float dstX2 = dstX1 + eyeWidth;
        int eyelidSourceHeight = Math.max(1, Math.round(splitY - dstY1));
        submitEyelidTexture(poseStack, collector, renderType, light, overlay, eyelidX, eyelidY, eyeWidth, eyelidSourceHeight, dstX1, dstY1, dstX2, splitY, eyelidColor);

        float sourceVisibleHeight = eyeHeight * SQUINT_VISIBLE_EYE_COVERAGE;
        float sourceY1 = skinY + eyeHeight - sourceVisibleHeight;
        if (canUseBlockEyeAnimation(eyeWidth, eyeHeight)) {
            submitBlockEyeRow(poseStack, collector, renderType, light, overlay, skinX, sourceY1, eyeWidth, eyeHeight, sourceVisibleHeight, dstX1, splitY, dstY2, side, eyeLook);
            return;
        }

        boolean mirrored = shouldMirrorEyeColumns(eyeLook, side);
        if (mirrored) {
            for (int column = 0; column < eyeWidth; column++) {
                int sourceX = skinX + eyeWidth - 1 - column;
                float columnDstX1 = dstX1 + column;
                float columnDstX2 = columnDstX1 + 1.0F;
                float u1 = sourceX / SKIN_SIZE;
                float v1 = sourceY1 / SKIN_SIZE;
                float u2 = (sourceX + 1) / SKIN_SIZE;
                float v2 = (skinY + eyeHeight) / SKIN_SIZE;
                collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> quad(vertexConsumer, pose, columnDstX1, splitY, columnDstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR));
            }
            return;
        }

        float u1 = skinX / SKIN_SIZE;
        float v1 = sourceY1 / SKIN_SIZE;
        float u2 = (skinX + eyeWidth) / SKIN_SIZE;
        float v2 = (skinY + eyeHeight) / SKIN_SIZE;
        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> quad(vertexConsumer, pose, dstX1, splitY, dstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR));
    }

    private static boolean shouldExtendSclera(int eyeWidth, int eyeHeight, boolean hurtSclera) {
        return hurtSclera && eyeWidth == 2 && (eyeHeight == 1 || eyeHeight == 2);
    }

    private static void submitHurtScleraEye(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, int skinX, int skinY, int eyeWidth, int eyeHeight, float dstX1, float dstY1, float dstY2, EyeSide side, boolean mirrored) {
        float extendedDstY1 = dstY1 - HURT_SCLERA_EXTENSION;
        int externalColumn = side == EyeSide.LEFT ? 0 : eyeWidth - 1;
        if (eyeWidth == 2 && eyeHeight == 2) {
            submitColumnScleraExtension(poseStack, collector, renderType, light, overlay, skinX, skinY, eyeWidth, dstX1, extendedDstY1, dstY1, mirrored);
        } else {
            int externalSourceX = mirrored ? skinX + eyeWidth - 1 - externalColumn : skinX + externalColumn;
            float externalU1 = externalSourceX / SKIN_SIZE;
            float externalV1 = skinY / SKIN_SIZE;
            float externalU2 = (externalSourceX + 1) / SKIN_SIZE;
            float externalV2 = (skinY + eyeHeight) / SKIN_SIZE;
            collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> quad(vertexConsumer, pose, dstX1, extendedDstY1, dstX1 + eyeWidth, dstY1, externalU1, externalV1, externalU2, externalV2, light, overlay, NORMAL_COLOR));
        }

        for (int column = 0; column < eyeWidth; column++) {
            if (column != externalColumn) {
                continue;
            }

            int sourceX = mirrored ? skinX + eyeWidth - 1 - column : skinX + column;
            float columnDstX1 = dstX1 + column;
            float columnDstX2 = columnDstX1 + 1.0F;
            float u1 = sourceX / SKIN_SIZE;
            float v1 = skinY / SKIN_SIZE;
            float u2 = (sourceX + 1) / SKIN_SIZE;
            float v2 = (skinY + eyeHeight) / SKIN_SIZE;
            float finalDstX1 = columnDstX1;
            float finalDstX2 = columnDstX2;
            float overlayDstY1 = eyeWidth == 2 && eyeHeight == 2 ? dstY1 : extendedDstY1;
            collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> quad(vertexConsumer, pose, finalDstX1, overlayDstY1, finalDstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR));
        }
    }

    private static void submitColumnScleraExtension(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, int overlay, int skinX, int skinY, int eyeWidth, float dstX1, float extendedDstY1, float dstY1, boolean mirrored) {
        for (int column = 0; column < eyeWidth; column++) {
            int sourceX = mirrored ? skinX + eyeWidth - 1 - column : skinX + column;
            float columnDstX1 = dstX1 + column;
            float columnDstX2 = columnDstX1 + 1.0F;
            float u1 = sourceX / SKIN_SIZE;
            float v1 = skinY / SKIN_SIZE;
            float u2 = (sourceX + 1) / SKIN_SIZE;
            float v2 = (skinY + 1) / SKIN_SIZE;
            collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> quad(vertexConsumer, pose, columnDstX1, extendedDstY1, columnDstX2, dstY1, u1, v1, u2, v2, light, overlay, NORMAL_COLOR));
        }
    }

    private static DamageEyeReaction damageReaction(int entityId, boolean hurt, float ageInTicks) {
        if (!hurt) {
            DAMAGE_REACTIONS.remove(entityId);
            return DamageEyeReaction.NONE;
        }

        return DAMAGE_REACTIONS.computeIfAbsent(entityId, id -> chooseDamageReaction(id, ageInTicks));
    }

    private static DamageEyeReaction chooseDamageReaction(int entityId, float ageInTicks) {
        DamageEyeReaction reaction = randomDamageReaction(entityId, ageInTicks);
        DamageEyeReaction lastReaction = LAST_DAMAGE_REACTIONS.get(entityId);
        int streak = DAMAGE_REACTION_STREAKS.getOrDefault(entityId, 0);
        if (reaction != DamageEyeReaction.NONE && reaction == lastReaction && streak >= 2) {
            reaction = oppositeDamageReaction(reaction);
        }

        if (reaction == DamageEyeReaction.NONE) {
            DAMAGE_REACTION_STREAKS.put(entityId, 0);
        } else if (reaction == lastReaction) {
            DAMAGE_REACTION_STREAKS.put(entityId, streak + 1);
        } else {
            DAMAGE_REACTION_STREAKS.put(entityId, 1);
        }
        LAST_DAMAGE_REACTIONS.put(entityId, reaction);
        return reaction;
    }

    private static DamageEyeReaction randomDamageReaction(int entityId, float ageInTicks) {
        int roll = seededOffset(entityId, (int) ageInTicks + 97, 100);
        if (roll < 48) {
            return DamageEyeReaction.CLOSED;
        }
        if (roll < 96) {
            return DamageEyeReaction.SCLERA;
        }
        return DamageEyeReaction.NONE;
    }

    private static DamageEyeReaction oppositeDamageReaction(DamageEyeReaction reaction) {
        return reaction == DamageEyeReaction.CLOSED ? DamageEyeReaction.SCLERA : DamageEyeReaction.CLOSED;
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

    private static boolean isBlinking(AvatarRenderState state, ReactionsClientConfig config) {
        int baseInterval = Math.max(20, config.blinkIntervalTicks);
        int randomWindow = Math.max(20, baseInterval / 2);
        int blinkIndex = Math.max(0, (int) state.ageInTicks / baseInterval);
        int interval = baseInterval + seededOffset(state.id, blinkIndex, randomWindow);
        int phase = Math.floorMod((int) state.ageInTicks + seededOffset(state.id, blinkIndex + 31, randomWindow), interval);
        return phase < config.blinkDurationTicks;
    }

    private static HumanoidArm spyglassUseArm(AvatarRenderState state) {
        if (!state.isUsingItem || state.useItemHand == null || state.mainArm == null) {
            return null;
        }

        HumanoidArm useArm = state.useItemHand == InteractionHand.MAIN_HAND ? state.mainArm : state.mainArm.getOpposite();
        ItemStack useStack = state.getUseItemStackForArm(useArm);
        if (useStack.is(Items.SPYGLASS)) {
            return useArm;
        }

        if (state.rightHandItemStack.is(Items.SPYGLASS) && state.ticksUsingItem(HumanoidArm.RIGHT) > 0.0F) {
            return HumanoidArm.RIGHT;
        }
        if (state.leftHandItemStack.is(Items.SPYGLASS) && state.ticksUsingItem(HumanoidArm.LEFT) > 0.0F) {
            return HumanoidArm.LEFT;
        }
        return null;
    }

    private static HumanoidArm bowUseArm(AvatarRenderState state) {
        if (!state.isUsingItem || state.useItemHand == null || state.mainArm == null) {
            return null;
        }

        HumanoidArm useArm = state.useItemHand == InteractionHand.MAIN_HAND ? state.mainArm : state.mainArm.getOpposite();
        ItemStack useStack = state.getUseItemStackForArm(useArm);
        if (useStack.is(Items.BOW)) {
            return useArm;
        }

        if (state.rightHandItemStack.is(Items.BOW) && state.ticksUsingItem(HumanoidArm.RIGHT) > 0.0F) {
            return HumanoidArm.RIGHT;
        }
        if (state.leftHandItemStack.is(Items.BOW) && state.ticksUsingItem(HumanoidArm.LEFT) > 0.0F) {
            return HumanoidArm.LEFT;
        }
        return null;
    }

    private static boolean isBowFullyDrawn(AvatarRenderState state, HumanoidArm bowArm) {
        return bowArm != null && state.ticksUsingItem(bowArm) >= BOW_FULL_CHARGE_TICKS;
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

    private static EyeLook idleEyeLook(AvatarRenderState state) {
        if (state.walkAnimationSpeed > 0.01F) {
            IDLE_STARTED_AT.remove(state.id);
            return EyeLook.CENTER;
        }

        float idleStartedAt = IDLE_STARTED_AT.computeIfAbsent(state.id, id -> state.ageInTicks);
        int idleTicks = (int) (state.ageInTicks - idleStartedAt);
        int cycleTick = Math.floorMod(idleTicks, IDLE_LOOK_CYCLE_TICKS);
        if (cycleTick < IDLE_LOOK_DELAY_TICKS) {
            return EyeLook.CENTER;
        }

        int phase = cycleTick - IDLE_LOOK_DELAY_TICKS;
        if (phase < IDLE_LOOK_STEP_TICKS) {
            return EyeLook.LEFT;
        }
        if (phase < IDLE_LOOK_STEP_TICKS * 2) {
            return EyeLook.RIGHT;
        }
        return EyeLook.CENTER;
    }

    private static EyeLook mountedBackLook(PlayerActionAnimationState.Snapshot actionState) {
        float yawDelta = actionState.mountedYawDelta();
        if (yawDelta <= -MOUNTED_BACK_LOOK_THRESHOLD) {
            return EyeLook.RIGHT;
        }
        if (yawDelta >= MOUNTED_BACK_LOOK_THRESHOLD) {
            return EyeLook.LEFT;
        }
        return EyeLook.CENTER;
    }

    private static EyeLook blockFocusEye(int entityId, boolean isSelf) {
        int directFocus = isSelf ? BlockInteractionEyeFocus.localDirectFocusSignal() : ReactionsNetworking.remoteEyeFocus(entityId);
        if (directFocus == DIRECT_BLOCK_FOCUS_DOWN_SIGNAL) {
            return EyeLook.DOWN;
        }
        if (directFocus == DIRECT_BLOCK_FOCUS_UP_SIGNAL) {
            return EyeLook.UP;
        }

        float focus = isSelf ? BlockInteractionEyeFocus.localFocusAmount() : directFocus / 100.0F;
        if (focus <= -BLOCK_FOCUS_EYE_THRESHOLD) {
            return EyeLook.LEFT;
        }
        if (focus >= BLOCK_FOCUS_EYE_THRESHOLD) {
            return EyeLook.RIGHT;
        }
        return EyeLook.CENTER;
    }

    private static RenderType renderType(Identifier texture) {
        return RenderTypes.entityCutout(texture);
    }

    private static String playerName(AvatarRenderState state) {
        return state.nameTag == null ? null : state.nameTag.getString();
    }

    private static boolean isDefaultPlayerSkin(Identifier texture) {
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
