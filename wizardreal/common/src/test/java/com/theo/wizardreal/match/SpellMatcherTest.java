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

class SpellMatcherTest {

    @BeforeEach
    void setUp() {
        SpellRegistry.clear();
        SpellRegistry.replace(TestSpell.of("wizardreal:ignis",
                new Pronunciation("ignis", List.of(), List.of("ignis", "fire"))));
        SpellRegistry.replace(TestSpell.of("wizardreal:explosion",
                new Pronunciation("explosion", List.of(), List.of("explosion magic"))));
        // CJK alias survives normalization and uses CJK-adjacency word boundaries.
        SpellRegistry.replace(TestSpell.of("wizardreal:bakuretsu",
                new Pronunciation("bakuretsu", List.of(), List.of("爆裂"))));
    }

    @AfterEach
    void tearDown() {
        SpellRegistry.clear();
    }

    @Test
    void exactAliasScoresOne() {
        SpellMatcher.Match m = SpellMatcher.match("ignis");
        assertNotNull(m);
        assertEquals("wizardreal:ignis", m.spell().id());
        assertEquals(1.0f, m.score(), 1e-6f);
    }

    @Test
    void normalizationStripsCaseAndPunctuation() {
        SpellMatcher.Match m = SpellMatcher.match("  IGNIS!!! ");
        assertNotNull(m);
        assertEquals(1.0f, m.score(), 1e-6f);
    }

    @Test
    void wholeWordContainmentScoresNinetyPercent() {
        SpellMatcher.Match m = SpellMatcher.match("please cast fire now");
        assertNotNull(m);
        assertEquals("wizardreal:ignis", m.spell().id());
        assertEquals(0.9f, m.score(), 1e-6f);
    }

    @Test
    void multiWordAliasContainmentScoresNinetyFivePercent() {
        SpellMatcher.Match m = SpellMatcher.match("cast explosion magic now");
        assertNotNull(m);
        assertEquals("wizardreal:explosion", m.spell().id());
        assertEquals(0.95f, m.score(), 1e-6f);
    }

    @Test
    void substringOfASingleWordAliasIsNotAWholeWord() {
        // "ign" is inside "ignis" but not at a word boundary -> no 0.9 shortcut;
        // the Levenshtein similarity of "ign" vs "ignis" is 1 - 2/5 = 0.6 < 0.8.
        assertNull(SpellMatcher.match("ign"));
    }

    @Test
    void smallTypoStillMatchesViaLevenshtein() {
        // distance("ignus","ignis") = 1 -> similarity 0.8 == MATCH_THRESHOLD.
        SpellMatcher.Match m = SpellMatcher.match("ignus");
        assertNotNull(m);
        assertEquals(0.8f, m.score(), 1e-6f);
    }

    @Test
    void largeDistanceFallsBelowDefaultThreshold() {
        assertNull(SpellMatcher.match("ig"));
        assertNull(SpellMatcher.match("abra cadabra"));
    }

    @Test
    void unknownTokenAndEmptyInputMatchNothing() {
        assertNull(SpellMatcher.match("[unk]"));
        assertNull(SpellMatcher.match("   "));
        assertNull(SpellMatcher.match(null));
    }

    @Test
    void cjkAliasMatchesInsideCjkRun() {
        SpellMatcher.Match m = SpellMatcher.match("火球爆裂啊");
        assertNotNull(m);
        assertEquals("wizardreal:bakuretsu", m.spell().id());
        assertEquals(0.9f, m.score(), 1e-6f);
    }

    @Test
    void cjkNormalizationKeepsLettersOnly() {
        assertEquals("爆裂", SpellMatcher.normalize("《爆裂》"));
        // punctuation becomes a word separator (hyphen is not kept)
        assertEquals("ig nis", SpellMatcher.normalize("Ig-Nis!"));
        // apostrophes are kept (English contractions)
        assertEquals("it's", SpellMatcher.normalize("It's!"));
        assertEquals("ignis", SpellMatcher.normalize("IGNIS!!!"));
    }

    @Test
    void perSpellThresholdOverrideApplies() {
        SpellRegistry.replace(new TestSpell("wizardreal:strict",
                new Pronunciation("strict", List.of(), List.of("strict")), 0.95f, List.of()));
        // similarity("stric","strict") = 1 - 1/6 ≈ 0.83 — above the default 0.8
        // but below this spell's 0.95 override, and no other alias comes close.
        assertNull(SpellMatcher.match("stric"));
        SpellMatcher.Match exact = SpellMatcher.match("strict");
        assertNotNull(exact);
        assertEquals("wizardreal:strict", exact.spell().id());
    }

    @Test
    void bestScoringSpellWins() {
        SpellRegistry.replace(TestSpell.of("wizardreal:igni",
                new Pronunciation("igni", List.of(), List.of("igni"))));
        SpellMatcher.Match m = SpellMatcher.match("ignis");
        assertNotNull(m);
        assertEquals("wizardreal:ignis", m.spell().id());
        assertTrue(m.score() >= 1.0f - 1e-6f);
    }

    @Test
    void emptyRegistryMatchesNothing() {
        SpellRegistry.clear();
        assertNull(SpellMatcher.match("ignis"));
    }
}
