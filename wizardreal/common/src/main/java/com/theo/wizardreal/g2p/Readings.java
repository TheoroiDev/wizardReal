package com.theo.wizardreal.g2p;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chant-line reading derivation for the annotation layer (catalog v4, plan
 * docs/plans/chant_reading_annotation.md §4): line text + language bucket ->
 * a {@code readings} map with the FIXED key set {@code pinyin / romaji / ipa}
 * (keys absent when not derivable — a blank map is a valid result).
 *
 * <p>Per language bucket (D1/D3/D6; en/ko/neutral stay empty — D5):
 * <ul>
 *   <li><b>zh</b>: {@code pinyin} = toned citation pinyin per hanzi
 *       ({@link PinyinIpa#toned} + {@link PinyinTone#marked}); {@code ipa} =
 *       the line's hand-curated template when present, else the G2P draft.</li>
 *   <li><b>ja</b>: {@code romaji} = kanji phrases through {@link KanjiIpa}
 *       then {@link KanaRomaji} (pure-kana lines pass straight through);
 *       {@code ipa} = hand-curated first, else the G2P draft.</li>
 * </ul>
 *
 * <p>Fail-closed, same discipline as {@link G2p}: any unconvertible letter
 * (unknown hanzi, kanji left after the KanjiIpa pass, latin in a non-latin
 * bucket) voids the whole KEY — no partial or invented readings. Punctuation
 * and whitespace are SEPARATORS, dropped rather than fatal: the recognition
 * templates transcribe the same syllables with no pause tokens, so dropping
 * keeps the reading same-source with the hand-curated IPA (e.g. "アクア・ウィタエ"
 * -> "a kɯ a ɯ i ta ɛ" exactly). Empty/blank text yields an empty map. The
 * hand-curated {@code ipa} wins whenever the line carries one (curated-wins,
 * mirrors {@link G2p#tier0Dictionary}).
 *
 * <p>Pure JVM, no Minecraft types — unit-testable. Derivation happens ONCE
 * per line at server-side catalog build time; clients only render strings.
 */
public final class Readings {
    private Readings() {}

    /** Fixed readings key set. */
    public static final String PINYIN = "pinyin";
    public static final String ROMAJI = "romaji";
    public static final String IPA = "ipa";

    /**
     * Pinyin display style for the {@code pinyin} key (D8): symbol tone marks
     * ({@code zhēn}, the default pending the R-B unifont spike) or the digit
     * fallback ({@code zhen1}, always renderable). Applies ONLY to pinyin —
     * romaji and IPA are tone-free transcriptions and never change.
     */
    public enum PinyinStyle {
        /** Tone-marked pinyin via {@link PinyinTone#marked} (default). */
        MARKS,
        /** Digit (TONE3-style) pinyin via {@link PinyinTone#numeric}. */
        NUMBERS
    }

    /** Derive with the default pinyin style ({@link PinyinStyle#MARKS}). */
    public static Map<String, String> derive(String text, String lang, List<String> handIpa) {
        return derive(text, lang, handIpa, PinyinStyle.MARKS);
    }

    /**
     * Derive the readings map for one chant line (or trigger word).
     *
     * @param text    the line text (display anchor = the line's first alias,
     *                same value the recognizer scores against)
     * @param lang    language bucket key ("zh"/"ja"; other buckets -> empty)
     * @param handIpa the line's hand-curated IPA templates (may be empty;
     *                first entry wins when present)
     * @param style   pinyin display style (D8); romaji/ipa unaffected
     * @return insertion-ordered readings (pinyin, romaji, ipa — only present
     *         keys), never {@code null}; empty for unannotated buckets
     */
    public static Map<String, String> derive(String text, String lang, List<String> handIpa, PinyinStyle style) {
        Map<String, String> out = new LinkedHashMap<>();
        if (text == null || text.isBlank() || lang == null) return out;
        String bucket = lang.trim().toLowerCase(Locale.ROOT);
        switch (bucket) {
            case "zh" -> {
                String pinyin = zhPinyin(text, style);
                if (!pinyin.isEmpty()) out.put(PINYIN, pinyin);
                String ipa = ipa(text, handIpa, "zh");
                if (!ipa.isEmpty()) out.put(IPA, ipa);
            }
            case "ja" -> {
                String romaji = jaRomaji(text);
                if (!romaji.isEmpty()) out.put(ROMAJI, romaji);
                String ipa = ipa(text, handIpa, "ja");
                if (!ipa.isEmpty()) out.put(IPA, ipa);
            }
            default -> {
                // en / ko / neutral: readings empty this batch (D5/D9)
            }
        }
        return out;
    }

    /**
     * zh: every LETTER must be a table hanzi; punctuation and whitespace are
     * separators (dropped — the recognition templates transcribe the same
     * syllables with no pause tokens, so dropping keeps the reading
     * same-source with the hand-curated IPA). Any letter that is not a table
     * hanzi (latin, foreign scripts) voids the key. One token per char.
     */
    private static String zhPinyin(String text, PinyinStyle style) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String hanzi = text.substring(i, i + 1);
            if (!Character.isLetter(hanzi.charAt(0))) continue; // separator
            String tone3 = PinyinIpa.toned(hanzi);
            if (tone3 == null) return ""; // unknown letter -> key absent
            String display = style == PinyinStyle.NUMBERS
                    ? PinyinTone.numeric(tone3) : PinyinTone.marked(tone3);
            if (display == null || display.isEmpty()) return "";
            if (out.length() > 0) out.append(' ');
            out.append(display);
        }
        return out.toString();
    }

    /**
     * ja: kanji phrases first through the curated KanjiIpa table (unknown
     * kanji pass through untouched), then every letter run must be pure kana
     * for {@link KanaRomaji} — punctuation/whitespace separate runs (dropped,
     * same-source with the hand templates which carry no pause tokens);
     * anything else (leftover kanji, latin) voids the key.
     */
    private static String jaRomaji(String text) {
        String kana = KanjiIpa.toKana(text);
        StringBuilder out = new StringBuilder();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < kana.length(); i++) {
            char c = kana.charAt(i);
            if (Character.isLetter(c)) {
                run.append(c);
                continue;
            }
            if (run.length() > 0) {
                if (!appendRomaji(out, run.toString())) return "";
                run.setLength(0);
            }
        }
        if (run.length() > 0 && !appendRomaji(out, run.toString())) return "";
        return out.toString();
    }

    /** Convert one pure-kana run; false when unconvertible (key voided). */
    private static boolean appendRomaji(StringBuilder out, String run) {
        String romaji = KanaRomaji.toRomaji(run);
        if (romaji.isEmpty()) return false;
        if (out.length() > 0) out.append(' ');
        out.append(romaji);
        return true;
    }

    /** Hand-curated template wins; else the G2P strict draft ("" = absent). */
    private static String ipa(String text, List<String> handIpa, String lang) {
        if (handIpa != null && !handIpa.isEmpty()) {
            String curated = handIpa.get(0);
            if (curated != null && !curated.isBlank()) return curated.strip();
        }
        return G2p.toIpa(text, lang);
    }
}
