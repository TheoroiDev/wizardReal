package com.theo.wizardreal.config;

import com.theo.voicecast.config.Toml;
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
 * failBlindness = true                    # failed chants apply stacking darkness (D-C5)
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
 * g2pDrafts = false                       # fill vocabulary entries without curated ipa with
 *                                         # G2P drafts at push time (unverified templates;
 *                                         # 2026-09 backtest: no CTC gain until scoring is
 *                                         # calibrated - keep off by default)
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

    private static final String HEADER =
            "wizardreal configuration. Delete a key to fall back to its default.";

    private final FileMode fileMode;
    private final PushMode pushMode;
    private final ChantSettings chant;
    private final LearningSettings learning;
    private final VoiceSettings voice;
    private static volatile WizardRealConfig cache;

    private WizardRealConfig(FileMode fileMode, PushMode pushMode, ChantSettings chant,
                             LearningSettings learning, VoiceSettings voice) {
        this.fileMode = fileMode;
        this.pushMode = pushMode;
        this.chant = chant;
        this.learning = learning;
        this.voice = voice;
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
                        toml.getBool("chant", "failBlindness", true),
                        (float) toml.getDouble("chant", "failBlindnessBase", 3.0),
                        (float) toml.getDouble("chant", "failBlindnessStep", 3.0),
                        (int) toml.getInt("chant", "failBlindnessWindowSeconds", 30)),
                new LearningSettings(
                        toml.getBool("learning", "overlearning", false),
                        (float) toml.getDouble("learning", "skipChantThreshold", 0.5),
                        (float) toml.getDouble("learning", "knownThreshold", 10.0)),
                new VoiceSettings(parseLanguages(
                        toml.getString("voice", "languages", "")),
                        toml.getBool("voice", "g2pDrafts", false)));        if (!existed) {
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
                .setBool("chant", "failBlindness", true)
                .setDouble("chant", "failBlindnessBase", 3.0)
                .setDouble("chant", "failBlindnessStep", 3.0)
                .setInt("chant", "failBlindnessWindowSeconds", 30)
                .setBool("learning", "overlearning", false)
                .setDouble("learning", "skipChantThreshold", 0.5)
                .setDouble("learning", "knownThreshold", 10.0)
                .setString("voice", "languages", "")
                .setBool("voice", "g2pDrafts", false)
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
}
