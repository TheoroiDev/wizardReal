package com.theo.wizardreal.server;

import com.theo.wizardreal.api.Pronunciation;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.voicecast.match.ChantLineMatcher;
import com.theo.voicecast.match.LenientLine;
import com.theo.voicecast.match.LenientLineMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure, MC-free state machine for one player's in-progress ritual chant.
 * Extracted from {@link ChantManager} so the line-matching rules and the
 * variant-lock / grace-window / timeout behavior are unit-testable: the engine
 * takes the wall-clock time as a parameter and reports outcomes as inert
 * {@link FeedResult} events, while the manager maps them onto network packets,
 * lockouts and the validated cast path.
 *
 * <p>D9 entry rework (0.4.0): the engine can be constructed pre-locked
 * ({@code startAtLine}) when the idle-state L1 gate already matched the entry
 * utterance — that utterance then counts as completed line 1. While chanting,
 * speaking the LAST line (the spell name) releases early at the completed-lines
 * power tier (咒名跳章, {@code chant_policy.skip_allowed}); the timeout is
 * injected per engine (per-line or fixed mode, see the {@code [chant]} config),
 * and {@code wrongStreak} consecutive post-grace wrong lines fail the chant
 * (失败反噬: the manager applies the darkness penalty).
 */
public final class ChantEngine {
    /** Fallback timeout when the config provides none (legacy 90 s behavior). */
    public static final long DEFAULT_TIMEOUT_MS = 90_000L;
    // After advancing to a new line, ignore non-matching utterances for a short
    // grace window: the recognizer often flushes leftover audio / silence of the
    // just-completed line, which would otherwise immediately flash a false error.
    public static final long LINE_GRACE_MS = 1200L;
    // Ignore extremely short/empty utterances entirely (noise, breath).
    public static final int MIN_HEARD_CHARS = 2;
    // Consecutive post-grace wrong lines (same target line) that fail the chant.
    public static final int WRONG_LINE_FAIL_LIMIT = 3;

    /** One HUD update: which variant/line the player is on, and whether it is an error flash. */
    public record Progress(int variant, int lineIndex, boolean error) {}

    /** Outcome of feeding one recognized utterance into the state machine. */
    public record FeedResult(boolean consumed, boolean timeout, boolean finished, boolean earlyRelease,
                             boolean failed, int completedLines, List<Progress> progress) {
        static FeedResult notConsumed() {
            return new FeedResult(false, false, false, false, false, 0, List.of());
        }

        static FeedResult of(boolean timeout, boolean finished, List<Progress> progress) {
            return new FeedResult(true, timeout, finished, false, false, 0, progress);
        }

        static FeedResult earlyRelease(int completedLines, List<Progress> progress) {
            return new FeedResult(true, false, false, true, false, completedLines, progress);
        }

        static FeedResult failed(List<Progress> progress) {
            return new FeedResult(true, false, false, false, true, 0, progress);
        }
    }

    private final Spell spell;
    private final List<Chant> chants;
    private final long timeoutMs;
    private final ChantLineMatcher matcher;
    private int variant = -1;   // locked chant index; -1 until first line
    private int lineIndex;
    private long lastActivity;
    private long lineStartedMs; // when the current line began (for the grace window)
    private int wrongStreak;    // consecutive post-grace wrong lines against the current line

    public ChantEngine(Spell spell, long nowMs) {
        this(spell, nowMs, DEFAULT_TIMEOUT_MS, -1);
    }

    /** Entry rework (D9): pre-locked engine — the idle-state L1 gate already
     * matched the entry utterance, which counts as completed line 1. */
    public ChantEngine(Spell spell, long nowMs, long timeoutMs, int lockedVariant) {
        this(spell, nowMs, timeoutMs, lockedVariant, LenientLineMatcher.INSTANCE);
    }

    /** voiceCast#48 W1 seam: the line-progression decision comes from the
     *  injected matcher (default binding = the pre-#48 lenient boolean,
     *  behavior-free). W2+ bindings route through the production adjudicator. */
    public ChantEngine(Spell spell, long nowMs, long timeoutMs, int lockedVariant,
                       ChantLineMatcher matcher) {
        this.spell = spell;
        this.chants = spell.chants();
        this.timeoutMs = timeoutMs;
        this.matcher = matcher == null ? LenientLineMatcher.INSTANCE : matcher;
        if (lockedVariant >= 0 && lockedVariant < chants.size()) {
            this.variant = lockedVariant;
            this.lineIndex = 1;
        }
        this.lastActivity = nowMs;
        this.lineStartedMs = nowMs;
    }

    Spell spell() {
        return spell;
    }

    long lastActivityMs() {
        return lastActivity;
    }

    long timeoutMs() {
        return timeoutMs;
    }

    /** Total line count of the currently locked variant (0 while unlocked). */
    int currentLineCount() {
        return variant < 0 ? 0 : chants.get(variant).lines().size();
    }

    public boolean timedOut(long nowMs) {
        return nowMs - lastActivity > timeoutMs;
    }

