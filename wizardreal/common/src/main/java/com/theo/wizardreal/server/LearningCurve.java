package com.theo.wizardreal.server;

/**
 * Pure learning math (voice overhaul D4, 0.4.0). All values are percentages of
 * mastery ({@code t}): 10 = the starting baseline, 100 = fully learned, 625 =
 * the overlearning cap (power 2.5 = sqrt(6.25)).
 *
 * <ul>
 *   <li>Gate: {@code requires_learning} spells need {@code t >= 10} to cast.</li>
 *   <li>Known derivation (D-D2, single source of truth): {@code t > threshold}
 *       — the knownSpells/forgottenSpells sets were removed because the
 *       wizardpedia catalog could not reconcile them.</li>
 *   <li>Power: 10%→0.5, 100%→1.0 linear; beyond 100% only with overlearning
 *       enabled: sqrt(t/100) capped at 2.5.</li>
 * </ul>
 */
public final class LearningCurve {
    public static final float GATE_PERCENT = 10f;
    public static final float DEFAULT_KNOWN_THRESHOLD = 10f;
    public static final float BASE_POWER = 0.5f;
    public static final float MAX_POWER = 2.5f;
    public static final float MAX_PERCENT = 625f;

    private LearningCurve() {}

    /** Hard gate for {@code requires_learning} spells. */
    public static boolean castable(float t) {
        return t >= GATE_PERCENT;
    }

    /** Single-source known derivation: strictly above the threshold. */
    public static boolean known(float t, float knownThreshold) {
        return t > knownThreshold;
    }

    /**
     * Piecewise power curve. Below the gate the floor (0.5) still applies for
     * non-gated spells (new players cast at half power, onboarding intact).
     */
    public static float power(float t, boolean overlearning) {
        if (t <= GATE_PERCENT) return BASE_POWER;
        if (t <= 100f) {
            return BASE_POWER + BASE_POWER * (t - GATE_PERCENT) / (100f - GATE_PERCENT);
        }
        if (!overlearning) return 1.0f;
        return Math.min(MAX_POWER, (float) Math.sqrt(t / 100.0));
    }
}
