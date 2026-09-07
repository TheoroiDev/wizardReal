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

/**
 * Phonetic-tolerance cases gathered from the engbench failure corpus
 * (tools/benchmark/out/engbench): zh homophones (identical pinyin), latin
 * vowel errors and single-insertion near-misses must match, while distinct
 * spells must not cross-match.
 */
class SpellMatcherPhoneticTest {

    @BeforeEach
    void setUp() {
        SpellRegistry.clear();
        SpellRegistry.replace(TestSpell.of("wizardreal:falsum",
                new Pronunciation("falsum", List.of(), List.of("falsum", "幻弹"))));
        SpellRegistry.replace(TestSpell.of("wizardreal:fulgur",
                new Pronunciation("fulgur", List.of(), List.of("雷蓄"))));
        SpellRegistry.replace(TestSpell.of("wizardreal:explosion",
                new Pronunciation("explosion", List.of(), List.of("explosion"))));
    }

    @AfterEach
    void tearDown() {
        SpellRegistry.clear();
    }

    @Test
    void zhHomophonesMatchViaPinyin() {
        // 幻弹 → huan dan; the recognizer heard 换蛋/换谈 — identical pinyin
        assertMatch("换蛋", "wizardreal:falsum");
        assertMatch("换谈。", "wizardreal:falsum");
        // 雷蓄 → lei xu; heard 雷续 — identical pinyin
        assertMatch("雷续。", "wizardreal:fulgur");
    }

    @Test
    void latinVowelAndInsertionErrorsMatch() {
        // falsome = single insertion + vowel error on falsum
        assertMatch("falsome", "wizardreal:falsum");
        // vowel error inside explosion
        assertMatch("explion", "wizardreal:explosion");
    }

    @Test
    void distinctSpellsDoNotCrossMatch() {
        // phonetic layer must not turn unrelated words into matches
        assertNull(SpellMatcher.match("banana"));
        assertNull(SpellMatcher.match(" telefono "));
    }

    private static void assertMatch(String heard, String expectedSpell) {
        SpellMatcher.Match m = SpellMatcher.match(heard);
        assertNotNull(m, "no match for heard=" + heard);
        assertEquals(expectedSpell, m.spell().id(), "wrong spell for heard=" + heard);
    }
}
