package com.theo.wizardreal.match;

import java.util.Locale;

/**
 * Score bands for chant performance (wizardReal#43, consuming the voiceCast#48
 * W3 graded {@code LineMatch.score}): a completed line's score grades into
 * PERFECT / EXCELLENT / PASS — {@code pass} doubles as the success line.
 *
 * <p>Two-axis rule (防异化): the band grades PERFORMANCE and powers the
 * resonance reward only — it never changes whether a line or spell succeeds
 * (success stays the matcher's boolean verdict, as before #43). The cut
 * points are data, not constants: they ride the
 * {@link PerModeThresholdProvider#scoreBands(String)} hook so per-mode
 * calibration can move them (practice ships a STRICT set).
 *
 * @param perfect   score at/above which a line is perfect (default 0.95)
 * @param excellent score at/above which a line is excellent (default 0.85)
 * @param pass      the success line (default 0.70)
 */
public record ScoreBands(float perfect, float excellent, float pass) {

    public enum Band { PERFECT, EXCELLENT, PASS, MISS }

    public static final ScoreBands DEFAULT = new ScoreBands(0.95f, 0.85f, 0.70f);
    /** Practice grading: every cut raised — practice is where you rehearse perfection. */
    public static final ScoreBands STRICT = new ScoreBands(0.98f, 0.92f, 0.85f);

    public ScoreBands {
        if (!(perfect >= excellent && excellent >= pass && pass >= 0f && perfect <= 1f)) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "inverted score bands: perfect=%f excellent=%f pass=%f",
                            perfect, excellent, pass));
        }
    }

    /** Grade one completed-line score. */
    public Band bandOf(float score) {
        if (score >= perfect) return Band.PERFECT;
        if (score >= excellent) return Band.EXCELLENT;
        if (score >= pass) return Band.PASS;
        return Band.MISS;
    }

    /** Mean of the per-line scores collected for one chant (0 when empty). */
    public static float average(Iterable<Float> scores) {
        float sum = 0f;
        int n = 0;
        for (Float s : scores) {
            sum += s;
            n++;
        }
        return n == 0 ? 0f : sum / n;
    }
}
