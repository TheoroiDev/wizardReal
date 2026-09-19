package com.theo.wizardreal.match;

import com.theo.voicecast.api.Pronunciation;
import com.theo.wizardreal.TestSpell;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Alias tie-break cases (issue #29 P6 port of the lab TextMatcherTieTest —
 * TM-FIX, build/accent_calibration/c1ca_report.md): score ties are broken by
 * LONGEST alias first (most specific match); a full tie falls back to spell
 * id lexicographic order.
 *
 * <p>Case adaptation note (production scoring differs from the lab
 * TextMatcher, which has no phonetic layer): SpellMatcher.scoreAlias returns
 * the flat contains scores (0.9/0.95) EARLY — the phonetic fallback only
 * applies to pairs that are NOT contained — so contained aliases tie exactly
 * at 0.9 and the rule ports cleanly (cases 1-3). The lab's verbatim
 * sagitta/lumen sentences (cases 1/2 in the lab harness) are NOT ties in
 * production: the non-contained longer alias reaches 1.0 through the phonetic
 * fallback and wins on score alone, before and after this port (no behavior
 * change) — the rule itself is pinned by cases 1-3 below.
 */
class SpellMatcherTieTest {

    @BeforeEach
    void setUp() {
        SpellRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        SpellRegistry.clear();
    }

    @Test
    void longestAliasWinsTheContainsTie() {
        // Artifact shape (sagitta/lumen C1c-a): a short alias (圣光) contained
        // inside another spell's longer alias (圣光矢) — both contain-match the
        // sentence at exactly 0.9. The old "first candidate wins" rule gave it
        // to the short alias listed first; the longest alias must win.
        List<Spell> entries = List.of(
                spell("wizardreal:aaa", "圣光"),
                spell("wizardreal:bbb", "圣光矢"));
        SpellMatcher.Match m = SpellMatcher.match("圣光矢初现", entries);
        assertNotNull(m);
        assertEquals("wizardreal:bbb", m.spell().id());
        assertEquals(0.9f, m.score(), 1e-6f);
    }

    @Test
    void fullTieFallsBackToSpellIdOrder() {
        // Identical aliases, larger id listed first -> smaller id wins
        // (lab TextMatcherTieTest case 5; matches the old first-in-vocab
        // behavior on id-sorted vocab files).
        List<Spell> twins = List.of(
                spell("wizardreal:bbb", "爆裂"),
                spell("wizardreal:aaa", "爆裂"));
        SpellMatcher.Match m = SpellMatcher.match("爆裂吧", twins);
        assertNotNull(m);
        assertEquals("wizardreal:aaa", m.spell().id());
        assertEquals(0.9f, m.score(), 1e-6f);
    }

    @Test
    void containsTieWithSameLengthResolvesById() {
        // Mixed sentence containing BOTH 圣光束 and 圣光矢 (lab case 3): both
        // contain-match at 0.9, same alias length -> smaller spell id wins.
        List<Spell> entries = List.of(
                spell("wizardreal:lumen", "lumen", "holy beam", "圣光束", "圣光"),
                spell("wizardreal:sagitta", "sagitta", "holy arrow", "圣光矢", "光箭"));
        SpellMatcher.Match m = SpellMatcher.match("圣光束与圣光矢", entries);
        assertNotNull(m);
        assertEquals("wizardreal:lumen", m.spell().id());
        assertEquals(0.9f, m.score(), 1e-6f);
    }

    @Test
    void exactMatchBeatsLongerAlias() {
        // Score still outranks specificity (lab case 4): exact equality (1.0)
        // beats a longer alias that scores lower (0.6 via the phonetic
        // fallback), regardless of list order.
        List<Spell> entries = List.of(
                spell("wizardreal:zz-long", "圣光矢初现"),
                spell("wizardreal:aa-exact", "圣光矢"));
        SpellMatcher.Match m = SpellMatcher.match("圣光矢", entries);
        assertNotNull(m);
        assertEquals("wizardreal:aa-exact", m.spell().id());
        assertEquals(1.0f, m.score(), 1e-6f);
    }

    @Test
    void unrelatedTextDoesNotMatch() {
        List<Spell> entries = List.of(
                spell("wizardreal:lumen", "lumen", "holy beam", "圣光束", "圣光"),
                spell("wizardreal:sagitta", "sagitta", "holy arrow", "圣光矢", "光箭"));
        assertNull(SpellMatcher.match("完全没有咒语的话", entries));
    }

    private static Spell spell(String id, String... aliases) {
        return new TestSpell(id, new Pronunciation(id, List.of(), List.of(aliases)), -1f, List.of());
    }
}
