package com.theo.wizardreal.config;

import com.theo.voicecast.config.Toml;
import com.theo.wizardreal.g2p.Readings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * wizardreal's own config file: {@code config/wizardreal/wizardreal.toml}
 * (parsed/written with the voicecast {@link Toml} mini-reader — no extra
 * dependencies). Loaded separately by the client (file export switches) and
 * the server (push switches); missing keys fall back to defaults and a
 * missing file is created with commented defaults.
 *
 * <pre>
 * [spellCatalog]
 * fileMode = "all" | "castable" | "off"   # spell_catalog.json export scope (client)
 *
 * [wizardpedia]
 * pushMode = "all" | "castable" | "off"   # wizardpedia:catalog push scope (server)
 *
 * [chant]
 * timeoutMode = "per_line" | "fixed"      # chant timeout: 10s x lines (default) or a fixed cap
 * perLineSeconds = 10                     # per_line mode: seconds per chant line
 * fixedSeconds = 90                       # fixed mode: total chant timeout
 * failBlindness = false                   # failed chants apply stacking darkness (D-C5;
 *                                         # default OFF since #41 — the penalty stacked on
 *                                         # accent/noise misrecognition, not on mistakes)
 * failBlindnessBase = 3.0                 # first failure: darkness seconds
 * failBlindnessStep = 3.0                 # extra seconds per consecutive failure
 * failBlindnessWindowSeconds = 30         # consecutive-failure window
 *
 * [learning]
 * overlearning = false                    # t>100 keeps growing power (PvP servers opt in)
 * skipChantThreshold = 0.5                # skip-cast (破弃) requires t >= 50%
 * knownThreshold = 10                     # known = t strictly above this
 *
 * [voice]
 * languages = ""                          # enabled language buckets, csv (en,zh,ja,ko); "" = all
 * g2pDrafts = true                        # fill vocabulary entries without curated ipa with
 *                                         # G2P drafts at push time (strict per-alias;
 *                                         # unconvertible scripts — e.g. en, no Tier-2 —
 *                                         # produce no draft, fail-closed; voiceCast#47)
 *
 * [chantReadings]                         # chant annotation layer (R-B: D2/D4/D8)
 * hud = true                              # ChantHud current-line reading row (D4: default on)
 * languagePolicy = "auto"                 # auto | off | selected | all (D2; same semantics
 *                                         # as the wizardpedia book page, independent file)
 * languages = ""                          # selected policy: csv of language buckets ("ja,zh")
 * pinyinStyle = "marks"                   # marks | numbers (D8) — applied when the spell
 *                                         # catalog is DERIVED (server start / login sync),
 *                                         # so a change takes effect at the next rebuild
 * </pre>
 */
public final class WizardRealConfig {

    public enum FileMode { ALL, CASTABLE, OFF }

    public enum PushMode { ALL, CASTABLE, OFF }

    /** Fallback game dir for config loads without a server handle (headless/unit). */
    public static final Path DEFAULT_GAME_DIR = Path.of(".");

    /** {@code [chant]} section: timeout mode + failed-chant darkness (D-C4/D-C5). */
    public record ChantSettings(String timeoutMode, int perLineSeconds, int fixedSeconds,
                                boolean failBlindness, float failBlindnessBase, float failBlindnessStep,
                                int failBlindnessWindowSeconds) {
        /** Chant timeout: per_line = seconds x line count (default), fixed = total cap. */
        public long timeoutFor(int lineCount) {
            if ("fixed".equalsIgnoreCase(timeoutMode)) return fixedSeconds * 1000L;
            return (long) perLineSeconds * Math.max(1, lineCount) * 1000L;
        }
    }

    /** {@code [learning]} section (D4/D-D2): overlearning switch, skip-cast
     * mastery threshold (fraction of 100), known derivation threshold. */
    public record LearningSettings(boolean overlearning, float skipChantThreshold, float knownThreshold) {
        public static final LearningSettings DEFAULT = new LearningSettings(false, 0.5f, 10f);
    }

