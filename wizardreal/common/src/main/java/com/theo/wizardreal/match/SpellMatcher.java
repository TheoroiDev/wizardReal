package com.theo.wizardreal.match;

import com.theo.voicecast.api.Pronunciation;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.util.Levenshtein;

import java.util.Locale;

/**
 * Matches recognized speech text against spell aliases.
 *
 * <p>Strategy (best score wins, must be >= the spell's effective threshold —
 * {@link Spell#threshold()} override, else {@link #MATCH_THRESHOLD}):
 * <ol>
 *   <li>normalized alias equals normalized utterance ...... 1.0</li>
 *   <li>multi-word alias appears inside the utterance ...... 0.95</li>
 *   <li>single-word alias appears as a whole word .......... 0.9</li>
 *   <li>Levenshtein similarity on the full strings ......... 0..1</li>
 * </ol>
 */
public final class SpellMatcher {
    public static final float MATCH_THRESHOLD = 0.8f;

    private SpellMatcher() {}

    public record Match(Spell spell, float score) {}

    public static Match match(String heard) {
        String text = normalize(heard);
        if (text.isEmpty() || "[unk]".equals(text)) return null;

        Spell bestSpell = null;
        float bestScore = 0f;
        for (Spell spell : SpellRegistry.all()) {
            Pronunciation p = spell.pronunciation();
            for (String alias : p.aliases()) {
                float score = scoreAlias(normalize(alias), text);
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

    private static float scoreAlias(String alias, String text) {
        if (alias.isEmpty()) return 0f;
        if (alias.equals(text)) return 1.0f;
        boolean multiWord = alias.indexOf(' ') >= 0;
        if (multiWord) {
            if (text.contains(alias)) return 0.95f;
        } else if (containsWord(text, alias)) {
            return 0.9f;
        }
        return similarity(alias, text);
    }

    static String normalize(String s) {
        if (s == null) return "";
        // \p{L}\p{N} keep letters/digits of ALL scripts, so CJK aliases
        // (e.g. "爆裂") survive normalization.
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}'\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsWord(String haystack, String word) {
        int idx = haystack.indexOf(word);
        while (idx >= 0) {
            boolean beforeOk = idx == 0 || isBoundary(haystack.charAt(idx - 1));
            int end = idx + word.length();
            boolean afterOk = end == haystack.length() || isBoundary(haystack.charAt(end));
            if (beforeOk && afterOk) return true;
            idx = haystack.indexOf(word, idx + 1);
        }
        return false;
    }

    /**
     * A word boundary is whitespace or any CJK-range character: after
     * normalization the text is a mix of latin words and CJK runs, and a CJK
     * alias must count as a whole "word" when directly adjacent to other CJK
     * characters is NOT required (e.g. 火球爆裂啊 contains 爆裂).
     */
    private static boolean isBoundary(char c) {
        return c <= ' ' || c >= 0x2E80;
    }

    /** Levenshtein-based similarity in [0,1], comparing against the longest string. */
    static float similarity(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1f;
        return 1.0f - (float) Levenshtein.distance(a, b) / maxLen;
    }
}

