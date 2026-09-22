package com.theo.wizardreal.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code [chantReadings]} config (R-B, D2/D4/D8): HUD switch, language
 *  policy, selected bucket set and the pinyin display style. */
class WizardRealConfigReadingsTest {

    @TempDir
    Path gameDir;

    @Test
    void freshInstallWritesReadingsDefaults() throws Exception {
        WizardRealConfig c = WizardRealConfig.load(gameDir);
        WizardRealConfig.ReadingsSettings r = c.chantReadings();
        assertTrue(r.hud()); // D4: default on
        assertEquals(WizardRealConfig.ReadingsSettings.LanguagePolicy.AUTO, r.languagePolicy());
        assertEquals(com.theo.wizardreal.g2p.Readings.PinyinStyle.MARKS, r.pinyinStyle());
        assertTrue(r.selectedLanguages().isEmpty());
        String toml = Files.readString(WizardRealConfig.file(gameDir)).replace("\r\n", "\n");
        assertTrue(toml.contains("[chantReadings]"));
        assertTrue(toml.contains("pinyinStyle"));
    }

    @Test
    void parsesOverrides() throws Exception {
        Files.createDirectories(WizardRealConfig.file(gameDir).getParent());
        Files.writeString(WizardRealConfig.file(gameDir), """
                [chantReadings]
                hud = false
                languagePolicy = "selected"
                languages = "ja, KO"
                pinyinStyle = "numbers"
                """);
        WizardRealConfig.ReadingsSettings r = WizardRealConfig.load(gameDir).chantReadings();
        assertTrue(!r.hud());
        assertEquals(WizardRealConfig.ReadingsSettings.LanguagePolicy.SELECTED, r.languagePolicy());
        assertEquals(com.theo.wizardreal.g2p.Readings.PinyinStyle.NUMBERS, r.pinyinStyle());
        // bucket set normalizes to lowercase two-letter codes
        assertEquals(java.util.Set.of("ja", "ko"), r.selectedLanguages());
    }

    @Test
    void invalidValuesFallBackToDefaults() throws Exception {
        Files.createDirectories(WizardRealConfig.file(gameDir).getParent());
        Files.writeString(WizardRealConfig.file(gameDir), """
                [chantReadings]
                languagePolicy = "sometimes"
                pinyinStyle = "circles"
                """);
        WizardRealConfig.ReadingsSettings r = WizardRealConfig.load(gameDir).chantReadings();
        assertEquals(WizardRealConfig.ReadingsSettings.LanguagePolicy.AUTO, r.languagePolicy());
        assertEquals(com.theo.wizardreal.g2p.Readings.PinyinStyle.MARKS, r.pinyinStyle());
        assertTrue(r.hud()); // key absent -> default
    }

    @Test
    void missingSectionFallsBackToDefaults() throws Exception {
        Files.createDirectories(WizardRealConfig.file(gameDir).getParent());
        Files.writeString(WizardRealConfig.file(gameDir), "[chant]\ntimeoutMode = \"fixed\"\n");
        WizardRealConfig.ReadingsSettings r = WizardRealConfig.load(gameDir).chantReadings();
        assertEquals(WizardRealConfig.ReadingsSettings.DEFAULT, r);
    }
}
