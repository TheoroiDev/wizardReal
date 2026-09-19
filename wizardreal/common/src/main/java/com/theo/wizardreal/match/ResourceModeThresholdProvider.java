package com.theo.wizardreal.match;

import com.theo.wizardreal.server.ServerVoiceCast;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mode-aware {@link PerModeThresholdProvider} backed by the calibration
 * resource {@code assets/voicecast/mode_threshold.tsv} (issue #30 D3 — the
 * file ships in the voicecast jar next to {@code confusion_neighbors.tsv}).
 *
 * <p>Lookup per {@code (mode, spell)}: per-spell row &rarr; mode-wide row
 * ({@code spell = *}) &rarr; shipped constants ({@code FORWARD_MATCH_
 * THRESHOLD} for the forward tier — D3's "缺省行回退"). Only the forward
 * (CTC posterior) tier is table-driven today; the phoneme and text tiers
 * return their shipped constants until their own per-mode recalibration
 * lands (S9 validated the 0.65 text threshold unchanged across all four
 * candidate sets; future rows follow the S7b protocol — same-protocol
 * negatives, 1% quantile + 0.03). A missing or malformed resource degrades
 * to the shipped constants for every mode, never wider acceptance.
 *
 * <p>Nothing wires this provider into the live matcher chain yet: the first
 * batch of rows mirrors the shipped constants, so wiring would be a no-op.
 * Consumption starts when per-mode recalibration data differs from the
 * constants (data-only change, issue #29 ④ follow-up).
 */
public final class ResourceModeThresholdProvider implements PerModeThresholdProvider {

    private static final String RESOURCE = "/assets/voicecast/mode_thresholds.tsv";

    private final Map<String, Float> rows;
    private final float phonemeThreshold;
    private final float textThreshold;

    public ResourceModeThresholdProvider() {
        this(load(RESOURCE), PhonemeMatcher.MATCH_THRESHOLD, SpellMatcher.MATCH_THRESHOLD);
    }

    /** Test-visible constructor: explicit table + tier constants. */
    ResourceModeThresholdProvider(Map<String, Float> rows, float phonemeThreshold, float textThreshold) {
        this.rows = Map.copyOf(rows);
        this.phonemeThreshold = phonemeThreshold;
        this.textThreshold = textThreshold;
    }

    @Override
    public Thresholds thresholds(String mode) {
        return thresholds(mode, "*");
    }

    @Override
    public Thresholds thresholds(String mode, String spellId) {
        Float forward = row(mode, spellId);
        if (forward == null) forward = row(mode, "*");
        float fw = forward != null ? forward : ServerVoiceCast.FORWARD_MATCH_THRESHOLD;
        return new Thresholds(fw, phonemeThreshold, textThreshold);
    }

    /** Row value for a normalized {@code (mode, spell)} key, or null. */
    private Float row(String mode, String spell) {
        if (mode == null || mode.isBlank()) return null;
        String key = key(mode, spell == null || spell.isBlank() ? "*" : spell);
        return rows.get(key);
    }

    private static String key(String mode, String spell) {
        return mode.trim().toUpperCase(Locale.ROOT) + "\t" + spell.trim();
    }

    private static Map<String, Float> load(String resource) {
        Map<String, Float> out = new HashMap<>();
        InputStream stream = ResourceModeThresholdProvider.class.getResourceAsStream(resource);
        if (stream == null) {
            // Cross-loader safety net: the file lives in the voicecast jar, so
            // resolve through a voicecast-owned class when the own loader
            // cannot see it (isolated module layers).
            stream = com.theo.voicecast.server.VoiceCastServer.class.getResourceAsStream(resource);
        }
        if (stream == null) {
            return out; // degrade to shipped constants (documented fallback)
        }
        try (InputStream in = stream) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] cols = line.split("\t");
                if (cols.length < 3) continue;
                try {
                    out.put(key(cols[0], cols[1].isBlank() ? "*" : cols[1]), Float.parseFloat(cols[2].trim()));
                } catch (NumberFormatException ignored) {
                    // malformed row: skip (degrades to the fallback chain)
                }
            }
        } catch (Exception ignored) {
            return Map.of(); // unreadable: shipped constants everywhere
        }
        return out;
    }
}
