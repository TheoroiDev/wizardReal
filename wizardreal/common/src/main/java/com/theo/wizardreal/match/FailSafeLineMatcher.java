package com.theo.wizardreal.match;

import com.theo.voicecast.match.ChantLineMatcher;
import com.theo.voicecast.match.LenientLineMatcher;

import java.util.List;

/**
 * 失败庇护 line matcher (voiceCast#52, per-player dynamic threshold): wraps
 * the production binding and, once the player is on a failure streak with the
 * same spell, additionally accepts the utterance's AMBIGUOUS verdict when it
 * names this line with a graded score at/above the relaxed floor (the mode's
 * success line minus the fail-safe bonus, default 0.70 − 0.05 = 0.65).
 *
 * <p>Scope guard: only the IN-CHANT line progression relaxes — idle routing
 * (ChantGate) is untouched, so the庇护 can never create new spell triggers,
 * only help an ongoing chant progress. The relaxation is per-match live (the
 * engine asks a supplier), so a streak that builds up mid-chant applies from
 * the next utterance; a success resets it (caller-side).
 */
public final class FailSafeLineMatcher implements ChantLineMatcher {

    /** Live relaxation state for one (player, spell) pair. */
    public interface Relaxation {
        /** true when the failure streak has reached the庇护 threshold. */
        boolean relaxed();

        /** The mode's success line (e.g. {@code ScoreBands.DEFAULT.pass()}). */
        float passFloor();

        /** The relaxation bonus in points (e.g. {@code 0.05f}; 0 = off). */
        float bonus();
    }

    private static final ChantLineMatcher DEFAULT_DELEGATE = LenientLineMatcher.INSTANCE;

    private final ChantLineMatcher delegate;
    private final Relaxation relaxation;

    public FailSafeLineMatcher(ChantLineMatcher delegate, Relaxation relaxation) {
        this.delegate = delegate == null ? DEFAULT_DELEGATE : delegate;
        this.relaxation = relaxation;
    }

    @Override
    public LineMatch match(String linePronId, List<String> lineIpa, List<String> aliases,
                           String heard, List<String> heardIpa, ChantVerdict verdict) {
        LineMatch lm = delegate.match(linePronId, lineIpa, aliases, heard, heardIpa, verdict);
        if (lm.matched() || !relaxation.relaxed()) return lm;

        float floor = relaxation.passFloor() - relaxation.bonus();
        if (verdict != null && linePronId != null && linePronId.equals(verdict.pronId())
                && "AMBIGUOUS".equals(verdict.decision())
                && verdict.score() >= floor) {
            return new LineMatch(true, verdict.score());
        }
        return lm;
    }
}
