package com.theo.wizardreal.match;

import com.theo.voicecast.api.ThresholdHint;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Mode-aware threshold lookup (issue #30 D3), C1b rework: the provider
 *  PRODUCES a {@link ThresholdHint} for the vocabulary push — resource rows,
 *  wildcard defaults, all-null fallback to the voicecast calibration. */
class ResourceModeThresholdProviderTest {

    @Test
    void shippedResourceMirrorsDefaultsForEveryMode() {
        // First batch (S9/S6FINAL working point): every mode row mirrors the
        // shipped forward default — the resource is live but behavior-neutral.
        ResourceModeThresholdProvider provider = new ResourceModeThresholdProvider();
        for (String mode : new String[]{"OPEN", "CHANT_CONFIRM", "PRACTICE_CONFIRM", "GRAY_NARROW"}) {
            ThresholdHint hint = provider.thresholds(mode);
            assertEquals(0.10f, hint.forward(), 1e-6f, mode); // shipped row = the old 0.10 constant
            assertNull(hint.phoneme(), mode);
            assertNull(hint.text(), mode);
        }
    }

    @Test
    void unknownModeFallsBackToWildcardRow() {
        ResourceModeThresholdProvider provider = new ResourceModeThresholdProvider();
        assertNull(provider.thresholds("UNDECLARED").forward(), "no row -> calibration default");
    }

    @Test
    void modeRowOverridesDefault() {
        ResourceModeThresholdProvider provider = new ResourceModeThresholdProvider(Map.of(
                "CHANT_CONFIRM\t*", 0.07f));
        assertEquals(0.07f, provider.thresholds("chant_confirm").forward(), 1e-6f); // case-insensitive
        assertNull(provider.thresholds("OPEN").forward());
    }

    @Test
    void perSpellRowOverridesModeRow() {
        ResourceModeThresholdProvider provider = new ResourceModeThresholdProvider(Map.of(
                "GRAY_NARROW\t*", 0.08f,
                "GRAY_NARROW\twizardreal:falsum", 0.05f));
        assertEquals(0.05f, provider.thresholds("GRAY_NARROW", "wizardreal:falsum").forward(), 1e-6f);
        assertEquals(0.08f, provider.thresholds("GRAY_NARROW", "wizardreal:ignis").forward(), 1e-6f);
        // spell-aware lookup degrades to the mode row for unknown spells and to
        // the plain mode lookup when no spell context exists.
        assertEquals(0.08f, provider.thresholds("GRAY_NARROW").forward(), 1e-6f);
    }

    @Test
    void blankAndNullModesFallBackToDefaults() {
        ResourceModeThresholdProvider provider = new ResourceModeThresholdProvider(Map.of(
                "OPEN\t*", 0.09f));
        assertNull(provider.thresholds(null).forward());
        assertNull(provider.thresholds("  ").forward());
        assertNull(provider.thresholds(null, "wizardreal:ignis").forward());
    }

    @Test
    void fullVocabularyDefaultYieldsAllNullHints() {
        // The P6 default provider ignores the mode entirely — every tier keeps
        // the voicecast engine-calibration default (the pre-C1b constants).
        PerModeThresholdProvider provider = PerModeThresholdProvider.fullVocabulary();
        for (String mode : new String[]{"OPEN", "CHANT_CONFIRM", "GRAY_NARROW"}) {
            ThresholdHint hint = provider.thresholds(mode, "wizardreal:ignis");
            assertNull(hint.forward());
            assertNull(hint.phoneme());
            assertNull(hint.text());
        }
    }
}
