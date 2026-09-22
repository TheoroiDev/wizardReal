package com.theo.wizardreal.g2p;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Japanese kana -> Hepburn romaji (Tier-1 ja annotation layer, plan
 * docs/plans/chant_reading_annotation.md §4 — D3: Hepburn for players to read
 * aloud). Structure mirrors {@link KanaIpa}: hiragana table + katakana by
 * codepoint shift, strict fail-closed on anything unconvertible.
 *
 * <p>Output style (documented deviations from bare Hepburn, chosen for
 * readability and same-source alignment with the IPA templates):
 * <ul>
 *   <li><b>Mora-spaced</b> tokens ("a ne mo su", "shi ppū") — same mora
 *       segmentation as {@link KanaIpa}, avoids the classic n-boundary
 *       ambiguity of concatenated romaji (gurenno = gu-ren-no). Two coda-style
 *       exceptions: ん attaches to the previous token as its coda (にほん →
 *       "ni hon"; word-initial ん stands alone), and long vowels merge into
 *       macrons (below).</li>
 *   <li><b>Long vowels merge into macrons</b> (Hepburn ā ī ū ē ō): same-vowel
 *       doubling (おお → ō), お+う → ō, え+い → ē, and chōonpu ー lengthens the
 *       previous vowel. This makes the romaji one token where the IPA chain
 *       keeps two mora (ko u vs kō) — display-grade convention, the
 *       recognizer still scores the IPA templates.</li>
 *   <li><b>ん = always n</b> (modern Hepburn / waapuro; the classical m
 *       variant "shimbun" is NOT adopted — modern standard is n).</li>
 *   <li><b>ヲ = wo, ヂ = ji, ヅ = zu, ヴ = vu</b> (modern kana usage).</li>
 *   <li><b>Sokuon っ/ッ</b> doubles the next syllable's first consonant letter
 *       (った tta, っし sshi; ち-series doubles t per MLIT-style Hepburn:
 *       っち tchi, っちゃ tcha). Word-final っ renders as an apostrophe "'"
 *       (the established convention for terminal sokuon — no letter exists
 *       for a terminal glottal catch); before a vowel the mark is dropped
 *       (nothing to double, same rule as {@link KanaIpa}).</li>
 *   <li><b>Foreign morae</b> via small-kana composition: ティ ti, ディ di,
 *       トゥ tu, ドゥ du, ファ fi-family fa/fi/fe/fo, フュ fyu, ウィ wi,
 *       ウェ we, ウォ wo, クァ kwa, グァ gwa, ツァ tsa/tsi/tse/tso, シェ she,
 *       チェ che, ジェ je, イェ ye, ヴァ va/vi/ve/vo.</li>
 * </ul>
 *
 * <p>Excluded (strict ""): kanji (convert through {@link KanjiIpa} first),
 * half-width katakana (U+FF66-FF9F), ヷヸヹヺ (U+30F7-FA, above the shift
 * window), punctuation and any other character — all fail closed, no garbage
 * output. ー at word start or after a non-vowel token is strict ("ンー" is
 * not "n̄"), mirroring {@link KanaIpa}.
 *
 * <p>Pure JVM, no Minecraft types — unit-testable.
 */
public final class KanaRomaji {
    private KanaRomaji() {}

    private static final int HIRA_START = 0x3041;
    private static final int KATA_START = 0x30A1;
    private static final int KATA_END = 0x30F6; // ヷヸヹヺ (0x30F7-FA) excluded: see class doc
    private static final int SHIFT = KATA_START - HIRA_START;

