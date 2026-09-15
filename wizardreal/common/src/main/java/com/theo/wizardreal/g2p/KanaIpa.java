package com.theo.wizardreal.g2p;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Japanese kana -> espeak-style IPA (Tier-1 ja, docs/g2p/02 §2).
 *
 * <p>Hiragana table + katakana by codepoint shift; the vowel/consonant choices
 * follow the hand-curated ja templates already in the corpus (う=ɯ, え=ɛ,
 * を=wo, け=kɛ, つ=tsɯ, ら-column r=ɾ... e.g. アルカヌム -> a ɾɯ ka nɯ mɯ).
 * Sokuon っ/ッ geminates the next onset (empty-cluster safe: っ+vowel-initial
 * kana just drops the gemination mark); chōonpu ー lengthens the previous
 * vowel and is STRICT at word start or after a non-vowel token; ん = n.
 *
 * <p>Excluded (strict ""): kanji (docs C4 — needs a morphological analyzer),
 * half-width katakana (U+FF66-FF9F), ゝゞ゛゜ヽヾ, and ヷヸヹヺ (U+30F7-FA, above
 * the shift window) — all fail closed, no garbage output. ゔ/ヴ map to bɯ.
 *
 * <p>Kanji are NOT handled here (docs C4 — needs a morphological
 * analyzer; P0 scope is kana only).
 */
public final class KanaIpa {
    private KanaIpa() {}

    private static final int HIRA_START = 0x3041;
    private static final int KATA_START = 0x30A1;
    private static final int KATA_END = 0x30F6; // ヷヸヹヺ (0x30F7-FA) excluded: see class doc
    private static final int SHIFT = KATA_START - HIRA_START;

    private static final Map<String, String> BASE = new HashMap<>(Map.ofEntries(
            // a-wadan: vowel row follows the curated corpus style (u=ɯ, e=ɛ)
            Map.entry("あ", "a"), Map.entry("い", "i"), Map.entry("う", "ɯ"), Map.entry("え", "ɛ"),
            Map.entry("お", "o"),
            Map.entry("か", "ka"), Map.entry("き", "ki"), Map.entry("く", "kɯ"), Map.entry("け", "kɛ"),
            Map.entry("こ", "ko"),
            Map.entry("さ", "sa"), Map.entry("し", "ɕi"), Map.entry("す", "sɯ"), Map.entry("せ", "sɛ"),
            Map.entry("そ", "so"),
            Map.entry("た", "ta"), Map.entry("ち", "tɕi"), Map.entry("つ", "tsɯ"), Map.entry("て", "tɛ"),
            Map.entry("と", "to"),
            Map.entry("な", "na"), Map.entry("に", "ni"), Map.entry("ぬ", "nɯ"), Map.entry("ね", "nɛ"),
            Map.entry("の", "no"),
            Map.entry("は", "ha"), Map.entry("ひ", "çi"), Map.entry("ふ", "ɸɯ"), Map.entry("へ", "hɛ"),
            Map.entry("ほ", "ho"),
            Map.entry("ま", "ma"), Map.entry("み", "mi"), Map.entry("む", "mɯ"), Map.entry("め", "mɛ"),
            Map.entry("も", "mo"),
            Map.entry("や", "ja"), Map.entry("ゆ", "ju"), Map.entry("よ", "jo"),
            Map.entry("ら", "ɾa"), Map.entry("り", "ɾi"), Map.entry("る", "ɾɯ"), Map.entry("れ", "ɾɛ"),
            Map.entry("ろ", "ɾo"),
            Map.entry("わ", "wa"), Map.entry("ゐ", "wi"), Map.entry("ゑ", "we"), Map.entry("を", "wo"),
            // voiced
            Map.entry("が", "ga"), Map.entry("ぎ", "gi"), Map.entry("ぐ", "gɯ"), Map.entry("げ", "gɛ"),
            Map.entry("ご", "go"),
            Map.entry("ざ", "za"), Map.entry("じ", "dʑi"), Map.entry("ず", "zɯ"), Map.entry("ぜ", "zɛ"),
            Map.entry("ぞ", "zo"),
            Map.entry("だ", "da"), Map.entry("ぢ", "dʑi"), Map.entry("づ", "dzɯ"), Map.entry("で", "dɛ"),
            Map.entry("ど", "do"),
            Map.entry("ば", "ba"), Map.entry("び", "bi"), Map.entry("ぶ", "bɯ"), Map.entry("べ", "bɛ"),
            Map.entry("ぼ", "bo"),
            Map.entry("ぱ", "pa"), Map.entry("ぴ", "pi"), Map.entry("ぷ", "pɯ"), Map.entry("ぺ", "pɛ"),
            Map.entry("ぽ", "po"),
            // small kana ( standalone vowel / palatal glide )
            Map.entry("ぁ", "a"), Map.entry("ぃ", "i"), Map.entry("ぅ", "ɯ"), Map.entry("ぇ", "ɛ"),
            Map.entry("ぉ", "o"),
            Map.entry("ゃ", "ja"), Map.entry("ゅ", "ju"), Map.entry("ょ", "jo"),
            // ゔ (and ヴ via the katakana shift): modern v-line, mapped to b-series
            Map.entry("ゔ", "bɯ")));

