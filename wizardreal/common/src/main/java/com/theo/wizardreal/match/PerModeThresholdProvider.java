package com.theo.wizardreal.match;

import com.theo.voicecast.api.ThresholdHint;

/**
 * Per-mode threshold provider — issue #29 ④ "mechanism interface" (P6 port),
 * reworked by the C1b semantic contract: the provider no longer returns
 * matcher-tier constants for WizardReal-side matching (matching moved into
 * voicecast) — it PRODUCES a {@link ThresholdHint} for one (mode, spell)
 * pair, which travels WITH the vocabulary push as data (work order C1b
 * §0.3: 玩法调优过界的是数据，不是逻辑).
 *
 * <p>The S7 cross-scale threshold lesson stands: absolute matcher thresholds
 * are not reusable across candidate-set changes, so per-mode re-calibration
 * (same-protocol negative samples, 1st percentile + 0.03) feeds per-mode
 * hint rows once the four-mode casting-time routing lands (issue #30).
 *
 * <p>{@code forward} = the CTC posterior tier, {@code phoneme} =
 * the phoneme-similarity tier, {@code text} = the alias-matching tier; a
 * null component keeps the voicecast engine-calibration default.
 */
public interface PerModeThresholdProvider {

    /** Effective hint for the given mode id (see class javadoc). */
    ThresholdHint thresholds(String mode);

    /**
     * Effective hint for a (mode, spell) pair (#30 D3's per-spell
     * calibration rows). Default: spell-independent — delegates to
     * {@link #thresholds(String)}; mode-aware implementations override to
     * consult per-spell rows first.
     */
    default ThresholdHint thresholds(String mode, String spellId) {
        return thresholds(mode);
    }

    /** Default implementation: no overrides — every tier keeps the voicecast
     *  engine-calibration default (identical to the pre-C1b hard-coded
     *  behavior, which used the same constants). */
    static PerModeThresholdProvider fullVocabulary() {
        return mode -> new ThresholdHint(null, null, null);
    }

    /**
     * Score-band cut points for the given mode (wizardReal#43): where the
     * perfect / excellent / success lines sit for graded
     * {@code LineMatch.score}s. Data, not constants — per-mode calibration
     * overrides this; the default ships {@link ScoreBands#DEFAULT}
     * (practice grading uses {@link ScoreBands#STRICT} via its own engine
     * wiring).
     */
    default ScoreBands scoreBands(String mode) {
        return ScoreBands.DEFAULT;
    }

    /**
     * 失败庇护 relaxation (voiceCast#52, per-player): how many points the
     * AMBIGUOUS-acceptance floor drops below the mode's success line once a
     * player is on a failure streak (same spell, ≥ 3 consecutive failed
     * chants). Default 0.05 (= the "+5%" ruling); 0 disables the庇护
     * relaxation for the mode.
     */
    default float failSafeBonus(String mode) {
        return 0.05f;
    }
}
