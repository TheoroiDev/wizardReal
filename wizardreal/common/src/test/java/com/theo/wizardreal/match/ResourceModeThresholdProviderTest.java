package com.theo.wizardreal.match;

import com.theo.wizardreal.server.ServerVoiceCast;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Mode-aware threshold lookup (issue #30 D3): resource rows, wildcard
 *  defaults and the FORWARD_MATCH_THRESHOLD fallback chain. */
class ResourceModeThresholdProviderTest {

    @Test
    void shippedResourceMirrorsConstantsForEveryMode() {
        // First batch (S9/S6FINAL working point): every mode at the shipped
        // forward constant — the resource is live but behavior-neutral.
        ResourceModeThresholdProvider provider = new ResourceModeThresholdProvider();
        for (String mode : new String[]{"OPEN", "CHANT_CONFIRM", "PRACTICE_CONFIRM", "GRAY_NARROW"}) {
            PerModeThresholdProvider.Thresholds t = provider.thresholds(mode);
            assertEquals(ServerVoiceCast.FORWARD_MATCH_THRESHOLD, t.forward(), 1e-6f, mode);
            assertEquals(PhonemeMatcher.MATCH_THRESHOLD, t.phoneme(), 1e-6f, mode);
            assertEquals(SpellMatcher.MATCH_THRESHOLD, t.text(), 1e-6f, mode);
        }
    }

    @Test
    void unknownModeFallsBackToForwardConstant() {
        ResourceModeThresholdProvider provider = new ResourceModeThresholdProvider();
        PerModeThresholdProvider.Thresholds t = provider.thresholds("UNDECLARED");
        assertEquals(ServerVoiceCast.FORWARD_MATCH_THRESHOLD, t.forward(), 1e-6f);
    }

    @Test
    void modeRowOverridesConstant() {
        ResourceModeThresholdProvider provider = new ResourceModeThresholdProvider(Map.of(
                "CHANT_CONFIRM\t*", 0.07f), 0.6f, 0.65f);
        assertEquals(0.07f, provider.thresholds("chant_confirm").forward(), 1e-6f); // case-insensitive
        assertEquals(ServerVoiceCast.FORWARD_MATCH_THRESHOLD, provider.thresholds("OPEN").forward(), 1e-6f);
    }

    @Test
    void perSpellRowOverridesModeRow() {
        ResourceModeThresholdProvider provider = new ResourceModeThresholdProvider(Map.of(
                "GRAY_NARROW\t*", 0.08f,
                "GRAY_NARROW\twizardreal:falsum", 0.05f), 0.6f, 0.65f);
        assertEquals(0.05f, provider.thresholds("GRAY_NARROW", "wizardreal:falsum").forward(), 1e-6f);
        assertEquals(0.08f, provider.thresholds("GRAY_NARROW", "wizardreal:ignis").forward(), 1e-6f);
        // spell-aware lookup degrades to the mode row for unknown spells and to
        // the plain mode lookup when no spell context exists.
        assertEquals(0.08f, provider.thresholds("GRAY_NARROW").forward(), 1e-6f);
    }

    @Test
    void blankAndNullModesFallBackToConstants() {
        ResourceModeThresholdProvider provider = new ResourceModeThresholdProvider(Map.of(
                "OPEN\t*", 0.09f), 0.6f, 0.65f);
        assertEquals(ServerVoiceCast.FORWARD_MATCH_THRESHOLD, provider.thresholds(null).forward(), 1e-6f);
        assertEquals(ServerVoiceCast.FORWARD_MATCH_THRESHOLD, provider.thresholds("  ").forward(), 1e-6f);
        assertEquals(ServerVoiceCast.FORWARD_MATCH_THRESHOLD,
                provider.thresholds(null, "wizardreal:ignis").forward(), 1e-6f);
    }

    @Test
    void fullVocabularyDefaultIsUnchangedForAllModes() {
        // The P6 default provider ignores the mode entirely — the pre-#30
        // behavior; the spell-aware default method must degrade to it.
        PerModeThresholdProvider provider = PerModeThresholdProvider.fullVocabulary();
        for (String mode : new String[]{"OPEN", "CHANT_CONFIRM", "GRAY_NARROW"}) {
            PerModeThresholdProvider.Thresholds t = provider.thresholds(mode, "wizardreal:ignis");
            assertEquals(ServerVoiceCast.FORWARD_MATCH_THRESHOLD, t.forward(), 1e-6f);
            assertEquals(PhonemeMatcher.MATCH_THRESHOLD, t.phoneme(), 1e-6f);
            assertEquals(SpellMatcher.MATCH_THRESHOLD, t.text(), 1e-6f);
        }
    }
}
