package com.theo.wizardreal.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** wizardReal#43 逐词对齐: practice per-word hit mapping (feedback only —
 *  never the progression authority). */
class PracticeAlignerTest {

    @Test
    void latinLineHitsInOrder() {
        boolean[] hits = PracticeAligner.align(
                List.of("let", "the", "earth", "split", "wide"),
                "let the earth split wide");
        assertTrue(hits[0] && hits[1] && hits[2] && hits[3] && hits[4]);
    }

    @Test
    void caseAndPunctuationDoNotBlockHits() {
        boolean[] hits = PracticeAligner.align(
                List.of("Let", "the", "earth,", "split", "wide."),
                "LET THE   earth?! split wide...");
        assertTrue(hits[0] && hits[1] && hits[2] && hits[3] && hits[4]);
    }

    @Test
    void missedAndFuzzyWordsDiffer() {
        // "wid" (typo) fuzzy-hits "wide"; a foreign word stays dark.
        boolean[] hits = PracticeAligner.align(List.of("split", "wide"), "split wid");
        assertTrue(hits[0]);
        assertTrue(hits[1]);
        boolean[] miss = PracticeAligner.align(List.of("split", "wide"), "split bananas");
        assertTrue(miss[0]);
        assertFalse(miss[1]);
    }

    @Test
    void emptyHeardAlignsToNothing() {
        boolean[] hits = PracticeAligner.align(List.of("let", "the"), "");
        assertFalse(hits[0]);
        assertFalse(hits[1]);
    }

    @Test
    void cjkLineHitsByContainment() {
        List<String> words = List.of("以漆黑遮蔽苍穹", "唤醒绯红之雷");
        boolean[] hits = PracticeAligner.align(words, "以漆黑遮蔽苍穹，然后唤醒绯红之雷！");
        assertTrue(hits[0]);
        assertTrue(hits[1]);
        boolean[] half = PracticeAligner.align(words, "以漆黑遮蔽苍穹");
        assertTrue(half[0]);
        assertFalse(half[1]);
    }
}
