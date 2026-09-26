package com.theo.wizardreal.g2p;

import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-game G2P (grapheme-to-phoneme): text -> espeak-style IPA draft templates
 * for unknown / player-custom words, per docs/g2p/02_java_integration.md.
 *
 * <p>Three tiers (P0 scope):
 * <ol>
 *   <li><b>Tier-0 hand-curated dictionary</b> — every alias registered by
 *       {@link SpellRegistry} with a curated {@code ipa} template; hit = zero
 *       computation, quality 1.0.</li>
 *   <li><b>Tier-1 per-language primitives</b> — zh: hanzi -> toneless pinyin
 *       (embedded data table, generated from the MIT-licensed pypinyin data)
 *       -> initial/final IPA composition; ja: kana -> IPA, plus a CURATED
 *       phrase-level kanji->kana reading table ({@link KanjiIpa}, docs C4 —
 *       full morphological analysis stays out of scope); ko: Hangul
 *       decomposition -> jamo IPA (pure Unicode algorithm).</li>
 *   <li><b>Tier-2 English fallback</b> — NOT implemented at P0 (docs plan:
 *       Sphinx-4 WFST); English words outside Tier-0 return no result.</li>
 * </ol>
 *
 * <p>Output is a draft: the ipa-backtest loop (TTS round-trip scoring) is the
 * quality gate before a generated template may be written back into spell
 * JSONs — G2P output never reaches the recognizer without passing it.
 *
 * <p>Pure JVM, no Minecraft types — unit-testable.
 */
public final class G2p {
    private G2p() {}

    /** Script classification for one code point. */
    private enum Script { HAN, KANA, HANGUL, LATIN, OTHER }

    /**
     * Convert {@code text} to a space-separated espeak-style IPA string.
     *
     * @param lang preferred language bucket ("en"/"zh"/"ja"/"ko"); "" = auto
     *             per script. Han under "ja" converts only when covered by
     *             the KanjiIpa curated reading table (unknown kanji -> no
     *             draft, strict).
     * @return the IPA draft, or "" when any segment is unconvertible (strict:
     *         partial drafts must not reach template matching).
     */
    public static String toIpa(String text, String lang) {
        if (text == null || text.isBlank()) return "";
        Map<String, String> tier0 = tier0Dictionary();
        // Tier-0 wins on the WHOLE text first: the ja kanji pass below would
        // otherwise rewrite a curated kanji alias into kana and silently
        // demote it to a Tier-1 machine draft (the backtest loop writes
        // passing drafts back into spell JSONs — curated entries must keep
        // winning once they exist).
        String whole = tier0.get(text.strip().toLowerCase(Locale.ROOT));
        if (whole != null && !whole.isBlank()) return whole.strip();
        // ja kanji readings (docs C4, KanjiIpa): phrase-level substitution on
        // the WHOLE text BEFORE segmentation — segmentation would otherwise
        // split mixed kanji+kana aliases (炎のブレス) into a HAN segment that
        // strict mode must reject. Unknown kanji survive the pass and still
        // fail closed below.
        if ("ja".equals(lang)) {
            text = KanjiIpa.toKana(text.strip());
        }
        List<String> segments = segment(text.strip());
        if (segments.isEmpty()) return "";
        List<String> out = new ArrayList<>();
        for (String segment : segments) {
            String ipa = segmentIpa(segment, lang, tier0);
            if (ipa == null || ipa.isBlank()) return ""; // strict: no partial drafts
            out.add(ipa);
        }
        return String.join(" ", out);
    }

    private static String segmentIpa(String segment, String lang, Map<String, String> tier0) {
        Script script = scriptOf(segment.codePointAt(0));
        String low = segment.toLowerCase(Locale.ROOT);
        // Tier-0: curated dictionary wins for every script.
        String curated = tier0.get(low);
        if (curated != null && !curated.isBlank()) return curated.strip();
        return switch (script) {
            // Han converts under zh (default); any explicitly non-zh language
            // gets no draft (R1 audit #10: lang=en must not produce pinyin).
            case HAN -> (lang.isEmpty() || "zh".equals(lang)) ? PinyinIpa.toIpa(low) : "";
            case KANA -> KanaIpa.toIpa(segment);
            case HANGUL -> HangulIpa.toIpa(segment);
            // Latin: Tier-2 (Sphinx-4 WFST) is a later phase (docs 02 §4 P1).
            default -> "";
        };
    }

