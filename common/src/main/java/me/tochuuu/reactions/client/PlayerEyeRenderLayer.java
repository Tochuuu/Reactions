package me.tochuuu.reactions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.tochuuu.reactions.mixin.ModelPartAccessor;
import me.tochuuu.reactions.network.ReactionsNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

public final class PlayerEyeRenderLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
    private static final float SKIN_SIZE = 64.0F;
    private static final float HEAD_FRONT_U = 8.0F;
    private static final float HEAD_FRONT_V = 8.0F;
    private static final float HEAD_FACE_Z = -4.015F / 16.0F;
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
    private static final float HURT_SCLERA_EXTENSION = 0.5F;
    private static final float BLOCK_FOCUS_EYE_THRESHOLD = 0.25F;
    private static final EyeSettings DEFAULT_EYES = new EyeSettings(9, 12, 13, 12, false, 11, 14, 12, 14, 10, 11, 2, 1);
    private static final java.util.Map<Integer, Float> IDLE_STARTED_AT = new java.util.HashMap<>();
    private static final java.util.Map<Integer, DamageEyeReaction> DAMAGE_REACTIONS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, DamageEyeReaction> LAST_DAMAGE_REACTIONS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Integer> DAMAGE_REACTION_STREAKS = new java.util.HashMap<>();

    public PlayerEyeRenderLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int light, PlayerRenderState state, float limbSwing, float limbSwingAmount) {
        ReactionsClientConfig config = ReactionsClientConfig.get();
        if (!config.enabled || state.skin == null || state.isInvisible) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean isSelf = minecraft.player != null && minecraft.player.getId() == state.id;
        RemoteEyeConfig remoteConfig = isSelf ? null : ReactionsNetworking.remoteConfig(state.id);
        ResourceLocation texture = state.skin.texture();
        boolean canSyncWithServer = ReactionsNetworking.canSyncWithServer();
        ReactionsClientConfig.PlayerOverride playerOverride = !isSelf && remoteConfig == null && !canSyncWithServer
            ? config.playerOverride(playerName(state))
            : null;
        boolean useDefaultOfflineEyes = !isSelf && remoteConfig == null && playerOverride == null && !canSyncWithServer && isDefaultPlayerSkin(texture);
        if (!isSelf && remoteConfig == null && !useDefaultOfflineEyes && (playerOverride == null || !playerOverride.enabled)) {
            if (config.showMouth) {
                renderMouthOnly(poseStack, bufferSource, light, state, renderType(texture), config.animateOthers, EyeSettings.local(config));
            }
            return;
        }
        EyeSettings eyes = isSelf ? EyeSettings.local(config) : remoteConfig != null ? EyeSettings.remote(remoteConfig) : useDefaultOfflineEyes ? EyeSettings.defaults() : EyeSettings.override(playerOverride);

        RenderType renderType = renderType(texture);
        boolean animationsEnabled = isSelf ? config.animateSelf : config.animateOthers;
        boolean sleeping = state.hasPose(Pose.SLEEPING);
        PlayerActionAnimationState.Snapshot actionState = PlayerActionAnimationState.snapshot(state.id);
        boolean blinking = !sleeping && animationsEnabled && (isBlinking(state, config) || actionState.landingBlink());
        int blockFocusEye = animationsEnabled && !blinking ? blockFocusEye(state.id, isSelf) : 0;
        int mirroredEye = animationsEnabled && !blinking ? blockFocusEye != 0 ? blockFocusEye : mirroredIdleEye(state) : 0;
        HumanoidArm spyglassArm = spyglassUseArm(state);
        HumanoidArm bowArm = bowUseArm(state);
        boolean bowSquint = config.animateBowShooting && isBowFullyDrawn(state, bowArm);
        DamageEyeReaction damageReaction = animationsEnabled ? damageReaction(state.id, state.hasRedOverlay, state.ageInTicks) : DamageEyeReaction.NONE;
        boolean hurtSclera = damageReaction == DamageEyeReaction.SCLERA || actionState.fallingSurprise();
        EyeExpression leftEye = eyeExpression(sleeping, animationsEnabled, blinking, spyglassArm == HumanoidArm.LEFT, bowSquint);
        EyeExpression rightEye = eyeExpression(sleeping, animationsEnabled, blinking, spyglassArm == HumanoidArm.RIGHT, bowSquint);
        if (damageReaction == DamageEyeReaction.CLOSED) {
            leftEye = EyeExpression.CLOSED;
            rightEye = EyeExpression.CLOSED;
            mirroredEye = 0;
        }

        poseStack.pushPose();
        translateToFacePose(poseStack);
        int overlay = OverlayTexture.pack(0.0F, state.hasRedOverlay);
        submitEye(poseStack, bufferSource, renderType, light, overlay, eyes.leftEyeX, eyes.leftEyeY, eyes.eyelidColorX, eyes.eyelidColorY, eyes.eyeWidth, eyes.eyeHeight, leftEye, mirroredEye == -1, EyeSide.LEFT, hurtSclera);
        submitEye(poseStack, bufferSource, renderType, light, overlay, eyes.rightEyeX, eyes.rightEyeY, eyes.eyelidColorX, eyes.eyelidColorY, eyes.eyeWidth, eyes.eyeHeight, rightEye, mirroredEye == 1, EyeSide.RIGHT, hurtSclera);
        if (animationsEnabled && AdvancementMouthReaction.active(state.id)) {
            submitAdvancementMouth(poseStack, bufferSource, renderType, light, overlay, eyes);
        } else if (eyes.mouthEnabled || config.showMouth) {
            if (animationsEnabled && actionState.mouthUseAnimation() != PlayerActionAnimationState.MouthUseAnimation.NONE) {
                submitUseMouth(poseStack, bufferSource, renderType, light, overlay, eyes, actionState.mouthUseAnimation(), state.ageInTicks, state.id);
            } else {
                submitMouth(poseStack, bufferSource, renderType, light, overlay, eyes);
            }
        }
        poseStack.popPose();
    }

    private void renderMouthOnly(PoseStack poseStack, MultiBufferSource bufferSource, int light, PlayerRenderState state, RenderType renderType, boolean animationsEnabled, EyeSettings eyes) {
        poseStack.pushPose();
        translateToFacePose(poseStack);
        int overlay = OverlayTexture.pack(0.0F, state.hasRedOverlay);
        if (animationsEnabled && AdvancementMouthReaction.active(state.id)) {
            submitAdvancementMouth(poseStack, bufferSource, renderType, light, overlay, eyes);
        } else {
            PlayerActionAnimationState.Snapshot actionState = PlayerActionAnimationState.snapshot(state.id);
            if (animationsEnabled && actionState.mouthUseAnimation() != PlayerActionAnimationState.MouthUseAnimation.NONE) {
                submitUseMouth(poseStack, bufferSource, renderType, light, overlay, eyes, actionState.mouthUseAnimation(), state.ageInTicks, state.id);
            } else {
                submitMouth(poseStack, bufferSource, renderType, light, overlay, eyes);
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

    private static void submitEye(PoseStack poseStack, MultiBufferSource bufferSource, RenderType renderType, int light, int overlay, int skinX, int skinY, int eyelidColorX, int eyelidColorY, int eyeWidth, int eyeHeight, EyeExpression expression, boolean mirrored, EyeSide side, boolean hurtSclera) {
        int clampedSkinX = clamp(skinX, 0, (int) SKIN_SIZE - eyeWidth);
        int clampedSkinY = clamp(skinY, 0, (int) SKIN_SIZE - eyeHeight);
        int clampedEyelidSkinX = clamp(eyelidColorX, 0, (int) SKIN_SIZE - 1);
        int clampedEyelidSkinY = clamp(eyelidColorY, 0, (int) SKIN_SIZE - 1);
        float dstX1 = skinX - HEAD_FRONT_U - 4.0F;
        float dstY1 = skinY - HEAD_FRONT_V - 8.0F;
        float dstY2 = dstY1 + eyeHeight;

        if (expression == EyeExpression.SQUINT) {
            submitSquintEye(poseStack, bufferSource, renderType, light, overlay, clampedSkinX, clampedSkinY, clampedEyelidSkinX, clampedEyelidSkinY, eyeWidth, eyeHeight, dstX1, dstY1, dstY2, mirrored);
            return;
        }

        if (expression == EyeExpression.OPEN && shouldExtendSclera(eyeWidth, eyeHeight, hurtSclera)) {
            submitHurtScleraEye(poseStack, bufferSource, renderType, light, overlay, clampedSkinX, clampedSkinY, eyeWidth, eyeHeight, dstX1, dstY1, dstY2, side, mirrored);
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
                quad(bufferSource.getBuffer(renderType), poseStack.last(), columnDstX1, dstY1, columnDstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR);
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
        quad(bufferSource.getBuffer(renderType), poseStack.last(), dstX1, dstY1, dstX2, dstY2, u1, v1, u2, v2, light, overlay, color);
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
                for (ModelPart.Polygon polygon : cube.polygons) {
                    Vector3f normal = polygon.normal();
                    if (normal.z() < -0.5F) {
                        return fromVertices(polygon.vertices());
                    }
                }
                return null;
            }

            private static FaceBounds fromVertices(ModelPart.Vertex[] vertices) {
                if (vertices == null || vertices.length == 0) {
                    return null;
                }

                float minX = Float.POSITIVE_INFINITY;
                float minY = Float.POSITIVE_INFINITY;
                float minZ = Float.POSITIVE_INFINITY;
                float maxX = Float.NEGATIVE_INFINITY;
                float maxY = Float.NEGATIVE_INFINITY;
                for (ModelPart.Vertex vertex : vertices) {
                    Vector3f pos = vertex.pos();
                    minX = Math.min(minX, pos.x());
                    minY = Math.min(minY, pos.y());
                    minZ = Math.min(minZ, pos.z());
                    maxX = Math.max(maxX, pos.x());
                    maxY = Math.max(maxY, pos.y());
                }

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

    private static void submitMouth(PoseStack poseStack, MultiBufferSource bufferSource, RenderType renderType, int light, int overlay, EyeSettings eyes) {
        float dstX1 = eyes.leftMouthX - HEAD_FRONT_U - 4.0F;
        float dstY1 = eyes.leftMouthY - HEAD_FRONT_V - 8.0F;
        submitMouthPixel(poseStack, bufferSource, renderType, light, overlay, eyes.leftMouthX, eyes.leftMouthY, dstX1, dstY1, dstX1 + 1.0F, dstY1 + 1.0F);
        submitMouthPixel(poseStack, bufferSource, renderType, light, overlay, eyes.rightMouthX, eyes.rightMouthY, dstX1 + 1.0F, dstY1, dstX1 + 2.0F, dstY1 + 1.0F);
    }

    private static void submitAdvancementMouth(PoseStack poseStack, MultiBufferSource bufferSource, RenderType renderType, int light, int overlay, EyeSettings eyes) {
        float coverX = eyes.leftMouthX - HEAD_FRONT_U - 4.0F;
        float coverY = eyes.leftMouthY - HEAD_FRONT_V - 8.0F;
        submitMouthCover(poseStack, bufferSource, renderType, light, overlay, eyes, coverX, coverY, 2.0F, 1.0F);

        float centerX = ((eyes.leftMouthX + 0.5F) + (eyes.rightMouthX + 0.5F)) * 0.5F;
        float centerY = ((eyes.leftMouthY + 0.5F) + (eyes.rightMouthY + 0.5F)) * 0.5F;
        float width = 1.25F;
        float height = 1.25F;
        float dstX1 = centerX - HEAD_FRONT_U - 4.0F - width * 0.5F;
        float dstY1 = centerY - HEAD_FRONT_V - 8.0F - 0.5F - ADVANCEMENT_MOUTH_TOP_EXTENSION;
        float splitX = dstX1 + width * 0.5F;
        submitMouthPixel(poseStack, bufferSource, renderType, light, overlay, eyes.leftMouthX, eyes.leftMouthY, 1.25F, dstX1, dstY1, splitX, dstY1 + height + ADVANCEMENT_MOUTH_TOP_EXTENSION);
        submitMouthPixel(poseStack, bufferSource, renderType, light, overlay, eyes.rightMouthX, eyes.rightMouthY, 1.25F, splitX, dstY1, dstX1 + width, dstY1 + height + ADVANCEMENT_MOUTH_TOP_EXTENSION);
    }

    private static void submitUseMouth(PoseStack poseStack, MultiBufferSource bufferSource, RenderType renderType, int light, int overlay, EyeSettings eyes, PlayerActionAnimationState.MouthUseAnimation animation, float ageInTicks, int entityId) {
        float coverX = eyes.leftMouthX - HEAD_FRONT_U - 4.0F;
        float coverY = eyes.leftMouthY - HEAD_FRONT_V - 8.0F;
        submitMouthCover(poseStack, bufferSource, renderType, light, overlay, eyes, coverX, coverY, 2.0F, 1.0F);

        float offsetTicks = ageInTicks + seededOffset(entityId, 41, 6);
        float wave = 0.5F + 0.5F * (float) Math.sin(offsetTicks * Math.PI * 0.35D);
        float width = animation == PlayerActionAnimationState.MouthUseAnimation.DRINKING ? 1.15F + wave * 0.15F : 1.45F + wave * 0.35F;
        float height = animation == PlayerActionAnimationState.MouthUseAnimation.DRINKING ? 1.15F + wave * 0.25F : 1.0F + wave * 0.45F;
        float centerX = ((eyes.leftMouthX + 0.5F) + (eyes.rightMouthX + 0.5F)) * 0.5F;
        float dstX1 = centerX - HEAD_FRONT_U - 4.0F - width * 0.5F;
        float dstY1 = coverY + 0.05F;
        float splitX = dstX1 + width * 0.5F;
        submitMouthPixel(poseStack, bufferSource, renderType, light, overlay, eyes.leftMouthX, eyes.leftMouthY, 1.0F, dstX1, dstY1, splitX, dstY1 + height);
        submitMouthPixel(poseStack, bufferSource, renderType, light, overlay, eyes.rightMouthX, eyes.rightMouthY, 1.0F, splitX, dstY1, dstX1 + width, dstY1 + height);
    }

    private static void submitMouthCover(PoseStack poseStack, MultiBufferSource bufferSource, RenderType renderType, int light, int overlay, EyeSettings eyes, float dstX1, float dstY1, float width, float height) {
        int sourceX = clamp(eyes.leftMouthX - 1, 0, (int) SKIN_SIZE - 1);
        int sourceY = clamp(eyes.leftMouthY, 0, (int) SKIN_SIZE - 1);
        float u1 = (sourceX + MOUTH_UV_INSET) / SKIN_SIZE;
        float v1 = (sourceY + MOUTH_UV_INSET) / SKIN_SIZE;
        float u2 = (sourceX + 1.0F - MOUTH_UV_INSET) / SKIN_SIZE;
        float v2 = (sourceY + 1.0F - MOUTH_UV_INSET) / SKIN_SIZE;
        mouthCoverQuad(bufferSource.getBuffer(renderType), poseStack.last(), dstX1, dstY1, dstX1 + width, dstY1 + height, u1, v1, u2, v2, light, overlay, NORMAL_COLOR);
    }

    private static void submitMouthPixel(PoseStack poseStack, MultiBufferSource bufferSource, RenderType renderType, int light, int overlay, int skinX, int skinY, float dstX1, float dstY1, float dstX2, float dstY2) {
        submitMouthPixel(poseStack, bufferSource, renderType, light, overlay, skinX, skinY, 1.0F, dstX1, dstY1, dstX2, dstY2);
    }

    private static void submitMouthPixel(PoseStack poseStack, MultiBufferSource bufferSource, RenderType renderType, int light, int overlay, int skinX, int skinY, float sourceHeight, float dstX1, float dstY1, float dstX2, float dstY2) {
        int sourceX = clamp(skinX, 0, (int) SKIN_SIZE - 1);
        int sourceY = clamp(skinY, 0, (int) SKIN_SIZE - 1);
        float clampedSourceHeight = Math.min(sourceHeight, SKIN_SIZE - sourceY);
        float u1 = (sourceX + MOUTH_UV_INSET) / SKIN_SIZE;
        float v1 = (sourceY + MOUTH_UV_INSET) / SKIN_SIZE;
        float u2 = (sourceX + 1.0F - MOUTH_UV_INSET) / SKIN_SIZE;
        float v2 = (sourceY + clampedSourceHeight - MOUTH_UV_INSET) / SKIN_SIZE;
        mouthQuad(bufferSource.getBuffer(renderType), poseStack.last(), dstX1, dstY1, dstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR);
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

    private static void submitSquintEye(PoseStack poseStack, MultiBufferSource bufferSource, RenderType renderType, int light, int overlay, int skinX, int skinY, int eyelidX, int eyelidY, int eyeWidth, int eyeHeight, float dstX1, float dstY1, float dstY2, boolean mirrored) {
        float visibleHeight = Math.max(0.333F, (dstY2 - dstY1) * SQUINT_VISIBLE_EYE_COVERAGE);
        float splitY = Math.max(dstY1, dstY2 - visibleHeight);
        float dstX2 = dstX1 + eyeWidth;
        float eyelidU1 = eyelidX / SKIN_SIZE;
        float eyelidV1 = eyelidY / SKIN_SIZE;
        float eyelidU2 = (eyelidX + 1) / SKIN_SIZE;
        float eyelidV2 = (eyelidY + 1) / SKIN_SIZE;
        quad(bufferSource.getBuffer(renderType), poseStack.last(), dstX1, dstY1, dstX2, splitY, eyelidU1, eyelidV1, eyelidU2, eyelidV2, light, overlay, EYELID_DARKEN_COLOR);

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
                quad(bufferSource.getBuffer(renderType), poseStack.last(), columnDstX1, splitY, columnDstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR);
            }
            return;
        }

        float u1 = skinX / SKIN_SIZE;
        float v1 = sourceY1 / SKIN_SIZE;
        float u2 = (skinX + eyeWidth) / SKIN_SIZE;
        float v2 = (skinY + eyeHeight) / SKIN_SIZE;
        quad(bufferSource.getBuffer(renderType), poseStack.last(), dstX1, splitY, dstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR);
    }

    private static boolean shouldExtendSclera(int eyeWidth, int eyeHeight, boolean hurtSclera) {
        return hurtSclera && eyeWidth == 2 && (eyeHeight == 1 || eyeHeight == 2);
    }

    private static void submitHurtScleraEye(PoseStack poseStack, MultiBufferSource bufferSource, RenderType renderType, int light, int overlay, int skinX, int skinY, int eyeWidth, int eyeHeight, float dstX1, float dstY1, float dstY2, EyeSide side, boolean mirrored) {
        float extendedDstY1 = dstY1 - HURT_SCLERA_EXTENSION;
        int externalColumn = side == EyeSide.LEFT ? 0 : eyeWidth - 1;
        int externalSourceX = mirrored ? skinX + eyeWidth - 1 - externalColumn : skinX + externalColumn;
        float externalU1 = externalSourceX / SKIN_SIZE;
        float externalV1 = skinY / SKIN_SIZE;
        float externalU2 = (externalSourceX + 1) / SKIN_SIZE;
        float externalV2 = (skinY + eyeHeight) / SKIN_SIZE;
        quad(bufferSource.getBuffer(renderType), poseStack.last(), dstX1, extendedDstY1, dstX1 + eyeWidth, dstY1, externalU1, externalV1, externalU2, externalV2, light, overlay, NORMAL_COLOR);

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
            quad(bufferSource.getBuffer(renderType), poseStack.last(), columnDstX1, extendedDstY1, columnDstX2, dstY2, u1, v1, u2, v2, light, overlay, NORMAL_COLOR);
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

    private static boolean isBlinking(PlayerRenderState state, ReactionsClientConfig config) {
        int baseInterval = Math.max(20, config.blinkIntervalTicks);
        int randomWindow = Math.max(20, baseInterval / 2);
        int blinkIndex = Math.max(0, (int) state.ageInTicks / baseInterval);
        int interval = baseInterval + seededOffset(state.id, blinkIndex, randomWindow);
        int phase = Math.floorMod((int) state.ageInTicks + seededOffset(state.id, blinkIndex + 31, randomWindow), interval);
        return phase < config.blinkDurationTicks;
    }

    private static HumanoidArm spyglassUseArm(PlayerRenderState state) {
        if (state.rightArmPose == HumanoidModel.ArmPose.SPYGLASS) {
            return HumanoidArm.RIGHT;
        }
        if (state.leftArmPose == HumanoidModel.ArmPose.SPYGLASS) {
            return HumanoidArm.LEFT;
        }
        return null;
    }

    private static HumanoidArm bowUseArm(PlayerRenderState state) {
        if (state.rightArmPose == HumanoidModel.ArmPose.BOW_AND_ARROW) {
            return HumanoidArm.RIGHT;
        }
        if (state.leftArmPose == HumanoidModel.ArmPose.BOW_AND_ARROW) {
            return HumanoidArm.LEFT;
        }
        return null;
    }

    private static boolean isBowFullyDrawn(PlayerRenderState state, HumanoidArm bowArm) {
        return bowArm != null && state.ticksUsingItem >= BOW_FULL_CHARGE_TICKS;
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

    private static int mirroredIdleEye(PlayerRenderState state) {
        if (state.walkAnimationSpeed > 0.01F) {
            IDLE_STARTED_AT.remove(state.id);
            return 0;
        }

        float idleStartedAt = IDLE_STARTED_AT.computeIfAbsent(state.id, id -> state.ageInTicks);
        int idleTicks = (int) (state.ageInTicks - idleStartedAt);
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

    private static int blockFocusEye(int entityId, boolean isSelf) {
        float focus = isSelf ? BlockInteractionEyeFocus.localFocusAmount() : ReactionsNetworking.remoteEyeFocus(entityId) / 100.0F;
        if (focus <= -BLOCK_FOCUS_EYE_THRESHOLD) {
            return -1;
        }
        if (focus >= BLOCK_FOCUS_EYE_THRESHOLD) {
            return 1;
        }
        return 0;
    }

    private static RenderType renderType(ResourceLocation texture) {
        return RenderType.entityCutout(texture);
    }

    private static String playerName(PlayerRenderState state) {
        return state.name == null ? null : state.name;
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
