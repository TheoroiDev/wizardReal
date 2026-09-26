package com.theo.wizardreal.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code [chant]} config: timeout dual mode (D-C4) + failure darkness (D-C5). */
class WizardRealConfigChantTest {

    @TempDir
    Path gameDir;

    @Test
    void freshInstallWritesChantDefaults() throws Exception {
        WizardRealConfig c = WizardRealConfig.load(gameDir);
        WizardRealConfig.ChantSettings chant = c.chant();
        assertEquals("per_line", chant.timeoutMode());
        assertEquals(10, chant.perLineSeconds());
        assertEquals(90, chant.fixedSeconds());
        // #41: the darkness penalty defaults OFF — it stacked on accent/noise
        // misrecognition. Opt back in via the config key.
        assertFalse(chant.failBlindness());
        assertEquals(3.0f, chant.failBlindnessBase());
        assertEquals(3.0f, chant.failBlindnessStep());
        assertEquals(30, chant.failBlindnessWindowSeconds());
        // The written file carries the section for user editing.
        String toml = java.nio.file.Files.readString(WizardRealConfig.file(gameDir)).replace("\r\n", "\n");
        assertTrue(toml.contains("[chant]"));
        assertTrue(toml.contains("timeoutMode"));
    }

    @Test
    void failBlindnessOptsBackIn() throws Exception {
        java.nio.file.Files.createDirectories(WizardRealConfig.file(gameDir).getParent());
        java.nio.file.Files.writeString(WizardRealConfig.file(gameDir), """
                [chant]
                failBlindness = true
                """);
        WizardRealConfig c = WizardRealConfig.load(gameDir);
        assertTrue(c.chant().failBlindness());
    }

    @Test
    void perLineTimeoutScalesWithLineCount() {
        WizardRealConfig.ChantSettings chant = new WizardRealConfig.ChantSettings("per_line", 10, 90, true, 3, 3, 30);
        assertEquals(30_000L, chant.timeoutFor(3));
        assertEquals(60_000L, chant.timeoutFor(6));
        assertEquals(10_000L, chant.timeoutFor(0)); // clamped to at least one line
    }

    @Test
    void fixedTimeoutIgnoresLineCount() {
        WizardRealConfig.ChantSettings chant = new WizardRealConfig.ChantSettings("fixed", 10, 90, true, 3, 3, 30);
        assertEquals(90_000L, chant.timeoutFor(3));
        assertEquals(90_000L, chant.timeoutFor(9));
    }

    @Test
    void invalidTimeoutModeFallsBackToPerLine() throws Exception {
        java.nio.file.Files.createDirectories(WizardRealConfig.file(gameDir).getParent());
        java.nio.file.Files.writeString(WizardRealConfig.file(gameDir), """
                [chant]
                timeoutMode = "sometimes"
                """);
        WizardRealConfig c = WizardRealConfig.load(gameDir);
        assertEquals("per_line", c.chant().timeoutMode());
    }

    @Test
    void darknessDurationStacksWithinWindow() {
        // base + step x (count-1): 1st failure 3s, 2nd 6s, 3rd 9s (config math).
        WizardRealConfig.ChantSettings chant = new WizardRealConfig.ChantSettings("per_line", 10, 90, true, 3, 3, 30);
        assertEquals(3.0f, chant.failBlindnessBase() + chant.failBlindnessStep() * 0);
        assertEquals(6.0f, chant.failBlindnessBase() + chant.failBlindnessStep() * 1);
        assertEquals(9.0f, chant.failBlindnessBase() + chant.failBlindnessStep() * 2);
    }
}
