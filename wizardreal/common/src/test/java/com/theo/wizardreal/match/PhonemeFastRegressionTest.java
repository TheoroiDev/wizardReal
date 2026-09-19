package com.theo.wizardreal.match;

import com.theo.voicecast.api.Pronunciation;
import com.theo.wizardreal.TestSpell;
import com.theo.wizardreal.api.Spell;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fixed-corpus snapshot regression for the S6 weighted phoneme matcher
 * (issue #29 acceptance criterion 3): replays the lab "fast gate" —
 * {@code run.ps1 fast} in the local-only ipa lab, which produced the locked
 * S6-FINAL baseline of <strong>162/977 hits</strong> on
 * {@code fixtures/heard-tokens.tsv} against {@code corpus/vocab.tsv} —
 * against the PRODUCTION {@link PhonemeMatcher} (weighted DP, COST_SCALE
 * 2.0 + clamp, 0.6 indels, jar-asset cost table).
 *
 * <p>Judgment parity with the lab bench ({@code ipa/bench/Bench.java fast()}):
 * a case HITS when {@code match(heard, candidates)} returns a spell whose id
 * equals the fixture's expected spell. The candidate set mirrors the lab
 * vocab one TestSpell per vocab ENTRY in file order (entry granularity keeps
 * exact-score tie order identical to the lab's entry iteration; every vocab
 * threshold is -1, so the default 0.6 applies). The fixture is parsed with
 * {@code split("\t", 7)} exactly like the lab bench — the 8th column (tier)
 * therefore merges into the front of the tokens column and flows through
 * {@code IpaText.normalizeTokens} identically to the lab run that produced
 * the baseline.
 *
 * <p>If this assertion fails, matcher semantics drifted from the lab-final
 * S6-FINAL working point (x2.0, positives 74.3% on the full v3 bench) —
 * re-run the lab fast gate and re-baseline deliberately, never silently.
 */
class PhonemeFastRegressionTest {

    private static final int EXPECTED_TOTAL = 977;
    private static final int EXPECTED_HITS = 162;

    @Test
    void fastGateSnapshotMatchesLabS6FinalBaseline() throws Exception {
        List<Spell> candidates = loadVocab(resource("phoneme_fast/vocab.tsv"));

        int hit = 0;
        int total = 0;
        List<String> mismatches = new ArrayList<>();
        for (String line : Files.readAllLines(resource("phoneme_fast/heard-tokens.tsv"),
                StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            // exact Bench.fast parsing (7-field limit: tier column merges into
            // the tokens column, exactly as in the lab baseline run)
            String[] c = line.split("\t", 7);
            if (c.length < 7) throw new IOException("bad fixture line (need 7 columns): " + line);
            String spellId = c[4];
            String tokens = c[6];
            total++;
            List<String> heard = tokens.isBlank() ? List.of() : List.of(tokens.split(" "));
            PhonemeMatcher.Match m = PhonemeMatcher.match(heard, candidates);
            boolean ok = m != null && m.spell().id().equals(spellId);
            if (ok) hit++;
            else if (mismatches.size() < 10) {
                mismatches.add(c[0] + ": expected " + spellId + ", got "
                        + (m == null ? "MISS" : m.spell().id() + " @" + m.score()));
            }
        }
        assertEquals(EXPECTED_TOTAL, total, "corpus size drifted");
        assertEquals(EXPECTED_HITS, hit, "fast-gate snapshot drifted from the lab S6-FINAL "
                + "baseline (162/977); first mismatches: " + mismatches);
    }

    /** One TestSpell per vocab ENTRY, file order (see class javadoc). */
    private static List<Spell> loadVocab(Path tsv) throws IOException {
        List<Spell> out = new ArrayList<>();
        for (String line : Files.readAllLines(tsv, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] c = line.split("\t", -1);
            if (c.length < 6) throw new IOException("bad vocab line (need 6 columns): " + line);
            float threshold = c[2].isBlank() ? -1f : Float.parseFloat(c[2].trim());
            List<String> ipa = new ArrayList<>();
            for (String p : c[3].split(";", -1)) {
                if (!p.isBlank()) ipa.add(p.trim());
            }
            out.add(new TestSpell(c[1].trim(),
                    new Pronunciation(c[0].trim(), ipa, List.of()), threshold, List.of()));
        }
        return out;
    }

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(PhonemeFastRegressionTest.class.getResource("/" + name).toURI());
    }
}
