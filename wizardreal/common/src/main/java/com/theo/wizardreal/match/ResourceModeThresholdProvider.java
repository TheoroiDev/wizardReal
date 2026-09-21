package com.theo.wizardreal.match;

import com.theo.voicecast.api.ThresholdHint;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mode-aware {@link PerModeThresholdProvider} backed by the calibration
 * resource {@code assets/voicecast/mode_thresholds.tsv} (issue #30 D3 — the
 * file ships in the voicecast jar next to {@code confusion_neighbors.tsv}).
 *
 * <p>C1b rework: the provider PRODUCES a {@link ThresholdHint} per
 * {@code (mode, spell)} — per-spell row &rarr; mode-wide row ({@code
 * spell = *}) &rarr; all-null hint (voicecast engine-calibration defaults;
 * D3's "缺省行回退"). Only the forward (CTC posterior) tier is table-driven
 * today; the phoneme and text tiers stay null until their own per-mode
 * recalibration lands (S9 validated the 0.65 text threshold unchanged across
 * all four candidate sets; future rows follow the S7b protocol). A missing
 * or malformed resource degrades to the all-null hint, never narrower
 * acceptance.
 *
 * <p>Nothing wires this provider into the live push yet (unchanged from the
 * pre-C1b state): the first batch of rows mirrors the shipped constants, so
 * wiring would be a no-op. Consumption starts when per-mode recalibration
 * data differs from the defaults (data-only change, issue #29 ④ follow-up).
 */
public final class ResourceModeThresholdProvider implements PerModeThresholdProvider {

    private static final String RESOURCE = "/assets/voicecast/mode_thresholds.tsv";

    private final Map<String, Float> rows;

    public ResourceModeThresholdProvider() {
        this(load(RESOURCE));
    }

    /** Test-visible constructor: explicit table. */
    ResourceModeThresholdProvider(Map<String, Float> rows) {
        this.rows = Map.copyOf(rows);
    }

    @Override
    public ThresholdHint thresholds(String mode) {
        return thresholds(mode, "*");
    }

    @Override
    public ThresholdHint thresholds(String mode, String spellId) {
        Float forward = row(mode, spellId);
        if (forward == null) forward = row(mode, "*");
        // Only the forward tier is table-driven; the other tiers keep the
        // engine-calibration defaults (null), never wider than the table.
        return new ThresholdHint(forward, null, null);
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
            return out; // degrade to defaults (documented fallback)
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
            return Map.of(); // unreadable: defaults everywhere
        }
        return out;
    }
}
