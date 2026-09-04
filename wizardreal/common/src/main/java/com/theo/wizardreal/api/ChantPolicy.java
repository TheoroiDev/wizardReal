package com.theo.wizardreal.api;

import java.util.Arrays;

/**
 * Per-spell chant tuning parsed from the {@code chant_policy} JSON block (0.4.0).
 *
 * @param powerPerLine  power multiplier for the Nth completed line (index 0 =
 *                      line 1); lines beyond the table clamp to the last entry.
 *                      {@code null}/{@code empty} = every completion is 1.0.
 * @param skipAllowed   whether the spell-name jump (mid-chant early release)
 *                      and the idle skip-cast (破弃) are permitted; forbidden
 *                      spells (禁咒) set this to {@code false}.
 * @param interruptible whether damage may interrupt the chant (07 M1).
 * @param pact          optional self-restriction multiplier (07 M3); {@code null} = none.
 */
public record ChantPolicy(float[] powerPerLine, boolean skipAllowed, boolean interruptible, Pact pact) {
    /** Every completion casts at full power; skipping allowed; interruptible; no pact. */
    public static final ChantPolicy DEFAULT = new ChantPolicy(null, true, true, null);

    public ChantPolicy {
        powerPerLine = powerPerLine == null ? null : Arrays.copyOf(powerPerLine, powerPerLine.length);
    }

    /** Power multiplier after {@code completedLines} lines (1-based count). */
    public float powerFor(int completedLines) {
        if (powerPerLine == null || powerPerLine.length == 0) return 1.0f;
        int index = Math.min(completedLines, powerPerLine.length) - 1;
        return powerPerLine[Math.max(0, index)];
    }

    /** Optional self-restriction: fulfilling {@code require} grants the multiplier. */
    public record Pact(String require, float powerMultiplier) {
        public static final String ALL_LINES = "all_lines";
    }
}
