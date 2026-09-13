package com.theo.wizardreal.api;

import com.theo.wizardreal.effect.SpellEffect;
import java.util.List;

/**
 * One tier of the chant-stage ladder (magic_eco 03): reaching this tier
 * replaces the spell's base {@code effects} with {@link #effects} and may
 * override the mana cost / cooldown for casts resolved at this tier.
 *
 * <p>Reachability is dual-gated: the player must have completed at least
 * {@code afterLines} chant lines (including the L1 entry line) AND hold a
 * mastery {@code t >= masteryThreshold} (learning percent, 0-100).
 *
 * @param afterLines      completed chant lines required (ascending across stages)
 * @param masteryThreshold mastery percent 0-100 required to resolve this tier
 * @param effects         effect list replacing the base effects for this tier
 * @param manaCost        nullable mana override (falls back to the spell's base cost)
 * @param cooldownTicks   nullable cooldown override (falls back to the spell's base cooldown)
 */
public record SpellStage(int afterLines, float masteryThreshold, List<SpellEffect> effects,
                         Integer manaCost, Integer cooldownTicks) {
    public SpellStage {
        effects = List.copyOf(effects);
    }
}
