package com.theo.wizardreal.sound;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Sound event ids for WizardReal spell audio ({@code cast.<spell>} +
 * {@code impact.<spell>}). Actual {@link net.minecraft.sounds.SoundEvent}
 * registration happens per loader ({@code Registry.register} on Fabric,
 * {@code DeferredRegister} on Forge) iterating {@link #IDS}; playback is
 * referenced from datapack spell JSON via {@code wizardreal:<id>}.
 *
 * <p>Files live in {@code assets/wizardreal/sounds/spell/<id with . -> _>.ogg},
 * wired by {@code assets/wizardreal/sounds.json}. Generated via the workspace-root tools/sfx
 * (synth + Kenney CC0 + edge-tts voices, see AGENTS-wizardreal.md (workspace root)).
 */
public final class WizardRealSounds {

    private WizardRealSounds() {}

    public static final List<String> IDS = List.of(
            // ----- casts -----
            "cast.ignis", "cast.fulmen", "cast.vitae", "cast.aegis", "cast.ictus",
            "cast.explosion", "cast.excalibur", "cast.dragon_slave",
            "cast.arcanum", "cast.gaia", "cast.mare", "cast.mortis", "cast.sanctus",
            "cast.semina", "cast.tempest", "cast.umbra", "cast.ventus",
            // ----- impacts -----
            "impact.ignis", "impact.fulmen", "impact.vitae", "impact.aegis",
            "impact.ictus", "impact.explosion", "impact.excalibur", "impact.dragon_slave");

    public static ResourceLocation id(String soundId) {
        return new ResourceLocation("wizardreal", soundId);
    }
}