    /** {@code [voice]} section (误触发治理): the enabled language buckets for
     * the voice matcher chain and recognizer vocabulary (empty = all), and the
     * G2P draft switch for vocabulary entries without curated templates. */
    public record VoiceSettings(List<String> languages, boolean g2pDrafts) {
        public static final VoiceSettings DEFAULT = new VoiceSettings(List.of(), false);

        /** Enabled language codes (lowercase); empty set = no restriction. */
        public Set<String> enabledLanguages() {
            if (languages == null || languages.isEmpty()) return Set.of();
            Set<String> out = new LinkedHashSet<>();
            for (String lang : languages) {
                if (lang != null && !lang.isBlank()) out.add(lang.trim().toLowerCase(Locale.ROOT));
            }
            return out;
        }
    }

    /** {@code [chantReadings]} section (annotation layer R-B, D2/D4/D8):
     *  HUD annotation switch, language policy and pinyin display style. The
     *  same policy semantics as the wizardpedia book config, implemented
     *  independently here (zero cross-repo dependency). */
    public record ReadingsSettings(boolean hud, LanguagePolicy languagePolicy, List<String> languages,
                                   Readings.PinyinStyle pinyinStyle) {
        public static final ReadingsSettings DEFAULT =
                new ReadingsSettings(true, LanguagePolicy.AUTO, List.of(), Readings.PinyinStyle.MARKS);

        /** D2 language policy: auto = annotate only languages that are NOT
         *  the MC display language; off; selected = the configured bucket
         *  set; all = everything with a present reading. */
        public enum LanguagePolicy { AUTO, OFF, SELECTED, ALL }

        /** Configured buckets for the {@code selected} policy (lowercase). */
        public Set<String> selectedLanguages() {
            if (languages == null || languages.isEmpty()) return Set.of();
            Set<String> out = new LinkedHashSet<>();
            for (String lang : languages) {
                if (lang != null && !lang.isBlank()) out.add(lang.trim().toLowerCase(Locale.ROOT));
            }
            return out;
        }
    }

    private static final String HEADER =
            "wizardreal configuration. Delete a key to fall back to its default.";

    private final FileMode fileMode;
    private final PushMode pushMode;
    private final ChantSettings chant;
    private final LearningSettings learning;
    private final VoiceSettings voice;
    private final ReadingsSettings chantReadings;
    private static volatile WizardRealConfig cache;

    private WizardRealConfig(FileMode fileMode, PushMode pushMode, ChantSettings chant,
                             LearningSettings learning, VoiceSettings voice, ReadingsSettings chantReadings) {
        this.fileMode = fileMode;
        this.pushMode = pushMode;
        this.chant = chant;
        this.learning = learning;
        this.voice = voice;
        this.chantReadings = chantReadings;
    }

    /** Cached load for per-cast/per-utterance readers (file reread only when cleared). */
    public static WizardRealConfig loadCached(Path gameDir) {
        WizardRealConfig c = cache;
        if (c == null) {
            c = load(gameDir);
            cache = c;
        }
        return c;
    }

    public FileMode fileMode() {
        return fileMode;
    }

    public PushMode pushMode() {
        return pushMode;
    }

    public ChantSettings chant() {
        return chant;
    }

    public LearningSettings learning() {
        return learning;
    }

    public VoiceSettings voice() {
        return voice;
    }

    public ReadingsSettings chantReadings() {
        return chantReadings;
    }

    public static Path file(Path gameDir) {
        return gameDir.resolve("config").resolve("wizardreal").resolve("wizardreal.toml");
    }

