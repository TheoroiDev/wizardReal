package com.theo.wizardreal.server;

import com.theo.voicecast.match.Levenshtein;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Word-by-word alignment for practice feedback (wizardReal#43 逐词对齐): maps
 * the current chant line's words onto what the player actually said, so the
 * HUD can highlight per-word hits instead of a whole-line verdict.
 *
 * <p>Pure and MC-free. Deliberately lightweight — this is FEEDBACK, not the
 * progression authority (that stays the ChantLineMatcher): a greedy in-order
 * walk over the utterance tokens with normalized equality plus a
 * Levenshtein-similarity fallback (voicecast's implementation, same math the
 * matcher tiers use) for inflections and ASR mishearings. CJK transcripts
 * arrive without word gaps, so a CJK line word hits by contiguous containment
 * in the normalized utterance instead of tokenization.
 */
public final class PracticeAligner {

    /** Similarity floor for the fuzzy word hit (tuned loose — feedback only). */
    static final float WORD_SIMILARITY_FLOOR = 0.75f;

    private static final Pattern LATIN = Pattern.compile("[\\p{IsLatin}]");
    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private PracticeAligner() {}

    /**
     * Align one line's words against the heard utterance.
     *
     * @param lineWords the display line split into words (client order)
     * @param heard     the raw utterance text ("" allowed)
     * @return per-word hit flags, same length as {@code lineWords}
     */
    public static boolean[] align(List<String> lineWords, String heard) {
        boolean[] hits = new boolean[lineWords.size()];
        if (heard == null || heard.isBlank() || lineWords.isEmpty()) return hits;

        boolean latinLine = lineWords.stream().allMatch(w -> LATIN.matcher(w).find());
        if (latinLine) {
            List<String> tokens = tokenize(heard);
            int cursor = 0; // in-order consumption
            for (int i = 0; i < lineWords.size(); i++) {
                String word = normalize(lineWords.get(i));
                if (word.isEmpty()) continue;
                cursor = consumeToken(tokens, cursor, word, hits, i);
            }
        } else {
            String haystack = normalize(heard);
            for (int i = 0; i < lineWords.size(); i++) {
                String word = normalize(lineWords.get(i));
                hits[i] = !word.isEmpty() && haystack.contains(word);
            }
        }
        return hits;
    }

    /** Normalize one word: lowercase, strip non-letter/digit decoration. */
    static String normalize(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static List<String> tokenize(String heard) {
        List<String> out = new ArrayList<>();
        for (String t : SPLIT.split(heard.toLowerCase(Locale.ROOT))) {
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Greedy in-order consumption: find the first unused token at/after
     *  {@code from} that hits {@code word}; consume through it. */
    private static int consumeToken(List<String> tokens, int from, String word, boolean[] hits, int i) {
        for (int t = from; t < tokens.size(); t++) {
            String token = tokens.get(t);
            if (token.equals(word) || similarity(word, token) >= WORD_SIMILARITY_FLOOR) {
                hits[i] = true;
                return t + 1;
            }
        }
        return from;
    }

    static float similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0f;
        int max = Math.max(a.length(), b.length());
        return 1f - Levenshtein.distance(a, b) / (float) max;
    }
}
