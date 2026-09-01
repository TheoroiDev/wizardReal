package com.theo.wizardreal.server;

import com.theo.voicecast.api.Pronunciation;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.util.Levenshtein;

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
 */
public final class ChantEngine {
    public static final long TIMEOUT_MS = 90_000L;
    // After advancing to a new line, ignore non-matching utterances for a short
    // grace window: the recognizer often flushes leftover audio / silence of the
    // just-completed line, which would otherwise immediately flash a false error.
    public static final long LINE_GRACE_MS = 1200L;
    // Ignore extremely short/empty utterances entirely (noise, breath).
    public static final int MIN_HEARD_CHARS = 2;

    /** One HUD update: which variant/line the player is on, and whether it is an error flash. */
    public record Progress(int variant, int lineIndex, boolean error) {}

    /** Outcome of feeding one recognized utterance into the state machine. */
    public record FeedResult(boolean consumed, boolean timeout, boolean finished, List<Progress> progress) {
        static FeedResult notConsumed() {
            return new FeedResult(false, false, false, List.of());
        }

        static FeedResult of(boolean timeout, boolean finished, List<Progress> progress) {
            return new FeedResult(true, timeout, finished, progress);
        }
    }

    private final Spell spell;
    private final List<Chant> chants;
    private int variant = -1;   // locked chant index; -1 until first line
    private int lineIndex;
    private long lastActivity;
    private long lineStartedMs; // when the current line began (for the grace window)

    public ChantEngine(Spell spell, long nowMs) {
        this.spell = spell;
        this.chants = spell.chants();
        this.lastActivity = nowMs;
        this.lineStartedMs = nowMs;
    }

    Spell spell() {
        return spell;
    }

    long lastActivityMs() {
        return lastActivity;
    }

    public boolean timedOut(long nowMs) {
        return nowMs - lastActivity > TIMEOUT_MS;
    }

    /**
     * Feed one recognized utterance to an in-progress chant.
     *
     * @return a result whose {@code consumed} flag mirrors the manager contract
     *         (true = the utterance belonged to the chant, caller must not
     *         instant-cast it); {@code timeout} means the chant expired and the
     *         caller must run its cancel path.
     */
    public FeedResult feed(String heard, List<String> heardIpa, long nowMs) {
        if (timedOut(nowMs)) {
            return FeedResult.of(true, false, List.of());
        }
        lastActivity = nowMs;

        // Ignore empty/noise utterances (silence flush, breath, short blips).
        boolean hasText = heard != null && heard.replaceAll("[^a-z0-9A-Z]", "").length() >= MIN_HEARD_CHARS;
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
                if (lineMatches(first, heard, heardIpa)) {
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
        if (lineMatches(current, heard, heardIpa)) {
            lineIndex++;
            lineStartedMs = nowMs;
            boolean finished = lineIndex >= chant.lines().size();
            events.add(new Progress(variant, lineIndex, false));
            return FeedResult.of(false, finished, events);
        }
        // Wrong line: only flash red once the grace window after the last
        // advance has passed (this drops leftover audio of the prior line).
        if (nowMs - lineStartedMs > LINE_GRACE_MS) {
            events.add(new Progress(variant, lineIndex, true));
        }
        return FeedResult.of(false, false, events);
    }

    /** Lenient per-line match: IPA phonemes first, then text aliases. */
    static boolean lineMatches(ChantLine line, String heard, List<String> heardIpa) {
        Pronunciation p = line.pronunciation();
        if (heardIpa != null && !heardIpa.isEmpty() && !p.ipa().isEmpty()) {
            String ipaText = String.join(" ", heardIpa);
            for (String templ : p.ipa()) {
                if (loosePhonetic(ipaText, templ)) return true;
            }
        }
        if (heard != null && !heard.isBlank()) {
            for (String alias : p.aliases()) {
                if (looseText(heard.toLowerCase(Locale.ROOT),
                        alias.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    static boolean looseText(String heard, String alias) {
        String h = normalize(heard);
        String a = normalize(alias);
        if (h.isEmpty() || a.isEmpty()) return false;
        // The whole line spoken (filler words around it are fine).
        if (h.contains(a)) return true;
        // Token coverage: most of the line's words must appear in the
        // utterance. This deliberately REPLACED the old `alias.contains(heard)`
        // substring rule, which matched a line on its first word alone (any
        // substring of the alias counted as a full line).
        String[] tokens = a.split(" ");
        if (tokens.length >= 2) {
            int hit = 0;
            for (String token : tokens) {
                if (!token.isEmpty() && h.contains(token)) hit++;
            }
            return (double) hit / tokens.length >= 0.75;
        }
        // Single-word lines: fuzzy full-string match.
        int dist = Levenshtein.distance(h, a);
        return 1.0 - (double) dist / Math.max(1, Math.max(h.length(), a.length())) >= 0.6;
    }

    /** Lowercase, strip punctuation, collapse whitespace — chant aliases and
     *  recognizer transcripts otherwise differ by trailing "!" etc. */
    static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Very lenient phonetic: normalized template's letters mostly appear in order. */
    static boolean loosePhonetic(String heardIpa, String template) {
        String h = heardIpa.replaceAll("[\\sˈˌː.]", "").toLowerCase(Locale.ROOT);
        String t = template.replaceAll("[\\sˈˌː.]", "").toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return false;
        if (h.contains(t)) return true;
        // Levenshtein-ish ratio on chars
        int dist = Levenshtein.distance(h, t);
        return 1.0 - (double) dist / Math.max(1, Math.max(h.length(), t.length())) >= 0.6;
    }
}
