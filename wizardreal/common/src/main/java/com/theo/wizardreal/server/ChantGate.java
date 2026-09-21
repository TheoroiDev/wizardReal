package com.theo.wizardreal.server;

import com.theo.voicecast.api.Alternative;
import com.theo.voicecast.api.Decision;
import com.theo.voicecast.api.RecognitionResult;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;

import java.util.List;

/**
 * Idle-state semantic gate (D9, re-keyed onto the voicecast Decision by the
 * C1b contract refactor): decides what one ADJUDICATED utterance means while
 * the player is NOT chanting. Voicecast owns "what was said" — the fusion of
 * the text/phoneme/CTC lines over the routed vocabulary (see
 * {@code UtteranceAdjudicator}); this gate owns "what it means":
 *
 * <ul>
 *   <li>{@link Kind#ENTER} — the winning pronunciation is a ritual spell's
 *       first line (L1): the chant starts pre-locked at that variant (首行即门;
 *       the entry utterance counts as completed line 1).</li>
 *   <li>{@link Kind#INSTANT} — an instant spell's trigger matched: cast now.</li>
 *   <li>{@link Kind#SKIP} — a ritual spell's trigger / spell-name line matched:
 *       破弃快施 candidate (permission gated by {@link ChantManager#trySkipCast}).</li>
 * </ul>
 *
 * <p>Priority: a first-line candidate ENTERs over the decision's SKIP/INSTANT
 * (首行即门 — the pre-v2 L1 gate outranked every non-ENTER result; first-line
 * candidates are always EXACT-tier in the v2 fusion, so promoting them from
 * the alternatives is tier-safe). ENTER &gt; INSTANT &gt; SKIP &gt; NONE
 * otherwise.
 *
 * <p>Reject levels (误触发治理), now keyed on the Decision (the work order's
 * sanctioned re-keying — the pre-v2 ctcPresent conditionality was
 * engine-conditional and is gone): EXACT is accepted at every level; NEAR
 * only at level 0; AMBIGUOUS/REJECTED never cast. The per-surface
 * suppression the old levels performed (kill the trigger matcher surface at
 * level &ge; 1, the lenient L1 surface at level &ge; 2) travels as DATA:
 * WizardReal raises per-entry threshold hints at push time
 * ({@code ServerVoiceCast#pushVocabulary}), so voicecast already refuses to
 * emit those candidates. Language trimming is likewise upstream (push-time
 * trim + the session's engine-language projection).
 *
 * <p>Pure JVM logic ({@link SpellRegistry} + the result record only) —
 * unit-testable, and the shared equivalence vectors (c1b_vectors.json) pin
 * it against the pre-v2 chain's gameplay outcomes.
 */
final class ChantGate {
    enum Kind { NONE, INSTANT, ENTER, SKIP }

    record Decision(Kind kind, Spell spell, int variant, float score) {
        static final Decision NONE = new Decision(Kind.NONE, null, -1, 0f);
    }

    /** Default rejection level (0 = legacy full acceptance). Precision-minded
     *  servers raise it via {@code -Dwizardreal.voice.rejectLevel}: level 1
     *  refuses NEAR verdicts (and, via the push-time hint overlay, stops
     *  voicecast from emitting trigger-surface candidates at all); level 2
     *  additionally overlays the first-line surfaces — a true L1 utterance
     *  should clear voicecast's CTC/text evidence. */
    static final int DEFAULT_REJECT_LEVEL = 0;

    private ChantGate() {}

    static int rejectLevel() {
        return Integer.getInteger("wizardreal.voice.rejectLevel", DEFAULT_REJECT_LEVEL);
    }

    static Decision route(RecognitionResult result) {
        return route(result, rejectLevel());
    }

    /** @param level effective reject level (0 = accept NEAR, &ge;1 = EXACT only). */
    static Decision route(RecognitionResult result, int level) {
        // The voicecast verdict enum is qualified: this class nests its own
        // gameplay Decision record.
        com.theo.voicecast.api.Decision verdict =
                result == null ? null : result.decision();
        if (verdict == null) return Decision.NONE;
        if (verdict == com.theo.voicecast.api.Decision.AMBIGUOUS
                || verdict == com.theo.voicecast.api.Decision.REJECTED) {
            return Decision.NONE;
        }
        if (verdict == com.theo.voicecast.api.Decision.NEAR && level >= 1) {
            return Decision.NONE;
        }
        Decision best = map(result.spellId(), result.pronId(), result.score());
        if (best.kind() == Kind.ENTER) return best;
        // 首行即门: a first-line runner-up (always EXACT-tier in the fusion)
        // ENTERs over the decision's SKIP/INSTANT.
        for (Alternative alt : result.alternatives()) {
            Decision mapped = map(alt.spellId(), alt.pronId(), alt.score());
            if (mapped.kind() == Kind.ENTER) return mapped;
        }
        return best;
    }

    /** One candidate (decision or alternative) -> gameplay kind via the registry. */
    private static Decision map(String spellId, String pronId, float score) {
        Spell spell = byId(spellId);
        if (spell == null) return Decision.NONE; // not a registered spell (foreign vocabulary id)
        if (spell.pronunciation() != null && pronId.equals(spell.pronunciation().id())) {
            // trigger row: instant spells cast, rituals become skip-cast candidates
            return spell.chants().isEmpty()
                    ? new Decision(Kind.INSTANT, spell, -1, score)
                    : new Decision(Kind.SKIP, spell, -1, score);
        }
        List<Chant> chants = spell.chants();
        for (int v = 0; v < chants.size(); v++) {
            List<ChantLine> lines = chants.get(v).lines();
            for (int i = 0; i < lines.size(); i++) {
                if (!pronId.equals(lines.get(i).pronunciation().id())) continue;
                if (i == 0) return new Decision(Kind.ENTER, spell, v, score); // 首行即门
                if (i == lines.size() - 1) return new Decision(Kind.SKIP, spell, -1, score);
                return Decision.NONE; // middle lines spoken at idle carry no meaning
            }
        }
        return Decision.NONE; // unknown pronunciation id for this spell
    }

    private static Spell byId(String spellId) {
        if (spellId == null || spellId.isEmpty()) return null;
        for (Spell spell : SpellRegistry.all()) {
            if (spellId.equals(spell.id())) return spell;
        }
        return null;
    }
}
