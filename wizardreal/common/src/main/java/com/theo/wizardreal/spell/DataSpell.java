package com.theo.wizardreal.spell;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.CastContext;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.School;
import com.theo.wizardreal.effect.SpellEffect;
import com.theo.voicecast.api.Pronunciation;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * A spell defined by datapack JSON: metadata parsed by {@link SpellDefinition}
 * plus an ordered list of composable {@link SpellEffect}s.
 */
public final class DataSpell extends AbstractSpell {
    private final float thresholdOverride;
    private final List<SpellEffect> effects;
    private final List<Chant> chants;

    public DataSpell(ResourceLocation id, Set<School> schools, int manaCost, int cooldownTicks,
                     boolean requiresLearning, String origin, float thresholdOverride,
                     Pronunciation pronunciation, List<Chant> chants, List<SpellEffect> effects) {
        super(id, schools, manaCost, cooldownTicks, pronunciation, origin, requiresLearning);
        this.thresholdOverride = thresholdOverride;
        this.effects = List.copyOf(effects);
        this.chants = List.copyOf(chants);
    }

    /** Per-spell matcher threshold override (passed through to the matchers). */
    @Override
    public float threshold() {
        return thresholdOverride;
    }

    /** Ordered effect list (inspection/debug, e.g. the spellinfo command). */
    public List<SpellEffect> effects() {
        return effects;
    }

    @Override
    public List<Chant> chants() {
        return chants;
    }

    @Override
    protected void apply(CastContext ctx) {
        for (SpellEffect effect : effects) {
            try {
                effect.apply(ctx);
            } catch (Throwable t) {
                WizardReal.LOGGER.error("Effect {} of spell {} failed",
                        effect.effectId(), id, t);
            }
        }
    }
}
