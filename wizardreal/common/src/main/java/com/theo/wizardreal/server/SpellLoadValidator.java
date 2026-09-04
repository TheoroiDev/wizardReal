package com.theo.wizardreal.server;

import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.util.Levenshtein;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Load-time validation and startup report (voice overhaul P0, 2026-09-04):
 *
 * <ul>
 *   <li><b>L1 mutual distinctness</b> (04 §8 rule 6): every ritual chain's
 *       first line (the idle-state entry gate) must be distinguishable from
 *       every other chain's L1 — near-identical L1s cause cross-spell false
 *       entries. Pairwise normalized similarity &gt;= 0.75 → warning.</li>
 *   <li><b>Forbidden-chant field group</b> (C-5): {@code difficulty >= 2.0}
 *       implies {@code requires_learning} + {@code skip_allowed=false};
 *       {@code skip_allowed=false} without that difficulty is flagged as a
 *       possible mistake.</li>
 *   <li><b>IPA coverage gaps</b> (tool C'): aliases without any IPA template —
 *     expected for batches inside the IPA-retirement window, a real gap
 *     otherwise.</li>
 * </ul>
 *
 * Pure: warnings come back as strings; the loader logs them. Variant-count
 * warnings (<2 per language) are emitted at parse time by
 * {@code SpellDefinition.expandLanguageKeyedChants}.
 */
final class SpellLoadValidator {
    /** L1 pairs closer than this (normalized similarity) trigger a warning. */
    public static final float L1_DISTINCT_THRESHOLD = 0.75f;
    /** difficulty at/above this marks a forbidden chant (禁咒). */
    public static final float FORBIDDEN_DIFFICULTY = 2.0f;

    private SpellLoadValidator() {}

    static List<String> validate(java.util.Collection<Spell> spells) {
        List<String> warnings = new ArrayList<>();
        l1Distinctness(spells, warnings);
        forbiddenGroup(spells, warnings);
        ipaCoverage(spells, warnings);
        return warnings;
    }

    // ---- 04 §8-6: L1 全库互异 ---------------------------------------------

    private record ChainL1(String spellId, int variant, List<String> aliases) {}

    private static void l1Distinctness(java.util.Collection<Spell> spells, List<String> warnings) {
        List<ChainL1> chains = new ArrayList<>();
        for (Spell spell : spells) {
            List<Chant> chants = spell.chants();
            for (int v = 0; v < chants.size(); v++) {
                List<ChantLine> lines = chants.get(v).lines();
                if (lines.isEmpty()) continue;
                chains.add(new ChainL1(spell.id(), v, lines.get(0).pronunciation().aliases()));
            }
        }
        for (int a = 0; a < chains.size(); a++) {
            for (String aliasA : chains.get(a).aliases()) {
                if (aliasA.isBlank()) continue;
                for (int b = a + 1; b < chains.size(); b++) {
                    for (String aliasB : chains.get(b).aliases()) {
                        if (aliasB.isBlank()) continue;
                        float similarity = normalizedSimilarity(aliasA, aliasB);
                        if (similarity >= L1_DISTINCT_THRESHOLD) {
                            warnings.add(String.format(Locale.ROOT,
                                    "L1 conflict (%.2f >= %.2f): %s[v%d] \"%s\" vs %s[v%d] \"%s\"",
                                    similarity, L1_DISTINCT_THRESHOLD,
                                    chains.get(a).spellId(), chains.get(a).variant(), aliasA,
                                    chains.get(b).spellId(), chains.get(b).variant(), aliasB));
                        }
                    }
                }
            }
        }
    }

    /** Normalized full-string similarity (same normalization as chant matching). */
    private static float normalizedSimilarity(String a, String b) {
        String na = ChantEngine.normalize(a);
        String nb = ChantEngine.normalize(b);
        if (na.isEmpty() || nb.isEmpty()) return 0f;
        int dist = Levenshtein.distance(na, nb);
        return 1.0f - (float) dist / Math.max(na.length(), nb.length());
    }

    // ---- C-5: 禁咒字段组 ---------------------------------------------------

    private static void forbiddenGroup(java.util.Collection<Spell> spells, List<String> warnings) {
        for (Spell spell : spells) {
            boolean forbiddenDifficulty = spell.difficulty() >= FORBIDDEN_DIFFICULTY;
            boolean skipAllowed = spell.chantPolicy() == null || spell.chantPolicy().skipAllowed();
            if (forbiddenDifficulty && !spell.requiresLearning()) {
                warnings.add(String.format(Locale.ROOT,
                        "Forbidden-chant difficulty (%.1f) without requires_learning: %s",
                        spell.difficulty(), spell.id()));
            }
            if (forbiddenDifficulty && skipAllowed) {
                warnings.add(String.format(Locale.ROOT,
                        "Forbidden chant must set skip_allowed=false: %s", spell.id()));
            }
            if (!forbiddenDifficulty && !skipAllowed) {
                warnings.add(String.format(Locale.ROOT,
                        "skip_allowed=false but difficulty < %.1f (not a forbidden chant?): %s",
                        FORBIDDEN_DIFFICULTY, spell.id()));
            }
        }
    }

    // ---- C': IPA 覆盖率 ----------------------------------------------------

    private static void ipaCoverage(java.util.Collection<Spell> spells, List<String> warnings) {
        for (Spell spell : spells) {
            var pronunciation = spell.pronunciation();
            if (pronunciation == null) continue;
            int aliases = pronunciation.aliases().size();
            int templates = pronunciation.ipa().size();
            if (aliases > 0 && templates == 0) {
                warnings.add(String.format(Locale.ROOT,
                        "IPA coverage gap: %s has %d alias(es) but no IPA template",
                        spell.id(), aliases));
            }
        }
    }
}