    /** Split text into maximal same-script runs (Han / Kana / Hangul / Latin).
     *  Spaces and any other space characters (incl. NBSP) separate runs; a
     *  non-convertible character (digit/punct/emoji) forms its own OTHER run
     *  which the strict conversion then rejects — it never absorbs neighbours. */
    static List<String> segment(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Script currentScript = Script.OTHER;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            Script s = scriptOf(cp);
            boolean sep = s == Script.OTHER
                    && (Character.isWhitespace(cp) || Character.isSpaceChar(cp));
            if (sep) {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                currentScript = Script.OTHER;
            } else if (s == currentScript
                    || (current.length() == 0 && currentScript == Script.OTHER)) {
                // append same-script continuation; an OTHER char only starts a
                // run when the buffer is empty AND never absorbs a following
                // different script (R1 audit #24)
                if (current.length() == 0) currentScript = s;
                else if (s != currentScript) {
                    out.add(current.toString());
                    current.setLength(0);
                    currentScript = s;
                }
                current.appendCodePoint(cp);
            } else {
                if (current.length() > 0) out.add(current.toString());
                current.setLength(0);
                currentScript = s;
                current.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        if (current.length() > 0) out.add(current.toString());
        return out;
    }

    private static Script scriptOf(int cp) {
        if ((cp >= 0x3400 && cp <= 0x4DBF) || (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0x20000 && cp <= 0x2EBEF) || (cp >= 0x30000 && cp <= 0x3134A)) {
            return Script.HAN; // ext A/B+ declared strict: the pinyin table has no such rows
        }
        if ((cp >= 0x3041 && cp <= 0x309F) || (cp >= 0x30A1 && cp <= 0x30FF)
                || cp == 0x30FC) return Script.KANA;
        if (cp >= 0xAC00 && cp <= 0xD7A3) return Script.HANGUL;
        if (Character.isLetter(cp)) return Script.LATIN;
        return Script.OTHER;
    }

    private static volatile Map<String, String> tier0Cache;

    /**
     * Tier-0 dictionary: lowercase alias -> first curated IPA template, from
     * every registered spell (trigger aliases + chant line aliases).
     *
     * <p>Only SOLE-alias surfaces register. Multi-alias surfaces have no
     * machine-readable alias-&rarr;template mapping (a multilingual trigger
     * keeps one merged template list: en aliases have no segment at all, and
     * zh/ja segment counts need not match alias counts) — binding the first
     * template to every alias handed each language's aliases another
     * language's reading (en "radix" drafted as zh 缠根, 2026-09-26 audit).
     * Unregistered aliases fall to the per-script chain, which is the correct
     * draft for them.</p>
     */
    public static Map<String, String> tier0Dictionary() {
        Map<String, String> cache = tier0Cache;
        if (cache != null) return cache;
        synchronized (G2p.class) {
            if (tier0Cache != null) return tier0Cache;
            Map<String, String> out = new HashMap<>();
            for (Spell spell : SpellRegistry.all()) {
                collect(out, spell.pronunciation().aliases(), spell.pronunciation().ipa());
                for (Chant chant : spell.chants()) {
                    for (ChantLine line : chant.lines()) {
                        collect(out, line.pronunciation().aliases(), line.pronunciation().ipa());
                    }
                }
            }
            tier0Cache = Map.copyOf(out);
            return tier0Cache;
        }
    }

    private static void collect(Map<String, String> out, List<String> aliases, List<String> ipa) {
        if (aliases == null || ipa == null || aliases.size() != 1 || ipa.isEmpty()) return;
        String template = ipa.get(0);
        String alias = aliases.get(0);
        if (alias == null || alias.isBlank() || template == null || template.isBlank()) return;
        out.put(alias.strip().toLowerCase(Locale.ROOT), template.strip());
    }

    /** Invalidate the Tier-0 cache (call after the spell registry rebuilds). */
    public static void invalidate() {
        tier0Cache = null;
    }
}
