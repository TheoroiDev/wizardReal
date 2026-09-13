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
 *       -> initial/final IPA composition; ja: kana -> IPA (kanji readings are
 *       NOT supported at P0 — needs a morphological analyzer, see docs C4);
 *       ko: Hangul decomposition -> jamo IPA (pure Unicode algorithm).</li>
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
     *             per script. Han under "ja" returns "" at P0 (no kanji
     *             readings — docs C4).
     * @return the IPA draft, or "" when any segment is unconvertible (strict:
     *         partial drafts must not reach template matching).
     */
    public static String toIpa(String text, String lang) {
        if (text == null || text.isBlank()) return "";
        List<String> segments = segment(text.strip());
        if (segments.isEmpty()) return "";
        Map<String, String> tier0 = tier0Dictionary();
        List<String> out = new ArrayList<>();
        for (String segment : segments) {
            String ipa = segmentIpa(segment, lang, tier0);
            if (ipa == null || ipa.isBlank()) return ""; // strict: no partial drafts
            out.add(ipa);
        }
        return String.join(" ", out);
    }

    private static String segmentIpa(String segment, String lang, Map<String, String> tier0) {
        Script script = scriptOf(segment.charAt(0));
        String low = segment.toLowerCase(Locale.ROOT);
        // Tier-0: curated dictionary wins for every script.
        String curated = tier0.get(low);
        if (curated != null && !curated.isBlank()) return curated.strip();
        return switch (script) {
            case HAN -> "ja".equals(lang) ? "" : PinyinIpa.toIpa(low); // C4: no ja kanji at P0
            case KANA -> KanaIpa.toIpa(segment);
            case HANGUL -> HangulIpa.toIpa(segment);
            // Latin: Tier-2 (Sphinx-4 WFST) is a later phase (docs 02 §4 P1).
            default -> "";
        };
    }

    /** Split text into maximal same-script runs (Han / Kana / Hangul / Latin). */
    static List<String> segment(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Script currentScript = Script.OTHER;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            Script s = scriptOf(cp);
            if (s == Script.OTHER && Character.isWhitespace(cp)) {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                currentScript = Script.OTHER;
            } else if (s == currentScript || (currentScript == Script.OTHER && s != Script.OTHER)) {
                if (current.length() == 0) currentScript = s;
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
                || (cp >= 0xF900 && cp <= 0xFAFF)) return Script.HAN;
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
        if (aliases == null || ipa == null || ipa.isEmpty()) return;
        String template = ipa.get(0);
        if (template == null || template.isBlank()) return;
        for (String alias : aliases) {
            out.putIfAbsent(alias.strip().toLowerCase(Locale.ROOT), template.strip());
        }
    }

    /** Invalidate the Tier-0 cache (call after the spell registry rebuilds). */
    public static void invalidate() {
        tier0Cache = null;
    }
}
