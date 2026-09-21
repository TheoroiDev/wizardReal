package com.theo.wizardreal.server;

import com.theo.wizardreal.TestSpell;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.Pronunciation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Load-time validation: L1 distinctness (04 §8-6), forbidden group (C-5),
 * IPA coverage gaps (C'). */
class SpellLoadValidatorTest {

    private static Pronunciation pron(List<String> aliases, List<String> ipa) {
        return new Pronunciation("p", ipa, aliases);
    }

    private static Spell ritual(String id, String l1) {
        return new TestSpell(id, pron(List.of(id), List.of("ˈipа")), -1f, List.of(
                new Chant(List.of(
                        new ChantLine(null, pron(List.of(l1), List.of("ˈl1"))),
                        new ChantLine(null, pron(List.of("cast-" + id), List.of("ˈcast")))))));
    }

    @Test
    void identicalL1sConflict() {
        List<String> warnings = SpellLoadValidator.validate(List.of(
                ritual("wizardreal:a", "o flame and fury"),
                ritual("wizardreal:b", "o flame and fury")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("L1 conflict")
                && w.contains("wizardreal:a") && w.contains("wizardreal:b")));
    }

    @Test
    void nearIdenticalL1sConflict() {
        // "fiery" vs "fieryy" -> similarity 0.8 >= 0.75.
        List<String> warnings = SpellLoadValidator.validate(List.of(
                ritual("wizardreal:a", "fiery"),
                ritual("wizardreal:b", "fieryy")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("L1 conflict")));
    }

    @Test
    void distinctL1sPass() {
        List<String> warnings = SpellLoadValidator.validate(List.of(
                ritual("wizardreal:a", "o flame and fury"),
                ritual("wizardreal:b", "storm of the deep")));
        assertTrue(warnings.stream().noneMatch(w -> w.contains("L1 conflict")));
    }

    @Test
    void forbiddenGroupWarnings() {
        // difficulty >= 2 + skip allowed -> must warn.
        Spell badForbidden = new TestSpell("wizardreal:bad", pron(List.of("bad"), List.of()), -1f,
                2.5f, false, List.of(),
                new com.theo.wizardreal.api.ChantPolicy(null, true, true, null));
        List<String> warnings = SpellLoadValidator.validate(List.of(badForbidden));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("skip_allowed=false") && w.contains("wizardreal:bad")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("without requires_learning")));

        // skip_allowed=false on a normal spell -> possible-mistake warning.
        Spell pseudoForbidden = new TestSpell("wizardreal:odd", pron(List.of("odd"), List.of()), -1f,
                1.0f, false, List.of(),
                new com.theo.wizardreal.api.ChantPolicy(null, false, true, null));
        assertTrue(SpellLoadValidator.validate(List.of(pseudoForbidden)).stream()
                .anyMatch(w -> w.contains("not a forbidden chant")));
    }

    @Test
    void ipaCoverageGapWarns() {
        Spell noIpa = new TestSpell("wizardreal:bare", pron(List.of("bare"), List.of()), -1f, List.of());
        Spell withIpa = new TestSpell("wizardreal:full", pron(List.of("full"), List.of("fʊl")), -1f, List.of());
        List<String> warnings = SpellLoadValidator.validate(List.of(noIpa, withIpa));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("IPA coverage gap") && w.contains("wizardreal:bare")));
        assertTrue(warnings.stream().noneMatch(w -> w.contains("wizardreal:full") && w.contains("coverage")));
    }

    @Test
    void cleanSpellProducesNoWarnings() {
        // Proper forbidden chant: requires_learning + difficulty 2.5 + skip disallowed.
        Spell forbidden = new TestSpell("wizardreal:forbidden", pron(List.of("forbidden"), List.of("fəˈbɪdən")), -1f,
                2.5f, true, List.of(),
                new com.theo.wizardreal.api.ChantPolicy(null, false, false, null));
        List<String> warnings = SpellLoadValidator.validate(List.of(forbidden, ritual("wizardreal:ok", "totally distinct line")));
        assertFalse(warnings.contains("skip_allowed=false"));
        assertTrue(warnings.stream().noneMatch(w -> w.contains("wizardreal:forbidden")));
    }
}
