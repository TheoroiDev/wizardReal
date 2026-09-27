package com.theo.wizardreal.server;

import com.theo.voicecast.match.ChantLineMatcher;
import com.theo.wizardreal.TestSpell;
import com.theo.wizardreal.api.Spell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** wizardReal#43: the practice engine's strict gate on graded line scores —
 *  practice 严档（STRICT.pass = 0.85）while combat stays loose for the accent
 *  buffer. */
class ChantEnginePracticeTest {

    private static final long T0 = 1_000_000L;

    /** Stub matcher: matches the line whose first alias equals the heard text,
     *  grading it with a fixed score (stands in for the W3 adjudicator path). */
    private static ChantLineMatcher graded(float score) {
        return (pronId, lineIpa, aliases, heard, heardIpa, verdict) -> {
            boolean hit = aliases != null && heard != null
                    && aliases.stream().anyMatch(a -> a.equalsIgnoreCase(heard.trim()));
            return new ChantLineMatcher.LineMatch(hit, hit ? score : 0f);
        };
    }

    private static Spell spell() {
        return new TestSpell("wizardreal:ritual", null, -1f, List.of(
                new com.theo.wizardreal.api.Chant(List.of(
                        new com.theo.wizardreal.api.ChantLine("k1", pron("ignis")),
                        new com.theo.wizardreal.api.ChantLine("k2", pron("fire burn")),
                        new com.theo.wizardreal.api.ChantLine("k3", pron("explosion"))))));
    }

    private static com.theo.wizardreal.api.Pronunciation pron(String... aliases) {
        return new com.theo.wizardreal.api.Pronunciation("p", List.of(), List.of(aliases));
    }

    @Test
    void combatAcceptsLooseScoresPracticeDoesNot() {
        // Combat (loose): a graded 0.80 completes the line — accent buffer.
        ChantEngine combat = new ChantEngine(spell(), T0, 60_000, -1, graded(0.80f), false);
        assertTrue(combat.feed("ignis", null, T0 + 100).consumed());
        assertEquals(1, combat.lineScores().size());

        // Practice (strict): the same 0.80 sits below the 0.85 success line.
        ChantEngine practice = new ChantEngine(spell(), T0, 60_000, -1, graded(0.80f), true);
        ChantEngine.FeedResult r = practice.feed("ignis", null, T0 + 100);
        assertTrue(r.consumed());
        assertTrue(r.progress().isEmpty());
        assertFalse(r.finished());
        assertTrue(practice.lineScores().isEmpty());
    }

    @Test
    void practiceAcceptsAtTheStrictFloorAndTracksScores() {
        ChantEngine practice = new ChantEngine(spell(), T0, 60_000, -1, graded(0.85f), true);
        practice.feed("ignis", null, T0 + 100);
        practice.feed("fire burn", null, T0 + 1000);
        ChantEngine.FeedResult last = practice.feed("explosion", null, T0 + 2000);
        assertTrue(last.finished());
        assertEquals(List.of(0.85f, 0.85f, 0.85f), practice.lineScores());
        assertEquals(0.85f, practice.averageScore(), 0.0001f);
        assertTrue(practice.isPractice());
    }

    @Test
    void combatAverageScoreDrivesTheBand() {
        // 1.0 + 0.9 + 0.8 -> average 0.9 = EXCELLENT under DEFAULT bands.
        ChantLineMatcher varying = (pronId, lineIpa, aliases, heard, heardIpa, verdict) -> {
            float s = heard.equalsIgnoreCase("ignis") ? 1.0f
                    : heard.equalsIgnoreCase("fire burn") ? 0.9f
                    : heard.equalsIgnoreCase("explosion") ? 0.8f : 0f;
            boolean hit = aliases != null && heard != null
                    && aliases.stream().anyMatch(a -> a.equalsIgnoreCase(heard.trim()));
            return new ChantLineMatcher.LineMatch(hit, hit ? s : 0f);
        };
        ChantEngine combat = new ChantEngine(spell(), T0, 60_000, -1, varying, false);
        combat.feed("ignis", null, T0 + 100);
        combat.feed("fire burn", null, T0 + 1000);
        combat.feed("explosion", null, T0 + 2000);
        assertEquals(0.9f, combat.averageScore(), 0.0001f);
        assertEquals(com.theo.wizardreal.match.ScoreBands.Band.EXCELLENT,
                com.theo.wizardreal.match.ScoreBands.DEFAULT.bandOf(combat.averageScore()));
    }
}
