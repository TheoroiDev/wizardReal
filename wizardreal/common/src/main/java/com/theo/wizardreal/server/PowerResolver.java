package com.theo.wizardreal.server;

/**
 * Single power synthesis point (voice overhaul). Currently: base power (chant
 * tier or 1.0 for instant casts) x learning curve, clamped to the global 2.5x
 * cap. Pact (P1) and performance (P2) multipliers join here — one clamp, one
 * place to reason about multiplicative interactions.
 */
public final class PowerResolver {
    private PowerResolver() {}

    public static float resolve(float inputPower, float learningT, boolean overlearning) {
        float power = inputPower * LearningCurve.power(learningT, overlearning);
        return Math.max(0f, Math.min(LearningCurve.MAX_POWER, power));
    }
}
