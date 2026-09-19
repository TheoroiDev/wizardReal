package com.theo.wizardreal.match;

import com.theo.wizardreal.server.ServerVoiceCast;

/**
 * Per-mode threshold provider — issue #29 ④ "mechanism interface" ONLY (P6
 * port): the S7 cross-scale threshold lesson showed that absolute matcher
 * thresholds are not reusable across candidate-set changes, so per-mode
 * re-calibration (same-protocol negative samples, 1st percentile + 0.03)
 * will be needed once the four-mode casting-time routing lands (issue #30).
 * This interface is the seam that routing will consume; the actual
 * multi-mode calibration ships with #30 and nothing wires non-default
 * providers yet.
 *
 * <p>Mode ids are deliberately free-form strings until #30 names them; the
 * default {@link #fullVocabulary()} provider ignores the mode and returns
 * the shipped full-vocabulary constants — the exact pre-#30 behavior.
 *
 * <p>{@code forward} = CTC posterior acceptance ({@code FORWARD_MATCH_
 * THRESHOLD} semantics), {@code phoneme} = {@link PhonemeMatcher} tier,
 * {@code text} = {@link SpellMatcher} tier.
 */
public interface PerModeThresholdProvider {

    /** The three matcher-tier thresholds for one mode. */
    record Thresholds(float forward, float phoneme, float text) {}

    /** Effective thresholds for the given mode id (see class javadoc). */
    Thresholds thresholds(String mode);

    /** Default implementation: the shipped full-vocabulary constants for
     *  every mode (identical to the pre-#30 hard-coded behavior). */
    static PerModeThresholdProvider fullVocabulary() {
        return mode -> new Thresholds(ServerVoiceCast.FORWARD_MATCH_THRESHOLD,
                PhonemeMatcher.MATCH_THRESHOLD, SpellMatcher.MATCH_THRESHOLD);
    }
}
