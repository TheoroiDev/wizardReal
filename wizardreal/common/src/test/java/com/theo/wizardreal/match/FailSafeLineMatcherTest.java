package com.theo.wizardreal.match;

import com.theo.voicecast.match.ChantLineMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** voiceCast#52 失败庇护: the per-player relaxation wraps the production
 *  binding — only a failure-streaked player's IN-CHANT AMBIGUOUS verdicts
 *  naming the line, at/above the relaxed floor, get through. */
class FailSafeLineMatcherTest {

    private static final String LINE_ID = "wizardreal:ignis.chant.zh.0:1";

    private static ChantLineMatcher verdict(String decision, String pronId, float score) {
        return (linePronId, lineIpa, aliases, heard, heardIpa, v) -> {
            boolean hit = v != null && v.pronId().equals(linePronId)
                    && v.decision().equals(decision) && v.score() >= 0.70f;
            return new ChantLineMatcher.LineMatch(hit, hit ? v.score() : 0f);
        };
    }

    private static ChantLineMatcher.ChantVerdict ambiguous(String pronId, float score) {
        return new ChantLineMatcher.ChantVerdict(pronId, "AMBIGUOUS", score);
    }

    private FailSafeLineMatcher matcher(boolean relaxed) {
        return new FailSafeLineMatcher(
                verdict("NEAR", LINE_ID, 0.9f), // delegate: NEAR passes, AMBIGUOUS never
                new FailSafeLineMatcher.Relaxation() {
                    @Override public boolean relaxed() { return relaxed; }
                    @Override public float passFloor() { return 0.70f; }
                    @Override public float bonus() { return 0.05f; }
                });
    }

    @Test
    void noStreakKeepsTheStrictBehavior() {
        FailSafeLineMatcher m = matcher(false);
        assertFalse(m.match(LINE_ID, List.of(), List.of(), "x", List.of(),
                ambiguous(LINE_ID, 0.90f)).matched());
    }

    @Test
    void streakRelaxesAmbiguousAtOrAboveTheFloor() {
        FailSafeLineMatcher m = matcher(true);
        assertTrue(m.match(LINE_ID, List.of(), List.of(), "x", List.of(),
                ambiguous(LINE_ID, 0.65f)).matched());
        assertTrue(m.match(LINE_ID, List.of(), List.of(), "x", List.of(),
                ambiguous(LINE_ID, 0.70f)).matched());
        // Below the relaxed floor (0.70 − 0.05 = 0.65): still dark.
        assertFalse(m.match(LINE_ID, List.of(), List.of(), "x", List.of(),
                ambiguous(LINE_ID, 0.6499f)).matched());
    }

    @Test
    void foreignLinesAndPlainMissesNeverRelax() {
        FailSafeLineMatcher m = matcher(true);
        // The AMBIGUOUS verdict names a DIFFERENT line: no acceptance.
        assertFalse(m.match(LINE_ID, List.of(), List.of(), "x", List.of(),
                ambiguous("wizardreal:other.chant.zh.0:1", 0.9f)).matched());
        // No verdict (offline/lenient path): delegate result passthrough.
        assertFalse(m.match(LINE_ID, List.of(), List.of(), "x", List.of(), null).matched());
    }

    @Test
    void delegateMatchesPassThroughWithTheirScore() {
        FailSafeLineMatcher m = matcher(true);
        ChantLineMatcher.LineMatch lm = m.match(LINE_ID, List.of(), List.of(), "ignis", List.of(),
                new ChantLineMatcher.ChantVerdict(LINE_ID, "NEAR", 0.88f));
        assertTrue(lm.matched());
        assertEquals(0.88f, lm.score(), 0.0001f);
    }

    @Test
    void streakSimulation_threeFailsRelaxSuccessResets() {
        // 连败模拟: the ChantManager streak semantics behind the relaxation.
        FailStreaks streaks = new FailStreaks();
        java.util.UUID player = java.util.UUID.randomUUID();
        String spell = "wizardreal:vestibulum";
        String other = "wizardreal:rift";

        for (int i = 0; i < FailStreaks.RELAX_AFTER - 1; i++) {
            streaks.fail(player, spell);
        }
        assertFalse(streaks.relaxed(player, spell));
        streaks.fail(player, spell);
        assertTrue(streaks.relaxed(player, spell));

        // A different spell is unaffected; a success resets only its own.
        assertFalse(streaks.relaxed(player, other));
        streaks.success(player, spell);
        assertFalse(streaks.relaxed(player, spell));
    }
}
