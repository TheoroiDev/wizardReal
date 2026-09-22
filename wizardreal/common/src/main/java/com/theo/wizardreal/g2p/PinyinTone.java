package com.theo.wizardreal.g2p;

import java.util.Locale;

/**
 * Pinyin tone digit -> tone-marked display form (调号组合器, plan
 * docs/plans/chant_reading_annotation.md §10 — D8: toned pinyin, citation
 * tones, no sandhi in v1).
 *
 * <p>Input is the TONE3 form the data table carries ("zhen1"; neutral tone =
 * digit 5, bare syllable also accepted). Output is the standard
 * tone-marked orthography ({@code marked}) or the digit form for the numeric
 * fallback style ({@code numeric}). ü is written as ü in both (the table's
 * {@code v} encoding is internal).
 *
 * <p>Mark placement = textbook rules, in priority order:
 * <ol>
 *   <li>mark {@code a} (present in the syllable — 好 hǎo, 想 xiǎng);</li>
 *   <li>else mark {@code o} (欧 ōu, 多 duō, 用 yōng);</li>
 *   <li>else mark {@code e} (累 lèi, 月 yuè);</li>
 *   <li>else if i/u/ü are present, mark the LAST one (流 liǔ, 贵 guì —
 *       标后 rule);</li>
 *   <li>else mark the single remaining vowel (一 yī, 五 wǔ, 律 lǜ).</li>
 * </ol>
 * Syllabic m/n/ê (嗯 ń, 呣 ḿ) and any vowel-less leftover get a COMBINING
 * tone mark (U+0300/0301/0304/030C) — display-grade composites. Tone 5
 * (neutral) / bare syllables / unparseable input: plain v->ü form, no mark.
 */
public final class PinyinTone {
    private PinyinTone() {}

    /** index = tone digit - 1, per vowel (precomposed where Unicode has one). */
    private static final String[] A = {"ā", "á", "ǎ", "à"};
    private static final String[] O = {"ō", "ó", "ǒ", "ò"};
    private static final String[] E = {"ē", "é", "ě", "è"};
    private static final String[] I = {"ī", "í", "ǐ", "ì"};
    private static final String[] U = {"ū", "ú", "ǔ", "ù"};
    private static final String[] V = {"ǖ", "ǘ", "ǚ", "ǜ"};
    /** Combining marks for syllabic m/n/ê and unprecomposed leftovers. */
    private static final String[] COMBINING = {"\u0304", "\u0301", "\u030C", "\u0300"};

    /**
     * Tone-marked pinyin ("zhen1" -> "zhēn", "lv4" -> "lǜ", "zhen5" ->
     * "zhen"). Unparseable input returns the plain v->ü form unchanged.
     */
    public static String marked(String tone3) {
        return compose(tone3, true);
    }

    /**
     * Numeric (digit) pinyin for the fallback display style ("zhen1" ->
     * "zhen1", "lv4" -> "lü4"; neutral "zhen5" -> "zhen"). Bare syllables
     * pass through with v->ü.
     */
    public static String numeric(String tone3) {
        return compose(tone3, false);
    }

    private static String compose(String tone3, boolean mark) {
        if (tone3 == null || tone3.isEmpty()) return tone3 == null ? "" : tone3;
        String s = tone3.trim().toLowerCase(Locale.ROOT);
        int tone = 0; // 1-4 mark; 0 = none (5 / unknown / absent)
        if (s.length() > 1) {
            char last = s.charAt(s.length() - 1);
            if (last >= '1' && last <= '5') {
                if (last <= '4') tone = last - '0';
                s = s.substring(0, s.length() - 1);
            }
        }
        String base = s.replace('v', 'ü');
        if (tone == 0) return base;
        return mark ? markVowel(base, tone) : base + tone;
    }

    private static String markVowel(String base, int tone) {
        int a = base.indexOf('a');
        if (a >= 0) return base.substring(0, a) + A[tone - 1] + base.substring(a + 1);
        int o = base.indexOf('o');
        if (o >= 0) return base.substring(0, o) + O[tone - 1] + base.substring(o + 1);
        int e = base.indexOf('e');
        if (e >= 0) return base.substring(0, e) + E[tone - 1] + base.substring(e + 1);
        // 标后 rule: i/u/ü all present-or-any -> mark the LAST one
        // (liu -> liǔ, gui -> guì); otherwise the single vowel that exists.
        int i = base.lastIndexOf('i');
        int u = base.lastIndexOf('u');
        int v = base.lastIndexOf('ü');
        int last = Math.max(i, Math.max(u, v));
        if (last < 0) {
            // syllabic m/n/ê or vowel-less: combining mark over the last char
            if (base.isEmpty()) return base;
            return base.substring(0, base.length() - 1)
                    + base.charAt(base.length() - 1) + COMBINING[tone - 1];
        }
        String marked = switch (base.charAt(last)) {
            case 'i' -> I[tone - 1];
            case 'u' -> U[tone - 1];
            default -> V[tone - 1];
        };
        return base.substring(0, last) + marked + base.substring(last + 1);
    }
}
