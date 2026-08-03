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
    int eyelidTintIntensity
) {
    public RemoteEyeConfig {
        eyeWidth = ReactionsClientConfig.clampEyeWidth(eyeWidth);
        eyeHeight = ReactionsClientConfig.clampEyeHeight(eyeHeight);
        if (ReactionsClientConfig.isBlockedEyeSize(eyeWidth, eyeHeight)) {
            eyeHeight = 2;
        }
        eyelidTintIntensity = ReactionsClientConfig.clampEyelidTintIntensity(eyelidTintIntensity);
    }
}
