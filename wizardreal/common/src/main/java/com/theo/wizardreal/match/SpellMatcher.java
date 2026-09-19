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
 *
 * <p>TM-FIX port (issue #29 P6, IN-PRODUCTION as of 0.3.3 — the lab copy at
 * {@code ipa/match/TextMatcher.java} carried this rule AHEAD of production;
 * now synced, see build/accent_calibration/c1ca_report.md): score ties are
 * broken by LONGEST alias first — on sentence-length transcripts a short
 * alias (e.g. 圣光) can be contained inside another spell's longer alias
 * (e.g. 圣光矢), and the old "first in the candidate order wins" rule
 * misassigned the row; the longest alias is the most specific match. A full
 * tie (same score, same alias length) falls back to spell id lexicographic
 * order, smaller id first — matching the old first-in-vocab behavior on
 * id-sorted vocab files.
 */
public final class SpellMatcher {
    /**
     * 0.65 (was 0.8; the phonetic layer — pinyin/metaphone/skeleton — made
     * same-spell pairs score well above it): the engbench collision audit
     * measured the WORST cross-spell alias pair at 0.33 (en) / 0.20 (zh),
     * so 0.65 keeps a 2x margin over any wrong-spell confusion. Trade-off:
     * short fragments (3+ chars) of an alias now match — desirable for
     * half-spoken chants; per-spell {@code threshold} overrides remain
     * available for sensitive spells.
     */
    public static final float MATCH_THRESHOLD = 0.65f;

    private SpellMatcher() {}

    public record Match(Spell spell, float score) {}

    public static Match match(String heard) {
        return match(heard, SpellRegistry.all());
    }

    /** Candidate-trimmed match: only the given spells compete (language trim). */
    public static Match match(String heard, Iterable<Spell> candidates) {
        String text = normalize(heard);
        if (text.isEmpty() || "[unk]".equals(text)) return null;

        Spell bestSpell = null;
        float bestScore = 0f;
        int bestAliasLen = -1;
        for (Spell spell : candidates) {
            Pronunciation p = spell.pronunciation();
            for (String alias : p.aliases()) {
                String normAlias = normalize(alias);
                float score = scoreAlias(normAlias, text);
                if (score > bestScore) {
                    bestScore = score;
                    bestSpell = spell;
                    bestAliasLen = normAlias.length();
                } else if (score == bestScore && score > 0f && bestSpell != null) {
                    // Score tie: longest alias wins (most specific match);
                    // full tie -> spell id lexicographic order, smaller id
                    // first (TM-FIX, issue #29 P6).
                    int len = normAlias.length();
                    if (len > bestAliasLen
                            || (len == bestAliasLen && spell.id().compareTo(bestSpell.id()) < 0)) {
                        bestScore = score;
                        bestSpell = spell;
                        bestAliasLen = len;
                    }
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
        // ASR errors are phonetic: 换蛋/幻弹 share pinyin, falsome/falsum share
        // the consonant skeleton — character Levenshtein alone scores both at
        // 0.0-0.71. Take the best of orthographic and phonetic similarity.
        return Math.max(similarity(alias, text), Phonetics.score(alias, text));
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
