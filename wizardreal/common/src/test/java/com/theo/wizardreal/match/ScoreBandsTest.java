package com.theo.wizardreal.match;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** wizardReal#43 评分带: the band cuts are the success line + the two grade
 *  lines above it; STRICT is the practice set. */
class ScoreBandsTest {

    @Test
    void defaultCutsGradeByTheBoundary() {
        ScoreBands b = ScoreBands.DEFAULT;
        assertEquals(ScoreBands.Band.PERFECT, b.bandOf(0.95f));
        assertEquals(ScoreBands.Band.EXCELLENT, b.bandOf(0.9499f));
        assertEquals(ScoreBands.Band.EXCELLENT, b.bandOf(0.85f));
        assertEquals(ScoreBands.Band.PASS, b.bandOf(0.8499f));
        assertEquals(ScoreBands.Band.PASS, b.bandOf(0.70f));
        assertEquals(ScoreBands.Band.MISS, b.bandOf(0.6999f));
        assertEquals(ScoreBands.Band.MISS, b.bandOf(0f));
        assertEquals(ScoreBands.Band.PERFECT, b.bandOf(1f));
    }

    @Test
    void strictPracticeCutsAreRaised() {
        ScoreBands s = ScoreBands.STRICT;
        assertEquals(ScoreBands.Band.MISS, s.bandOf(ScoreBands.DEFAULT.pass()));
        assertEquals(ScoreBands.Band.PASS, s.bandOf(0.85f));
        assertEquals(ScoreBands.Band.PERFECT, s.bandOf(0.98f));
    }

    @Test
    void averageIsTheBandInput() {
        assertEquals(0f, ScoreBands.average(List.of()), 0.0001f);
        assertEquals(0.9f, ScoreBands.average(List.of(0.85f, 0.95f)), 0.0001f);
    }

    @Test
    void invertedCutsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ScoreBands(0.85f, 0.95f, 0.70f));
        assertThrows(IllegalArgumentException.class, () -> new ScoreBands(0.95f, 0.85f, 1.1f));
    }
}
