package me.tochuuu.reactions.client;

import java.util.UUID;

public record RemoteEyeConfig(
    UUID playerId,
    int entityId,
    int leftEyeX,
    int leftEyeY,
    int rightEyeX,
    int rightEyeY,
    boolean mouthEnabled,
    int leftMouthX,
    int leftMouthY,
    int rightMouthX,
    int rightMouthY,
    int eyelidColorX,
    int eyelidColorY,
    int eyeWidth,
    int eyeHeight,
    boolean cleanEyelidColor,
    boolean texturedEyelids,
    int eyelidTintIntensity,
    ReactionsClientConfig.DisabledEye disabledEye,
    boolean eyebrowsEnabled,
    ReactionsClientConfig.EyeSkinLayer eyeSkinLayer,
    ReactionsClientConfig.EyeSkinLayer leftEyeSkinLayer,
    ReactionsClientConfig.EyeSkinLayer rightEyeSkinLayer,
    ReactionsClientConfig.EyeSkinLayer mouthSkinLayer,
    ReactionsClientConfig.EyeSkinLayer eyelidColorSkinLayer,
    boolean separateEyelidColors,
    int rightEyelidColorX,
    int rightEyelidColorY,
    ReactionsClientConfig.EyeSkinLayer rightEyelidColorSkinLayer,
    boolean rightEyelidIsMain
) {
    public RemoteEyeConfig {
        eyeWidth = ReactionsClientConfig.clampEyeWidth(eyeWidth);
        eyeHeight = ReactionsClientConfig.clampEyeHeight(eyeHeight);
        if (ReactionsClientConfig.isBlockedEyeSize(eyeWidth, eyeHeight)) {
            eyeHeight = 2;
        }
        eyelidTintIntensity = ReactionsClientConfig.clampEyelidTintIntensity(eyelidTintIntensity);
        if (disabledEye == null) {
            disabledEye = ReactionsClientConfig.DisabledEye.NONE;
        }
        if (eyeSkinLayer == null) {
            eyeSkinLayer = ReactionsClientConfig.EyeSkinLayer.BASE;
        }
        if (leftEyeSkinLayer == null) {
            leftEyeSkinLayer = eyeSkinLayer;
        }
        if (rightEyeSkinLayer == null) {
            rightEyeSkinLayer = eyeSkinLayer;
        }
        if (mouthSkinLayer == null) {
            mouthSkinLayer = ReactionsClientConfig.EyeSkinLayer.BASE;
        }
        if (eyelidColorSkinLayer == null) {
            eyelidColorSkinLayer = ReactionsClientConfig.EyeSkinLayer.BASE;
        }
        rightEyelidColorX = Math.max(0, Math.min(63, rightEyelidColorX));
        rightEyelidColorY = Math.max(0, Math.min(63, rightEyelidColorY));
        if (rightEyelidColorSkinLayer == null) {
            rightEyelidColorSkinLayer = eyelidColorSkinLayer;
        }
    }
}
