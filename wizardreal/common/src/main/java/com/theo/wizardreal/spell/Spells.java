package com.theo.wizardreal.spell;

/**
 * Built-in Java spells. As of M6 every shipped spell is defined as datapack
 * JSON ({@code data/wizardreal/voicecast/spells/*.json}) composed from the
 * effect primitives in {@code com.theo.wizardreal.effect} — see
 * {@code docs/spell_json.md} (workspace-root docs/). This hook stays for future built-ins that need
 * bespoke Java logic; datapack definitions with the same id override them.
 */
public final class Spells {

    private Spells() {}

    /** Kept for symmetry with the reload flow ( {@code SpellDataLoader} ). */
    public static void register() {
        // no built-in Java spells in M6; all content is datapack-driven
    }
}
