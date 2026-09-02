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
 * </pre>
 */
public final class WizardRealConfig {

    public enum FileMode { ALL, CASTABLE, OFF }

    public enum PushMode { ALL, CASTABLE, OFF }

    private static final String HEADER =
            "wizardreal configuration. Delete a key to fall back to its default.";

    private final FileMode fileMode;
    private final PushMode pushMode;

    private WizardRealConfig(FileMode fileMode, PushMode pushMode) {
        this.fileMode = fileMode;
        this.pushMode = pushMode;
    }

    public FileMode fileMode() {
        return fileMode;
    }

    public PushMode pushMode() {
        return pushMode;
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
                parsePushMode(toml.getString("wizardpedia", "pushMode", "all")));
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
                .save(file);
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