    public static WizardRealConfig load(Path gameDir) {
        Path file = file(gameDir);
        boolean existed = Files.isRegularFile(file);
        Toml toml = Toml.load(file);
        WizardRealConfig config = new WizardRealConfig(
                parseFileMode(toml.getString("spellCatalog", "fileMode", "all")),
                parsePushMode(toml.getString("wizardpedia", "pushMode", "all")),
                new ChantSettings(
                        parseTimeoutMode(toml.getString("chant", "timeoutMode", "per_line")),
                        (int) toml.getInt("chant", "perLineSeconds", 10),
                        (int) toml.getInt("chant", "fixedSeconds", 90),
                        toml.getBool("chant", "failBlindness", false),
                        (float) toml.getDouble("chant", "failBlindnessBase", 3.0),
                        (float) toml.getDouble("chant", "failBlindnessStep", 3.0),
                        (int) toml.getInt("chant", "failBlindnessWindowSeconds", 30)),
                new LearningSettings(
                        toml.getBool("learning", "overlearning", false),
                        (float) toml.getDouble("learning", "skipChantThreshold", 0.5),
                        (float) toml.getDouble("learning", "knownThreshold", 10.0)),
                new VoiceSettings(parseLanguages(
                        toml.getString("voice", "languages", "")),
                        toml.getBool("voice", "g2pDrafts", true)),
                new ReadingsSettings(
                        toml.getBool("chantReadings", "hud", true),
                        parseLanguagePolicy(toml.getString("chantReadings", "languagePolicy", "auto")),
                        parseLanguages(toml.getString("chantReadings", "languages", "")),
                        parsePinyinStyle(toml.getString("chantReadings", "pinyinStyle", "marks"))));
        if (!existed) {
            writeDefaults(file);
        }
        return config;
    }

    private static void writeDefaults(Path file) {
        new Toml()
                .setComment(HEADER)
                .setString("spellCatalog", "fileMode", "all")
                .setString("wizardpedia", "pushMode", "all")
                .setString("chant", "timeoutMode", "per_line")
                .setInt("chant", "perLineSeconds", 10)
                .setInt("chant", "fixedSeconds", 90)
                .setBool("chant", "failBlindness", false)
                .setDouble("chant", "failBlindnessBase", 3.0)
                .setDouble("chant", "failBlindnessStep", 3.0)
                .setInt("chant", "failBlindnessWindowSeconds", 30)
                .setBool("learning", "overlearning", false)
                .setDouble("learning", "skipChantThreshold", 0.5)
                .setDouble("learning", "knownThreshold", 10.0)
                .setString("voice", "languages", "")
                .setBool("voice", "g2pDrafts", true)
                .setBool("chantReadings", "hud", true)
                .setString("chantReadings", "languagePolicy", "auto")
                .setString("chantReadings", "languages", "")
                .setString("chantReadings", "pinyinStyle", "marks")
                .save(file);
    }

    /** Comma-separated language codes → list ("" = all languages). */
    private static List<String> parseLanguages(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            if (!part.isBlank()) out.add(part.trim());
        }
        return out;
    }

    private static String parseTimeoutMode(String value) {
        if (value == null) return "per_line";
        return "fixed".equalsIgnoreCase(value.trim()) ? "fixed" : "per_line";
    }

    private static FileMode parseFileMode(String value) {
        try {
            return FileMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FileMode.ALL;
        }
    }

    private static PushMode parsePushMode(String value) {
        try {
            return PushMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PushMode.ALL;
        }
    }

    private static ReadingsSettings.LanguagePolicy parseLanguagePolicy(String value) {
        if (value == null) return ReadingsSettings.LanguagePolicy.AUTO;
        try {
            return ReadingsSettings.LanguagePolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ReadingsSettings.LanguagePolicy.AUTO;
        }
    }

    private static Readings.PinyinStyle parsePinyinStyle(String value) {
        return value != null && "numbers".equalsIgnoreCase(value.trim())
                ? Readings.PinyinStyle.NUMBERS : Readings.PinyinStyle.MARKS;
    }
}
