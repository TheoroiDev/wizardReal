package com.theo.wizardreal.match;

import com.theo.voicecast.api.IpaText;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.util.Levenshtein;

import java.util.List;

/**
 * Matches recognized IPA phoneme sequences against the {@code ipa()} templates
 * of registered spells (used by the IPA phoneme engine).
 *
 * <p>Both sides are normalized: diacritics/stress marks are stripped, and the
 * IPA string is split into per-phoneme tokens (multi-char affricates such as
 * tʃ/dʒ are kept together). Scoring is Levenshtein similarity on the token
 * sequences plus a bonus when a template appears as a contiguous subsequence
 * of the heard utterance. The per-spell effective threshold is
 * {@link Spell#threshold()} when overridden, else {@link #MATCH_THRESHOLD}.
 */
public final class PhonemeMatcher {
    public static final float MATCH_THRESHOLD = 0.6f;

    private PhonemeMatcher() {}

    public record Match(Spell spell, float score) {}

    public static Match match(List<String> heardIpa) {
        if (heardIpa == null || heardIpa.isEmpty()) return null;
        List<String> heard = normalizeTokens(heardIpa);
        if (heard.isEmpty()) return null;

        Spell bestSpell = null;
        float bestScore = 0f;
        for (Spell spell : SpellRegistry.all()) {
            for (String template : spell.pronunciation().ipa()) {
                List<String> target = tokenizeIpa(template);
                if (target.isEmpty()) continue;
                float score = score(heard, target);
                if (score > bestScore) {
                    bestScore = score;
                    bestSpell = spell;
                }
            }
        }
        if (bestSpell == null) return null;
        float threshold = bestSpell.threshold() >= 0 ? bestSpell.threshold() : MATCH_THRESHOLD;
        if (bestScore < threshold) return null;
        return new Match(bestSpell, bestScore);
    }

    private static float score(List<String> heard, List<String> target) {
        float sim = similarity(heard, target);
        if (containsSublist(heard, target)) {
            // exact template spoken as a contiguous chunk -> very strong match
            sim = Math.max(sim, 0.92f);
        }
        // Deletion-tolerant alignment: the engine systematically drops weak
        // syllable-final consonants (e.g. the dark L in "fulmen" -> [f uː m ʌ n],
        // see workspace-root docs/IPA识别问题.md). Allow target tokens to be skipped for free
        // while every heard token must still be aligned.
        sim = Math.max(sim, skipTargetSimilarity(heard, target));
        return sim;
    }

    /**
     * Similarity where skipping target tokens is free (their edit cost is not
     * counted), but every heard token costs 1 unless it matches. Normalized
     * against the heard length. Equals 1.0 when heard is a (fuzzy) subsequence
     * of target with zero substitutions.
     */
    static float skipTargetSimilarity(List<?> heard, List<?> target) {
        int m = heard.size();
        int n = target.size();
        if (m == 0) return 1f;
        if (n == 0) return 0f;
        // d[i][j] = min cost aligning heard[0..i) against a subsequence of target[0..j)
        int[] prev = new int[n + 1];
        int[] cur = new int[n + 1];
        for (int i = 1; i <= m; i++) {
            cur[0] = i; // every heard token must be consumed; target side empty
            for (int j = 1; j <= n; j++) {
                int sub = prev[j - 1] + (heard.get(i - 1).equals(target.get(j - 1)) ? 0 : 1);
                int skip = cur[j - 1]; // free skip of target[j-1]
                cur[j] = Math.min(sub, skip);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        int cost = prev[n];
        return 1f - (float) cost / m;
    }

    /** Normalize tokens already split by the engine (one phoneme per element). */
    static List<String> normalizeTokens(List<String> tokens) {
        return IpaText.normalizeTokens(tokens);
    }

    /** Split an IPA template string into phoneme tokens. */
    static List<String> tokenizeIpa(String ipa) {
        return IpaText.tokenize(ipa);
    }

    private static boolean containsSublist(List<?> hay, List<?> needle) {
        if (needle.isEmpty() || hay.size() < needle.size()) return false;
        outer:
        for (int start = 0; start <= hay.size() - needle.size(); start++) {
            for (int j = 0; j < needle.size(); j++) {
                if (!hay.get(start + j).equals(needle.get(j))) continue outer;
            }
            return true;
        }
        return false;
    }

    static float similarity(List<?> a, List<?> b) {
        int maxLen = Math.max(a.size(), b.size());
        if (maxLen == 0) return 1f;
        return 1.0f - (float) Levenshtein.distance(a, b) / maxLen;
    }
}

