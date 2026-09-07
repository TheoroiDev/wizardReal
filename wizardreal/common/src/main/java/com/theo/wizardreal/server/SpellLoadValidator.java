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
 * {@code SpellDefinition.expandLanguageKeyedChants}. Public so the
 * shipped-resource test can run the same lint over the datapack JSONs
 * (generator regression guard).
 */
public final class SpellLoadValidator {
    /** L1 pairs closer than this (normalized similarity) trigger a warning. */
    public static final float L1_DISTINCT_THRESHOLD = 0.75f;
    /** difficulty at/above this marks a forbidden chant (禁咒). */
    public static final float FORBIDDEN_DIFFICULTY = 2.0f;

    private SpellLoadValidator() {}

    public static List<String> validate(java.util.Collection<Spell> spells) {
        List<String> warnings = new ArrayList<>();
        l1Distinctness(spells, warnings);
        forbiddenGroup(spells, warnings);
        ipaCoverage(spells, warnings);
        stageLadder(spells, warnings);
        return warnings;
    }

    // ---- magic_eco 03: 阶梯校验 ---------------------------------------------

    /** chant_stages must ascend by after_lines; a stage beyond the chant's line
     *  count is unreachable (generator/authoring bug). */
    private static void stageLadder(java.util.Collection<Spell> spells, List<String> warnings) {
        for (Spell spell : spells) {
            var stages = spell.chantStages();
            if (stages.isEmpty()) continue;
            int prev = 0;
            for (int i = 0; i < stages.size(); i++) {
                var stage = stages.get(i);
                if (stage.afterLines() <= prev) {
                    warnings.add(String.format(Locale.ROOT,
                            "chant_stages not ascending by after_lines (%d then %d): %s[%d]",
                            prev, stage.afterLines(), spell.id(), i));
                }
                prev = Math.max(prev, stage.afterLines());
                if (!spell.chants().isEmpty()) {
                    int maxLines = 0;
                    for (Chant chant : spell.chants()) {
                        maxLines = Math.max(maxLines, chant.lines().size());
                    }
                    if (stage.afterLines() > maxLines) {
                        warnings.add(String.format(Locale.ROOT,
                                "chant_stages[%d] after_lines=%d exceeds the longest chant (%d lines): %s",
                                i, stage.afterLines(), maxLines, spell.id()));
                    }
                }
            }
        }
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
                    if (chains.get(a).spellId().equals(chains.get(b).spellId())) {
                        // 0.4.0 language-keyed format guarantees all variants of one
                        // spell share the trigger line — same-spell pairs are by design.
                        continue;
                    }
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

    /**
     * Similarity in the SAME terms the lenient gate ({@link ChantEngine#lineMatches})
     * accepts: for multi-word lines the symmetric token-coverage ratio (either
     * direction reaching the threshold means one utterance matches both lines);
     * for single-word lines the whole-string Levenshtein ratio. Whole-string
     * Levenshtein on multi-word template lines would flag skeleton-sharing
     * couplets the matcher itself never confuses.
     */
    private static float normalizedSimilarity(String a, String b) {
        String na = ChantEngine.normalize(a);
        String nb = ChantEngine.normalize(b);
        if (na.isEmpty() || nb.isEmpty()) return 0f;
        String[] ta = na.split(" ");
        String[] tb = nb.split(" ");
        if (ta.length >= 2 && tb.length >= 2) {
            return Math.max(tokenCoverage(ta, nb), tokenCoverage(tb, na));
        }
        int dist = Levenshtein.distance(na, nb);
        return 1.0f - (float) dist / Math.max(na.length(), nb.length());
    }

    /** Fraction of {@code tokens} found in {@code text} (ChantEngine.looseText rule). */
    private static float tokenCoverage(String[] tokens, String text) {
        int hit = 0;
        for (String token : tokens) {
            if (!token.isEmpty() && text.contains(token)) hit++;
        }
        return (float) hit / tokens.length;
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