    /**
     * Feed one recognized utterance to an in-progress chant.
     *
     * @return a result whose {@code consumed} flag mirrors the manager contract
     *         (true = the utterance belonged to the chant, caller must not
     *         instant-cast it); {@code timeout} means the chant expired and the
     *         caller must run its cancel path; {@code earlyRelease} means the
     *         spell-name line was spoken mid-chant (咒名跳章) and the cast
     *         should fire at {@code completedLines} power; {@code failed} means
     *         too many consecutive wrong lines — the caller runs the failure
     *         (darkness) path.
     */
    public FeedResult feed(String heard, List<String> heardIpa, long nowMs) {
        return feed(heard, heardIpa, nowMs, null);
    }

    /** voiceCast#48 W2: the utterance's production adjudication (may be null)
     *  rides along so the injected matcher can weigh it. */
    public FeedResult feed(String heard, List<String> heardIpa, long nowMs,
                           com.theo.voicecast.match.ChantLineMatcher.ChantVerdict verdict) {
        if (timedOut(nowMs)) {
            return FeedResult.of(true, false, List.of());
        }
        lastActivity = nowMs;

        // Ignore empty/noise utterances (silence flush, breath, short blips).
        // \p{L}\p{N} keeps CJK: the ASCII-only form stripped zh/ja transcripts
        // to empty, silently swallowing every text-lane chant line (found by
        // the chant-sequence bench, 2026-09-25 — qwen3 zh/ja progression 0%).
        boolean hasText = heard != null && heard.replaceAll("[^\\p{L}\\p{N}]", "").length() >= MIN_HEARD_CHARS;
        boolean hasIpa = heardIpa != null && heardIpa.size() >= 2;
        if (!hasText && !hasIpa) {
            return FeedResult.of(false, false, List.of()); // swallowed, not counted as a wrong line
        }

        List<Progress> events = new ArrayList<>(1);

        if (variant < 0) {
            // First line: pick the variant whose first line matches best.
            // Don't flash an error during the opening grace window.
            int best = -1;
            for (int vi = 0; vi < chants.size(); vi++) {
                ChantLine first = chants.get(vi).lines().get(0);
                if (lineMatches(matcher, first, heard, heardIpa, verdict)) {
                    best = vi;
                    break;
                }
            }
            if (best < 0) {
                if (nowMs - lineStartedMs > LINE_GRACE_MS) {
                    events.add(new Progress(0, 0, true));
                }
                return FeedResult.of(false, false, events);
            }
            variant = best;
            lineIndex = 1;
            lineStartedMs = nowMs;
            boolean finished = lineIndex >= chants.get(best).lines().size();
            events.add(new Progress(best, lineIndex, false));
            return FeedResult.of(false, finished, events);
        }

        Chant chant = chants.get(variant);
        if (lineIndex >= chant.lines().size()) {
            return FeedResult.of(false, true, events);
        }

        ChantLine current = chant.lines().get(lineIndex);
        if (lineMatches(matcher, current, heard, heardIpa, verdict)) {
            lineIndex++;
            lineStartedMs = nowMs;
            wrongStreak = 0;
            boolean finished = lineIndex >= chant.lines().size();
            events.add(new Progress(variant, lineIndex, false));
            return FeedResult.of(false, finished, events);
        }

        // D9 咒名跳章: speaking the LAST line (spell name) while a middle line
        // is expected releases early at the completed-lines power tier. The
        // permission (skip_allowed) is enforced by the manager, which sees the
        // spell policy.
        if (lineIndex < chant.lines().size() - 1) {
            ChantLine castLine = chant.lines().get(chant.lines().size() - 1);
            if (lineMatches(matcher, castLine, heard, heardIpa, verdict)) {
                return FeedResult.earlyRelease(lineIndex, events);
            }
        }

        // Wrong line: only flash red once the grace window after the last
        // advance has passed (this drops leftover audio of the prior line).
        if (nowMs - lineStartedMs > LINE_GRACE_MS) {
            wrongStreak++;
            events.add(new Progress(variant, lineIndex, true));
            if (wrongStreak >= WRONG_LINE_FAIL_LIMIT) {
                return FeedResult.failed(events);
            }
        }
        return FeedResult.of(false, false, events);
    }

    /**
     * M1 咏唱打断 (rollback one line): progress retreats by one line (never
     * below the entry line), the grace window restarts, and the wrong-streak
     * resets. The player must re-speak the retreated line.
     */
    public Progress rollbackLine(long nowMs) {
        lastActivity = nowMs;
        lineStartedMs = nowMs;
        wrongStreak = 0;
        if (variant < 0) return new Progress(0, 0, false);
        lineIndex = Math.max(0, lineIndex - 1);
        return new Progress(variant, lineIndex, false);
    }

    /** Lenient per-line match, delegated to the injected matcher (W1 seam;
     *  default binding = IPA phonemes first, then text aliases — unchanged). */
    static boolean lineMatches(ChantLine line, String heard, List<String> heardIpa) {
        return lineMatches(LenientLineMatcher.INSTANCE, line, heard, heardIpa, null);
    }

    static boolean lineMatches(ChantLineMatcher matcher, ChantLine line,
                               String heard, List<String> heardIpa,
                               com.theo.voicecast.match.ChantLineMatcher.ChantVerdict verdict) {
        Pronunciation p = line.pronunciation();
        return matcher.match(p.id(), p.ipa(), p.aliases(), heard, heardIpa, verdict).matched();
    }


}
