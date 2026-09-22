package com.theo.wizardreal.g2p;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mandarin pinyin -> espeak-style IPA (Tier-1 zh, docs/g2p/02 §2).
 *
 * <p>Hanzi -> toneless pinyin comes from the embedded data table
 * ({@code assets/wizardreal/g2p_pinyin.tsv}, generated once from the
 * MIT-licensed pypinyin data, default reading per char; {@code v} encodes ü).
 * The syllable -> IPA step composes an initial (onset) with a final (rime)
 * table — the mapping style follows the hand-curated templates already in the
 * spell corpus (e.g. 熔甲 rongjia -> ʐʊŋ tɕja, 屏障 pingzhang -> pʰɪŋ ʈʂɑŋ,
 * 奥术 aoshu -> aʊ ʂu). Tone is dropped (templates are stress/tone-free, like
 * every curated template in the corpus).
 *
 * <p>Since catalog v4 (chant annotation layer, plan
 * docs/plans/chant_reading_annotation.md §10) the table also carries a tone
 * digit column (1-5, 5 = neutral): {@link #toned} exposes the TONE3 form
 * ("zhen1") for the display pinyin composer {@link PinyinTone}. The toneless
 * column and the IPA chain are byte-identical to the pre-v4 table — the
 * recognition path is untouched.
 */
public final class PinyinIpa {
    private PinyinIpa() {}

    private static final String TABLE = "/assets/wizardreal/g2p_pinyin.tsv";
    private static volatile Map<String, String> hanziToPinyin;
    private static volatile Map<String, String> hanziToTone3;

    /** Longest-first initials (zh/ch/sh must win over single letters). */
    private static final List<String> INITIALS = List.of(
            "zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k",
            "h", "j", "q", "x", "r", "z", "c", "s");
    private static final Map<String, String> INITIAL_IPA = Map.ofEntries(
            Map.entry("b", "p"), Map.entry("p", "pʰ"), Map.entry("m", "m"), Map.entry("f", "f"),
            Map.entry("d", "t"), Map.entry("t", "tʰ"), Map.entry("n", "n"), Map.entry("l", "l"),
            Map.entry("g", "k"), Map.entry("k", "kʰ"), Map.entry("h", "x"),
            Map.entry("j", "tɕ"), Map.entry("q", "tɕʰ"), Map.entry("x", "ɕ"),
            Map.entry("zh", "ʈʂ"), Map.entry("ch", "ʈʂʰ"), Map.entry("sh", "ʂ"), Map.entry("r", "ʐ"),
            Map.entry("z", "ts"), Map.entry("c", "tsʰ"), Map.entry("s", "s"));

    /** Rimes in curated espeak style. "i" resolves by onset, "u" by onset (ü after j/q/x). */
    private static final Map<String, String> FINAL_IPA = new HashMap<>(Map.ofEntries(
            // "o" is onset-conditional (哦 standalone = o; bo/po/mo/fo o=uo -> wɔ): see rimeIpa
            Map.entry("a", "a"), Map.entry("e", "ɤ"), Map.entry("er", "ɚ"),
            Map.entry("ai", "aɪ"), Map.entry("ei", "eɪ"), Map.entry("ao", "aʊ"), Map.entry("ou", "oʊ"),
            Map.entry("an", "an"), Map.entry("en", "ən"), Map.entry("ang", "ɑŋ"), Map.entry("eng", "əŋ"),
            Map.entry("ong", "ʊŋ"),
            Map.entry("ia", "ja"), Map.entry("ie", "jɛ"), Map.entry("iao", "jaʊ"), Map.entry("iu", "joʊ"),
            Map.entry("io", "jo"),
            Map.entry("ye", "yɛ"), Map.entry("yan", "yan"), Map.entry("yn", "yn"),
            Map.entry("ian", "jɛn"), Map.entry("in", "in"), Map.entry("iang", "jɑŋ"), Map.entry("ing", "ɪŋ"),
            Map.entry("iong", "jʊŋ"),
            // l/n + ü-family: j-glide (l/n are non-palatal; ɥ would read palatalized)
            Map.entry("ua", "wa"), Map.entry("uo", "wɔ"), Map.entry("uai", "waɪ"), Map.entry("ui", "weɪ"),
            Map.entry("uan", "wan"), Map.entry("un", "wən"), Map.entry("uang", "wɑŋ"), Map.entry("ueng", "wəŋ"),
            Map.entry("ve", "ɥɛ"), Map.entry("van", "ɥɛn"), Map.entry("vn", "yn")));
    // ü-rimes after NON-palatal onsets use the j-glide (略 lve -> lyɛ, 虐 nve
    // -> nyɛ); ɥ is a palatal glide and only fits tɕ/ɕ/ɕʰ onsets (R1 audit #6).

    /** y-/w- orthographic syllables mapped to their underlying rime. */
    private static final Map<String, String> Y_W = new HashMap<>(Map.ofEntries(
            Map.entry("yi", "i"), Map.entry("ya", "ia"), Map.entry("ye", "ie"), Map.entry("yao", "iao"),
            Map.entry("you", "iu"), Map.entry("yan", "ian"), Map.entry("yin", "in"), Map.entry("yang", "iang"),
            Map.entry("ying", "ing"), Map.entry("yong", "iong"), Map.entry("yu", "v"), Map.entry("yue", "ve"),
            Map.entry("yuan", "van"), Map.entry("yun", "vn"), Map.entry("yo", "io"),
            Map.entry("wu", "u"), Map.entry("wa", "ua"), Map.entry("wo", "uo"), Map.entry("wai", "uai"),
            Map.entry("wei", "ui"), Map.entry("wan", "uan"), Map.entry("wen", "un"), Map.entry("wang", "uang"),
            Map.entry("weng", "ueng")));

    /** Word-level entry: every character must be in the data table (strict —
     *  punctuation, Latin letters or any unknown han produce no draft). */
    public static String toIpa(String word) {
        if (word == null || word.isBlank()) return "";
        Map<String, String> table = table();
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < word.length(); i++) {
            String py = table.get(word.substring(i, i + 1));
            if (py == null) return ""; // unknown char -> no draft (strict)
            String ipa = syllable(py);
            if (ipa == null) return "";
            out.add(ipa);
        }
        return String.join(" ", out);
    }

    /** One toneless pinyin syllable -> IPA; {@code null} when unmappable. */
    static String syllable(String py) {
        String s = py.toLowerCase(Locale.ROOT);
        String rime = Y_W.get(s);
        String onset = "";
        if (rime == null) {
            String initial = "";
            for (String cand : INITIALS) {
                if (s.startsWith(cand)) {
                    initial = cand;
                    break;
                }
            }
            rime = s.substring(initial.length());
            onset = INITIAL_IPA.getOrDefault(initial, "");
            // Onset-conditioned spellings: j/q/x are always palatal + ü-family
            // (the data table uses standard orthography: ju=jü, jun=jün,
            // jue=jüe, quan=qüan...).
            boolean palatal = initial.equals("j") || initial.equals("q") || initial.equals("x");
            if (palatal && rime.equals("u")) rime = "v";
            if (palatal && (rime.equals("un") || rime.equals("uan") || rime.equals("ue"))) {
                rime = "v" + rime.substring(1);
            }
            if (!palatal && !onset.isEmpty()
                    && (rime.equals("ve") || rime.equals("van") || rime.equals("vn"))) {
                // l/n + ü-family: ü as the MAIN vowel (lyɛ/nyɛ), no palatal
                // ɥ glide - that reads palatalized (R1 audit #6).
                rime = "y" + rime.substring(1);
            }
        }
        if (rime.isEmpty()) {
            // Syllabic nasal alone (嗯 n, 呣 m): the pinyin IS the consonant -
            // emit it as the whole syllable token (R1 audit #4).
            return onset.isEmpty() ? null : onset;
        }
        String rimeIpa = rimeIpa(rime, onset);
        if (rimeIpa == null) return null;
        return onset + rimeIpa;
    }

    private static String rimeIpa(String rime, String onset) {
        String mapped = FINAL_IPA.get(rime);
        if (mapped != null) return mapped;
        switch (rime) {
            case "i": // plain i after palatals/no onset; apical after retroflex/sibilant
                boolean retro = onset.equals("ʈʂ") || onset.equals("ʈʂʰ") || onset.equals("ʂ") || onset.equals("ʐ")
                        || onset.equals("ts") || onset.equals("tsʰ") || onset.equals("s");
                return retro ? "ɨ" : "i";
            case "u": return "u";
            case "v": return "y";
            case "o": return onset.isEmpty() ? "o" : "wɔ"; // 哦 standalone = o; bo/po/mo/fo o=uo 合写 (R1 #5)
            default: return null;
        }
    }

    private static Map<String, String> table() {
        load();
        return hanziToPinyin;
    }

    /** Public hanzi -> toneless pinyin query (annotation layer; same data the
     *  IPA chain uses). {@code null} when the char is not in the table. */
    public static String toneless(String hanzi) {
        if (hanzi == null || hanzi.length() != 1) return null;
        return table().get(hanzi);
    }

    /** Public hanzi -> TONE3 pinyin with tone digit ("zhen1", neutral = 5),
     *  for {@link PinyinTone}. {@code null} when the char is not in the table
     *  or the row predates the tone column. */
    public static String toned(String hanzi) {
        if (hanzi == null || hanzi.length() != 1) return null;
        load();
        return hanziToTone3.get(hanzi);
    }

    private static void load() {
        Map<String, String> t = hanziToPinyin;
        if (t != null) return;
        synchronized (PinyinIpa.class) {
            if (hanziToPinyin != null) return;
            Map<String, String> out = new HashMap<>();
            Map<String, String> tonedOut = new HashMap<>();
            try (InputStream in = PinyinIpa.class.getResourceAsStream(TABLE)) {
                if (in == null) throw new IllegalStateException("pinyin table missing: " + TABLE);
                try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.isBlank() || line.startsWith("#")) continue;
                        String[] cols = line.split("\t");
                        if (cols.length < 2 || cols[0].isEmpty()) continue;
                        out.putIfAbsent(cols[0], cols[1]);
                        if (cols.length >= 3) {
                            // TONE3 form: base + digit (tone 5 = neutral, kept explicit)
                            String digit = cols[2].isEmpty() ? "5" : cols[2];
                            if (digit.charAt(0) < '1' || digit.charAt(0) > '5') digit = "5";
                            tonedOut.putIfAbsent(cols[0], cols[1] + digit);
                        }
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("failed to load " + TABLE, e);
            }
            hanziToPinyin = Map.copyOf(out);
            hanziToTone3 = Map.copyOf(tonedOut);
        }
    }
}
