package com.theo.wizardreal.match;

import com.theo.voicecast.api.Pronunciation;
import com.theo.wizardreal.TestSpell;
import com.theo.wizardreal.api.SpellRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhonemeMatcherTest {

    private static final String FULMEN_IPA = "ˈfʊlmɛn"; // f ʊ l m ɛ n

    @BeforeEach
    void setUp() {
        SpellRegistry.clear();
        SpellRegistry.replace(TestSpell.of("wizardreal:fulmen",
                new Pronunciation("fulmen", List.of(FULMEN_IPA), List.of("fulmen"))));
    }

    @AfterEach
    void tearDown() {
        SpellRegistry.clear();
    }

    @Test
    void tokenizeStripsStressLengthAndDots() {
        assertEquals(List.of("f", "u", "l", "m", "e", "n"),
                PhonemeMatcher.tokenizeIpa("ˈfʊːl.mɛn"));
    }

    @Test
    void tokenizeKeepsAffricatesTogether() {
        assertEquals(List.of("tʃ"), PhonemeMatcher.tokenizeIpa("tʃ"));
        assertEquals(List.of("dʒ", "a"), PhonemeMatcher.tokenizeIpa("dʒa"));
        assertEquals(List.of("ts", "u"), PhonemeMatcher.tokenizeIpa("tsu"));
        assertEquals(List.of("t", "s"), PhonemeMatcher.tokenizeIpa("t s"));
    }

    @Test
    void normalizeTokensAppliesVowelClasses() {
        // lax->tense and open-mid->mid mappings from workspace-root docs/IPA识别问题.md
        assertEquals(List.of("f", "u", "m", "ə", "n"),
                PhonemeMatcher.normalizeTokens(List.of("f", "ʊ", "m", "ʌ", "n")));
        // lateral normalization: ɫ -> l
        assertEquals(List.of("l"), PhonemeMatcher.normalizeTokens(List.of("ɫ")));
        // engine may join phonemes with spaces; split defensively
        assertEquals(List.of("f", "u", "m"), PhonemeMatcher.normalizeTokens(List.of("f u m")));
    }

    @Test
    void exactTemplateMatches() {
        PhonemeMatcher.Match m = PhonemeMatcher.match(List.of("f", "u", "l", "m", "e", "n"));
        assertNotNull(m);
        assertEquals("wizardreal:fulmen", m.spell().id());
        assertEquals(1.0f, m.score(), 1e-6f);
    }

    @Test
    void engineShiftedVowelsAndDroppedDarkLStillMatch() {
        // Real-world engine output for "fulmen": [f uː m ʌ n] — see workspace-root docs/IPA识别问题.md.
        // The dropped syllable-final "l" is skipped for free; ʌ->ə costs one
        // substitution against the template's ɛ->e: 1 - 1/5 = 0.8.
        PhonemeMatcher.Match m = PhonemeMatcher.match(
                PhonemeMatcher.normalizeTokens(List.of("f", "uː", "m", "ʌ", "n")));
        assertNotNull(m);
        assertEquals("wizardreal:fulmen", m.spell().id());
        assertEquals(0.8f, m.score(), 1e-6f);
    }

    @Test
    void contiguousTemplateInsideUtteranceGetsSubsequenceBonus() {
        PhonemeMatcher.Match m = PhonemeMatcher.match(
                List.of("a", "f", "u", "l", "m", "e", "n", "z"));
        assertNotNull(m);
        assertEquals(0.92f, m.score(), 1e-6f);
    }

    @Test
    void unrelatedPhonemesDoNotMatch() {
        assertNull(PhonemeMatcher.match(List.of("x")));
        assertNull(PhonemeMatcher.match(List.of("z", "z", "z", "z", "z", "z")));
        assertNull(PhonemeMatcher.match(List.of()));
        assertNull(PhonemeMatcher.match(null));
    }

    @Test
    void perSpellThresholdOverrideApplies() {
        SpellRegistry.replace(new TestSpell("wizardreal:strict",
                new Pronunciation("strict", List.of("ˈʃiːld"), List.of()), 0.95f, List.of()));
        // [ʃ u l d] against template [ʃ i l d]: one substitution -> 0.75, which
        // clears the default 0.6 threshold but not this spell's 0.95 override.
        assertNull(PhonemeMatcher.match(List.of("ʃ", "u", "l", "d")));
        PhonemeMatcher.Match m = PhonemeMatcher.match(List.of("ʃ", "i", "l", "d"));
        assertNotNull(m);
        assertEquals(1.0f, m.score(), 1e-6f);
    }

    @Test
    void skipTargetSimilarityIsFreeOnTargetSideOnly() {
        // heard is a fuzzy subsequence of target with zero substitutions -> 1.0
        assertEquals(1.0f, PhonemeMatcher.skipTargetSimilarity(List.of("f", "n"), List.of("f", "u", "l", "m", "e", "n")), 1e-6f);
        // one substitution among 3 heard tokens -> 2/3
        assertEquals(2f / 3f, PhonemeMatcher.skipTargetSimilarity(List.of("f", "x", "n"), List.of("f", "u", "l", "m", "e", "n")), 1e-6f);
        // heard tokens must all be consumed: extra unrelated heard tokens cost
        assertTrue(PhonemeMatcher.skipTargetSimilarity(List.of("f", "n", "q"), List.of("f", "n")) < 1f);
        assertEquals(0f, PhonemeMatcher.skipTargetSimilarity(List.of("q"), List.of()), 1e-6f);
        assertEquals(1f, PhonemeMatcher.skipTargetSimilarity(List.of(), List.of("f")), 1e-6f);
    }
}