    /** Latin-consonant prefixes used for gemination/palatalization logic. */
    private static final String CONSONANTS = "kgsztdnhbpmyɾwjçɸb";
    /** Vowel endings that a chōonpu may lengthen. */
    private static final String VOWELS = "aɛiouɯɪeɤ";

    public static String toIpa(String word) {
        if (word == null || word.isBlank()) return "";
        // Normalize katakana to hiragana codepoints for table lookup.
        List<Integer> cps = new ArrayList<>();
        for (int i = 0; i < word.length();) {
            int cp = word.codePointAt(i);
            if (cp >= KATA_START && cp <= KATA_END) cp -= SHIFT;
            cps.add(cp);
            i += Character.charCount(word.codePointAt(i));
        }
        List<String> out = new ArrayList<>();
        boolean geminate = false;
        for (int idx = 0; idx < cps.size(); idx++) {
            String ch = new String(Character.toChars(cps.get(idx)));
            switch (ch) {
                case "ん" -> out.add("n");
                case "っ" -> geminate = true;
                case "ー" -> {
                    // chōonpu: lengthen the previous vowel; strict at word
                    // start or after a non-vowel token (ンー is not nː)
                    if (out.isEmpty() || !endsWithVowel(out.get(out.size() - 1))) return "";
                    out.set(out.size() - 1, out.get(out.size() - 1) + "ː");
                }
                default -> {
                    String ipa = BASE.get(ch);
                    boolean smallVowel = ch.equals("ぁ") || ch.equals("ぃ") || ch.equals("ぅ")
                            || ch.equals("ぇ") || ch.equals("ぉ");
                    boolean smallGlide = ch.equals("ゃ") || ch.equals("ゅ") || ch.equals("ょ");
                    // Small vowel after a kana modifies its vowel (ドゥ -> dɯ);
                    // after a lone vowel it is kept standalone (あゃ -> a ja,
                    // R1 audit: the old code silently swallowed the "a").
                    if (smallVowel && !out.isEmpty()) {
                        String prev = out.remove(out.size() - 1);
                        String head = prev.replaceAll("[" + VOWELS + "]+$", "");
                        if (head.isEmpty()) {
                            out.add(prev);
                            out.add(ipa);
                        } else {
                            out.add(head + ipa);
                        }
                        geminate = false;
                        continue;
                    }
                    // Small ya/yu/yo after a consonant-final kana palatalize it
                    // (きゃ -> kja); after sibilant rows the i-glide merges into
                    // the sibilant (しゃ -> ɕa, ジョ -> dʑo); standalone = plain.
                    if (smallGlide && !out.isEmpty()) {
                        String prev = out.remove(out.size() - 1);
                        String vowel = ch.equals("ゃ") ? "a" : ch.equals("ゅ") ? "ɯ" : "o";
                        boolean sibilantI = prev.endsWith("ɕi") || prev.endsWith("dʑi")
                                || prev.endsWith("tɕi");
                        String head;
                        if (sibilantI) {
                            head = prev.substring(0, prev.length() - 1); // keep bare sibilant
                        } else {
                            head = prev.length() > 1 && CONSONANTS.indexOf(prev.charAt(0)) >= 0
                                    ? prev.substring(0, 1) : "";
                            vowel = ch.equals("ゃ") ? "ja" : ch.equals("ゅ") ? "ju" : "jo";
                        }
                        out.add(head + vowel);
                        geminate = false;
                        continue;
                    }
                    if (ipa == null) {
                        return ""; // unknown kana -> no draft
                    }
                    if (geminate) {
                        // Duplicate the full onset consonant cluster: っち -> tɕ tɕi.
                        // After a vowel-initial kana (っあ) there is nothing to
                        // double - the gemination mark is simply dropped.
                        String cluster = ipa.replaceAll("[" + VOWELS + "].*$", "");
                        if (!cluster.isEmpty()) out.add(cluster);
                        geminate = false;
                    }
                    out.add(ipa);
                }
            }
        }
        return String.join(" ", out);
    }

    private static boolean endsWithVowel(String token) {
        return !token.isEmpty() && VOWELS.indexOf(token.charAt(token.length() - 1)) >= 0;
    }
}