    /** Hiragana -> plain romaji (no macrons; long vowels compose above). */
    private static final Map<String, String> BASE = new HashMap<>(Map.ofEntries(
            Map.entry("あ", "a"), Map.entry("い", "i"), Map.entry("う", "u"), Map.entry("え", "e"),
            Map.entry("お", "o"),
            Map.entry("か", "ka"), Map.entry("き", "ki"), Map.entry("く", "ku"), Map.entry("け", "ke"),
            Map.entry("こ", "ko"),
            Map.entry("が", "ga"), Map.entry("ぎ", "gi"), Map.entry("ぐ", "gu"), Map.entry("げ", "ge"),
            Map.entry("ご", "go"),
            Map.entry("さ", "sa"), Map.entry("し", "shi"), Map.entry("す", "su"), Map.entry("せ", "se"),
            Map.entry("そ", "so"),
            Map.entry("ざ", "za"), Map.entry("じ", "ji"), Map.entry("ず", "zu"), Map.entry("ぜ", "ze"),
            Map.entry("ぞ", "zo"),
            Map.entry("た", "ta"), Map.entry("ち", "chi"), Map.entry("つ", "tsu"), Map.entry("て", "te"),
            Map.entry("と", "to"),
            Map.entry("だ", "da"), Map.entry("ぢ", "ji"), Map.entry("づ", "zu"), Map.entry("で", "de"),
            Map.entry("ど", "do"),
            Map.entry("な", "na"), Map.entry("に", "ni"), Map.entry("ぬ", "nu"), Map.entry("ね", "ne"),
            Map.entry("の", "no"),
            Map.entry("は", "ha"), Map.entry("ひ", "hi"), Map.entry("ふ", "fu"), Map.entry("へ", "he"),
            Map.entry("ほ", "ho"),
            Map.entry("ば", "ba"), Map.entry("び", "bi"), Map.entry("ぶ", "bu"), Map.entry("べ", "be"),
            Map.entry("ぼ", "bo"),
            Map.entry("ぱ", "pa"), Map.entry("ぴ", "pi"), Map.entry("ぷ", "pu"), Map.entry("ぺ", "pe"),
            Map.entry("ぽ", "po"),
            Map.entry("ま", "ma"), Map.entry("み", "mi"), Map.entry("む", "mu"), Map.entry("め", "me"),
            Map.entry("も", "mo"),
            Map.entry("や", "ya"), Map.entry("ゆ", "yu"), Map.entry("よ", "yo"),
            Map.entry("ら", "ra"), Map.entry("り", "ri"), Map.entry("る", "ru"), Map.entry("れ", "re"),
            Map.entry("ろ", "ro"),
            Map.entry("わ", "wa"), Map.entry("ゐ", "wi"), Map.entry("ゑ", "we"), Map.entry("を", "wo"),
            // modern kana usage: を=wo, historical ゐ/ゑ=wi/we; ゔ=vu (D3 modern set)
            Map.entry("ゔ", "vu"),
            // small kana standalone fallback (ドゥ-style composition handles the
            // common cases; a small kana at word start or after a lone vowel
            // stands alone, mirroring KanaIpa)
            Map.entry("ぁ", "a"), Map.entry("ぃ", "i"), Map.entry("ぅ", "u"), Map.entry("ぇ", "e"),
            Map.entry("ぉ", "o"),
            Map.entry("ゃ", "ya"), Map.entry("ゅ", "yu"), Map.entry("ょ", "yo")));

    /** Vowel letters (incl. macron forms) a token may end with. */
    private static final String VOWELS = "aiueoāīūēō";
    /** Consonant letters that can start a syllable token. */
    private static final String CONSONANTS = "bcdfghjklmnpqrstvwxyz";

    /** Roman vowel -> its macron (long-vowel) form. */
    private static String macron(char v) {
        return switch (v) {
            case 'a' -> "ā";
            case 'i' -> "ī";
            case 'u' -> "ū";
            case 'e' -> "ē";
            case 'o' -> "ō";
            default -> String.valueOf(v);
        };
    }

    /** Macrons are letters too; map any vowel (plain or macron) to its base letter. */
    private static char plainBase(char c) {
        return switch (c) {
            case 'ā' -> 'a';
            case 'ī' -> 'i';
            case 'ū' -> 'u';
            case 'ē' -> 'e';
            case 'ō' -> 'o';
            default -> c;
        };
    }

    private static boolean isVowel(char c) {
        return VOWELS.indexOf(c) >= 0;
    }

    /** True when {@code lastVowel} (the vowel a token currently ends with,
     *  possibly already a macron) is continued by {@code next} into a long
     *  vowel: same-vowel doubling, お+う, or え+い. */
    private static boolean isLongVowel(char lastVowel, char next) {
        char base = plainBase(lastVowel);
        if (base == next) return true;
        return (base == 'o' && next == 'u') || (base == 'e' && next == 'i');
    }

    /** Strip trailing vowel letters (incl. macrons) from a token. */
    private static String stripVowels(String token) {
        int end = token.length();
        while (end > 0 && isVowel(token.charAt(end - 1))) end--;
        return token.substring(0, end);
    }

    /** Sokuon doubling: った -> tta, っし -> sshi; the ち-series doubles the
     *  affricate's stop component per MLIT-style Hepburn (っち -> tchi,
     *  っちゃ -> tcha); vowel-initial syllables have nothing to double. */
    private static String doubleOnset(String token) {
        if (token.isEmpty() || !isConsonant(token.charAt(0))) return token;
        if (token.startsWith("ch")) return "t" + token;
        return token.charAt(0) + token;
    }

    private static boolean isConsonant(char c) {
        return CONSONANTS.indexOf(c) >= 0;
    }

