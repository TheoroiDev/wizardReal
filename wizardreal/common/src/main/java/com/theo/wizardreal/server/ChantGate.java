package com.theo.wizardreal.server;

import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.match.PhonemeMatcher;
import com.theo.wizardreal.match.SpellMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Idle-state utterance routing (D9, voice overhaul): decides what one
 * recognized utterance means while the player is NOT chanting.
 *
 * <ul>
 *   <li>{@link Kind#ENTER} — the utterance matched a ritual spell's first line
 *       (L1): the chant starts pre-locked at that variant (首行即门; the entry
 *       utterance counts as completed line 1).</li>
 *   <li>{@link Kind#INSTANT} — an instant spell's trigger matched: cast now.</li>
 *   <li>{@link Kind#SKIP} — a ritual spell's trigger or spell-name line matched:
 *       破弃快施 candidate (permission gated by {@link ChantManager#trySkipCast}).</li>
 * </ul>
 *
 * <p>Line hits come from CTC templateScores (chant-line pronunciation ids —
 * including the language dimension of the 0.4.0 language-keyed format) or the
 * lenient {@link ChantEngine} line rules; trigger hits from the
 * CTC/phoneme/text matcher chain. Priority: ENTER &gt; INSTANT &gt; SKIP &gt;
 * NONE — the L1 gate wins because the session router already narrowed the
 * recognizer's grammar to the player's language bucket, so a cross-language
 * false positive here is unlikely while a generic trigger word is not.
 *
 * <p>Rejection + trimming (0.4.x 误触发治理): the CTC posterior already
 * competes every pushed template against the "nothing said" null path, so
 * when template scores are present they are authoritative. {@link #rejectLevel()}
 * controls how far a CTC miss falls through to the snap-to-nearest fallbacks:
 * 0 = legacy (phoneme → text → lenient L1), 1 = also skip phoneme/text,
 * 2 = also skip the lenient L1 gate (full reject — a true L1 utterance should
 * clear the CTC threshold). The candidate set is additionally trimmed to the
 * configured language buckets ({@code [voice] languages}): legacy
 * pronunciations (no buckets) always stay.
 *
 * <p>Pure JVM logic (SpellRegistry + matchers only) — unit-testable.
 */
final class ChantGate {
    enum Kind { NONE, INSTANT, ENTER, SKIP }

    record Decision(Kind kind, Spell spell, int variant, float score) {
        static final Decision NONE = new Decision(Kind.NONE, null, -1, 0f);
    }

    /**
     * Default CTC-miss rejection level. 0 (legacy fallthrough) per the
     * 2026-09-08 TTS backtest (docs/ipa/ipa-backtest.md §reject-trim): the CTC
     * posterior misses ~49% of true utterances into the lenient fallbacks
     * (level 1 traded -237 hits for -72 wrongs), and no posterior-only
     * acceptance rule beat the legacy chain — rejection stays available for
     * servers that prefer precision (raise via
     * {@code -Dwizardreal.voice.rejectLevel}) until CTC scoring is calibrated.
     */
    static final int DEFAULT_REJECT_LEVEL = 0;

    private ChantGate() {}

    static int rejectLevel() {
        return Integer.getInteger("wizardreal.voice.rejectLevel", DEFAULT_REJECT_LEVEL);
    }

    static Decision route(String heard, List<String> heardIpa, Map<String, Float> templateScores) {
        return route(heard, heardIpa, templateScores, Set.of());
    }

    /**
     * @param allowedLanguages enabled language buckets (empty = all); legacy
     *                         pronunciations without buckets always pass.
     */
    static Decision route(String heard, List<String> heardIpa, Map<String, Float> templateScores,
                          Set<String> allowedLanguages) {
        Decision fromScores = fromTemplateScores(templateScores, allowedLanguages);
        if (fromScores.kind() == Kind.ENTER) return fromScores;
        boolean ctcPresent = templateScores != null && !templateScores.isEmpty();
        int level = rejectLevel();
        // Lenient L1 entry gate: the CTC posterior competes against the null
        // path, so with scores in hand a level >= 2 miss means no ritual entry.
        if (!ctcPresent || level < 2) {
            Decision l1 = l1Gate(heard, heardIpa, allowedLanguages);
            if (l1.kind() == Kind.ENTER) return l1;
        }
        if (fromScores.kind() != Kind.NONE) return fromScores;

        // Fallback trigger matchers (phoneme, then text aliases) over the
        // language-trimmed candidate set. With CTC evidence present these
        // snap-to-nearest layers are the main noise->false-cast path, so a
        // level >= 1 miss suppresses them entirely.
        if (!ctcPresent || level < 1) {
            List<Spell> candidates = candidates(allowedLanguages);
            SpellMatcher.Match match = null;
            if (heardIpa != null && !heardIpa.isEmpty()) {
                PhonemeMatcher.Match pm = PhonemeMatcher.match(heardIpa, candidates);
                if (pm != null) match = new SpellMatcher.Match(pm.spell(), pm.score());
            }
            if (match == null && heard != null && !heard.isBlank()) {
                match = SpellMatcher.match(heard, candidates);
            }
            if (match == null) return Decision.NONE;
            return match.spell().chants().isEmpty()
                    ? new Decision(Kind.INSTANT, match.spell(), -1, match.score())
                    : new Decision(Kind.SKIP, match.spell(), -1, match.score());
        }
        return Decision.NONE;
    }

    /** CTC forward posteriors: chant-line ids can ENTER (line 0) or SKIP (last
     * line); spell trigger ids cast instant spells or SKIP rituals. */
    private static Decision fromTemplateScores(Map<String, Float> templateScores,
                                               Set<String> allowedLanguages) {
        if (templateScores == null || templateScores.isEmpty()) return Decision.NONE;
        Decision best = Decision.NONE;
        for (Spell spell : SpellRegistry.all()) {
            if (!languageEnabled(spell.pronunciation(), allowedLanguages)) continue;
            float threshold = spell.threshold() >= 0 ? spell.threshold() : ServerVoiceCast.FORWARD_MATCH_THRESHOLD;
            List<Chant> chants = spell.chants();
            for (int v = 0; v < chants.size(); v++) {
                List<ChantLine> lines = chants.get(v).lines();
                for (int i = 0; i < lines.size(); i++) {
                    ChantLine line = lines.get(i);
                    if (!lineLanguageEnabled(line, allowedLanguages)) continue;
                    Float s = templateScores.get(line.pronunciation().id());
                    if (s == null || s < threshold) continue;
                    if (i == 0) return new Decision(Kind.ENTER, spell, v, s); // 首行即门
                    if (i == lines.size() - 1) {
                        best = promote(best, new Decision(Kind.SKIP, spell, -1, s));
                    }
                    // middle lines spoken at idle carry no meaning (yet)
                }
            }
            Float s = templateScores.get(spell.pronunciation().id());
            if (s != null && s >= threshold) {
                best = promote(best, spell.chants().isEmpty()
                        ? new Decision(Kind.INSTANT, spell, -1, s)
                        : new Decision(Kind.SKIP, spell, -1, s));
            }
        }
        return best;
    }

    /** Lenient first-line gate over every ritual chain (text + IPA rules). */
    private static Decision l1Gate(String heard, List<String> heardIpa, Set<String> allowedLanguages) {
        if ((heard == null || heard.isBlank()) && (heardIpa == null || heardIpa.isEmpty())) {
            return Decision.NONE;
        }
        for (Spell spell : candidates(allowedLanguages)) {
            List<Chant> chants = spell.chants();
            if (chants.isEmpty()) continue;
            for (int v = 0; v < chants.size(); v++) {
                if (ChantEngine.lineMatches(chants.get(v).lines().get(0), heard, heardIpa)) {
                    return new Decision(Kind.ENTER, spell, v, 1f);
                }
            }
        }
        return Decision.NONE;
    }

    /** Spells whose pronunciation passes the language trim (legacy = always). */
    private static List<Spell> candidates(Set<String> allowedLanguages) {
        Collection<Spell> all = SpellRegistry.all();
        List<Spell> out = new ArrayList<>(all.size());
        for (Spell spell : all) {
            if (languageEnabled(spell.pronunciation(), allowedLanguages)) out.add(spell);
        }
        return out;
    }

    private static boolean languageEnabled(com.theo.voicecast.api.Pronunciation p, Set<String> allowed) {
        // Legacy (bucket-less) pronunciations are routed to every engine.
        if (p.languages().isEmpty() || allowed.isEmpty()) return true;
        for (String lang : p.languages().keySet()) {
            if (allowed.contains(lang.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** Chant-line ids embed their language: {@code <spell>.chant.<lang>.<v>:<i>}. */
    private static boolean lineLanguageEnabled(com.theo.wizardreal.api.ChantLine line, Set<String> allowed) {
        if (allowed.isEmpty()) return true;
        String id = line.pronunciation().id();
        int p = id.indexOf(".chant.");
        if (p < 0) return true;
        String rest = id.substring(p + 7);
        int dot = rest.indexOf('.');
        String lang = (dot < 0 ? rest : rest.substring(0, dot)).toLowerCase(Locale.ROOT);
        return allowed.contains(lang);
    }

    private static Decision promote(Decision current, Decision candidate) {
        return candidate.score() > current.score() ? candidate : current;
    }
}
