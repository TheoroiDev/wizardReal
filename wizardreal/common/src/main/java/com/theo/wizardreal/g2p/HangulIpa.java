package com.theo.wizardreal.g2p;

import java.util.ArrayList;
import java.util.List;

/**
 * Korean Hangul -> IPA (Tier-1 ko, docs/g2p/02 §2). Pure Unicode block math:
 * syllable = AC00 + (initial*21 + medial)*28 + final, decomposed into jamo,
 * jamo -> IPA by direct mapping (no liaison/tone rules at P0, per docs).
 */
public final class HangulIpa {
    private HangulIpa() {}

    // Canonical jamo order per the Unicode syllable formula (KS X 1021),
    // verified against HANGUL SYLLABLE names: GA GGA NA DA DDA RA MA BA BBA
    // SA SSA (vowel) JA JJA CA KA TA PA HA.
    private static final String[] ONSET = {
            "k", "k", "n", "t", "t", "l", "m", "p", "p", "s", "s", "", "tɕ", "tɕ", "tɕʰ",
            "kʰ", "tʰ", "pʰ", "h"};
    // GA GAE GYA GYAE GEO GE GYEO YE GO GWA GWAE GOE GYO GU GWEO GWE WI GYU EU GYI GI
    private static final String[] JUNGSEONG = {
            "a", "ɛ", "ja", "jɛ", "ʌ", "e", "jʌ", "je", "o", "wa", "wɛ", "we", "jo", "u",
            "wʌ", "we", "wi", "ju", "ɯ", "ɰi", "i"}; // ㅢ = ɰi (simplified away only in modern morphs; espeak keeps the glide)
    // (none) GAG GAGG GAGS GAN GANJ GANH GAD GAL GALG GALM GALB GALS GALT GALP GALH
    // GAM GAB GABS GAS GASS GANG GAJ GAC GAK GAT GAP GAH
    private static final String[] CODA = {
            "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "p", "l", "l", "p", "l", "m",
            "p", "p", "t", "t", "ŋ", "t", "t", "k", "t", "p", "t"};

    public static String toIpa(String word) {
        if (word == null || word.isBlank()) return "";
        List<String> out = new ArrayList<>();
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            if (ch >= 0xAC00 && ch <= 0xD7A3) {
                int offset = ch - 0xAC00;
                int onsetIdx = offset / (21 * 28);
                int medialIdx = (offset % (21 * 28)) / 28;
                int codaIdx = offset % 28;
                StringBuilder sb = new StringBuilder();
                String onset = ONSET[onsetIdx];
                // Compact per-syllable token (han, kɯl) — matches the zh
                // syllable-token style the phoneme matcher expects.
                sb.append(onset).append(JUNGSEONG[medialIdx]);
                if (codaIdx > 0) sb.append(CODA[codaIdx - 1]);
                out.add(sb.toString());
            } else {
                return ""; // non-syllabic jamo etc. -> no draft (spaces are handled by the caller's segmentation)
            }
        }
        return String.join(" ", out);
    }
}
