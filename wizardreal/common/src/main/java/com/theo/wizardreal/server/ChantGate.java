package com.theo.wizardreal.server;

import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.match.PhonemeMatcher;
import com.theo.wizardreal.match.SpellMatcher;

import java.util.List;
import java.util.Map;

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
 * Line hits come from CTC templateScores (chant-line pronunciation ids —
 * including the language dimension of the 0.4.0 language-keyed format) or the
 * lenient {@link ChantEngine} line rules; trigger hits from the
 * CTC/phoneme/text matcher chain. Priority: ENTER &gt; INSTANT &gt; SKIP &gt;
 * NONE — the L1 gate wins because the session router already narrowed the
 * recognizer's grammar to the player's language bucket, so a cross-language
 * false positive here is unlikely while a generic trigger word is not.
 *
 * <p>Pure JVM logic (SpellRegistry + matchers only) — unit-testable.
 */
final class ChantGate {
    enum Kind { NONE, INSTANT, ENTER, SKIP }

    record Decision(Kind kind, Spell spell, int variant, float score) {
        static final Decision NONE = new Decision(Kind.NONE, null, -1, 0f);
    }

    private ChantGate() {}

    static Decision route(String heard, List<String> heardIpa, Map<String, Float> templateScores) {
        Decision fromScores = fromTemplateScores(templateScores);
        if (fromScores.kind() == Kind.ENTER) return fromScores;
        // L1-first priority: an L1 match (text/IPA) beats a SKIP candidate.
        Decision l1 = l1Gate(heard, heardIpa);
        if (l1.kind() == Kind.ENTER) return l1;
        if (fromScores.kind() != Kind.NONE) return fromScores;

        // Fallback trigger matchers (phoneme, then text aliases).
        SpellMatcher.Match match = null;
        if (heardIpa != null && !heardIpa.isEmpty()) {
            PhonemeMatcher.Match pm = PhonemeMatcher.match(heardIpa);
            if (pm != null) match = new SpellMatcher.Match(pm.spell(), pm.score());
        }
        if (match == null && heard != null && !heard.isBlank()) {
            match = SpellMatcher.match(heard);
        }
        if (match == null) return Decision.NONE;
        return match.spell().chants().isEmpty()
                ? new Decision(Kind.INSTANT, match.spell(), -1, match.score())
                : new Decision(Kind.SKIP, match.spell(), -1, match.score());
    }

    /** CTC forward posteriors: chant-line ids can ENTER (line 0) or SKIP (last
     * line); spell trigger ids cast instant spells or SKIP rituals. */
    private static Decision fromTemplateScores(Map<String, Float> templateScores) {
        if (templateScores == null || templateScores.isEmpty()) return Decision.NONE;
        Decision best = Decision.NONE;
        for (Spell spell : SpellRegistry.all()) {
            float threshold = spell.threshold() >= 0 ? spell.threshold() : ServerVoiceCast.FORWARD_MATCH_THRESHOLD;
            List<Chant> chants = spell.chants();
            for (int v = 0; v < chants.size(); v++) {
                List<ChantLine> lines = chants.get(v).lines();
                for (int i = 0; i < lines.size(); i++) {
                    Float s = templateScores.get(lines.get(i).pronunciation().id());
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
    private static Decision l1Gate(String heard, List<String> heardIpa) {
        if ((heard == null || heard.isBlank()) && (heardIpa == null || heardIpa.isEmpty())) {
            return Decision.NONE;
        }
        for (Spell spell : SpellRegistry.all()) {
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

    private static Decision promote(Decision current, Decision candidate) {
        return candidate.score() > current.score() ? candidate : current;
    }
}
