package com.theo.wizardreal.config;

import com.theo.voicecast.config.Toml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

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

    private static final String HEADER =
            "wizardreal configuration. Delete a key to fall back to its default.";

    private final FileMode fileMode;
    private final PushMode pushMode;
    private final ChantSettings chant;

    private WizardRealConfig(FileMode fileMode, PushMode pushMode, ChantSettings chant) {
        this.fileMode = fileMode;
        this.pushMode = pushMode;
        this.chant = chant;
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
                        (int) toml.getInt("chant", "failBlindnessWindowSeconds", 30)));        if (!existed) {
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
                .save(file);
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