    /**
     * Convert a pure-kana word to mora-spaced Hepburn romaji.
     *
     * @return the romaji, or "" when any segment is unconvertible (strict:
     *         no garbage readings — convert kanji through {@link KanjiIpa}
     *         first and strip punctuation before calling).
     */
    public static String toRomaji(String word) {
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
                case "ん" -> {
                    // modern Hepburn: always n (no m variant); attaches to the
                    // previous token as the coda (にほん -> ni hon)
                    if (!out.isEmpty()) {
                        out.set(out.size() - 1, out.get(out.size() - 1) + "n");
                    } else {
                        out.add("n");
                    }
                }
                case "っ" -> geminate = true;
                case "ー" -> {
                    // chōonpu: lengthen the previous vowel; strict at word
                    // start or after a non-vowel token (ンー is not n̄)
                    if (out.isEmpty()) return "";
                    String prev = out.get(out.size() - 1);
                    if (prev.isEmpty()) return "";
                    char last = prev.charAt(prev.length() - 1);
                    if (!isVowel(last)) return "";
                    if (last < 0x0100) { // plain vowel -> macron; already-macron stays
                        out.set(out.size() - 1, stripVowels(prev) + macron(last));
                    }
                }
                default -> {
                    boolean smallVowel = ch.equals("ぁ") || ch.equals("ぃ") || ch.equals("ぅ")
                            || ch.equals("ぇ") || ch.equals("ぉ");
                    boolean smallGlide = ch.equals("ゃ") || ch.equals("ゅ") || ch.equals("ょ");
                    String token;
                    boolean standalone = false;
                    if (smallGlide) {
                        // きゃ kya / しゃ sha / ちゃ cha / じゃ ja: strip the
                        // previous token's vowel, append ya/yu/yo — EXCEPT the
                        // sibilant rows (sh/ch/j), where the i-glide merges
                        // into the sibilant (しゃ -> sha, っちゅ -> tchu),
                        // mirroring KanaIpa. After a lone vowel or word start
                        // both stand alone (あゃ -> a ya).
                        if (out.isEmpty() || out.get(out.size() - 1).length() == 1) {
                            token = BASE.get(ch);
                        } else {
                            String head = stripVowels(out.get(out.size() - 1));
                            boolean sibilant = head.endsWith("sh") || head.endsWith("ch")
                                    || head.endsWith("j");
                            token = head + switch (ch) {
                                case "ゃ" -> sibilant ? "a" : "ya";
                                case "ゅ" -> sibilant ? "u" : "yu";
                                default -> sibilant ? "o" : "yo";
                            };
                            out.remove(out.size() - 1);
                        }
                    } else if (smallVowel) {
                        // Foreign morae: ティ ti / ファ fa / ウィ wi / ヴァ va.
                        // Strip the previous token's vowel and append; u-row
                        // glide special cases: う->w, く->kw, ぐ->gw (クァ kwa).
                        // A previous token with no onset left (イェ) keeps both
                        // standalone, mirroring KanaIpa.
                        String head = out.isEmpty() ? "" : switch (out.get(out.size() - 1)) {
                            case "u" -> "w";
                            case "ku" -> "kw";
                            case "gu" -> "gw";
                            default -> stripVowels(out.get(out.size() - 1));
                        };
                        if (head.isEmpty()) {
                            token = BASE.get(ch);
                        } else {
                            token = head + smallVowelLetter(ch);
                            out.remove(out.size() - 1);
                        }
                    } else {
                        token = BASE.get(ch);
                        if (token == null) return ""; // unknown kana -> no draft
                    }
                    if (token == null) return "";
                    if (geminate) {
                        token = doubleOnset(token);
                        geminate = false;
                    }
                    // Long-vowel merge fires only on plain vowel kana
                    // (あいうえお) — same-vowel continuation (おお -> ō),
                    // お+う -> ō, え+い -> ē.
                    if (!standalone && (ch.equals("あ") || ch.equals("い") || ch.equals("う")
                            || ch.equals("え") || ch.equals("お"))) {
                        if (!out.isEmpty()) {
                            String prev = out.get(out.size() - 1);
                            char last = prev.charAt(prev.length() - 1);
                            if (isVowel(last) && isLongVowel(last, token.charAt(0))) {
                                if (last < 0x0100) {
                                    out.set(out.size() - 1, stripVowels(prev) + macron(last));
                                }
                                continue;
                            }
                        }
                    }
                    out.add(token);
                }
            }
        }
        if (geminate) {
            // Terminal sokuon: apostrophe (documented convention), glottal
            // catch with no letter; alone -> "'" as its own token.
            if (!out.isEmpty()) {
                out.set(out.size() - 1, out.get(out.size() - 1) + "'");
            } else {
                out.add("'");
            }
        }
        return String.join(" ", out);
    }

    private static String smallVowelLetter(String ch) {
        return switch (ch) {
            case "ぁ" -> "a";
            case "ぃ" -> "i";
            case "ぅ" -> "u";
            case "ぇ" -> "e";
            default -> "o";
        };
    }
}
