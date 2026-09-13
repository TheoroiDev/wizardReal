package com.theo.wizardreal.spell;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.CastContext;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantPolicy;
import com.theo.wizardreal.api.School;
import com.theo.wizardreal.api.SpellStage;
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
    private final float difficulty;
    private final List<SpellEffect> effects;
    private final List<Chant> chants;
    private final ChantPolicy chantPolicy;
    private final List<SpellStage> chantStages;

    public DataSpell(ResourceLocation id, Set<School> schools, int manaCost, int cooldownTicks,
                     boolean requiresLearning, String origin, float thresholdOverride,
                     Pronunciation pronunciation, List<Chant> chants, List<SpellEffect> effects) {
        this(id, schools, manaCost, cooldownTicks, requiresLearning, origin, thresholdOverride,
                1.0f, pronunciation, chants, effects, ChantPolicy.DEFAULT, List.of());
    }

    public DataSpell(ResourceLocation id, Set<School> schools, int manaCost, int cooldownTicks,
                     boolean requiresLearning, String origin, float thresholdOverride, float difficulty,
                     Pronunciation pronunciation, List<Chant> chants, List<SpellEffect> effects,
                     ChantPolicy chantPolicy) {
        this(id, schools, manaCost, cooldownTicks, requiresLearning, origin, thresholdOverride,
                difficulty, pronunciation, chants, effects, chantPolicy, List.of());
    }

    public DataSpell(ResourceLocation id, Set<School> schools, int manaCost, int cooldownTicks,
                     boolean requiresLearning, String origin, float thresholdOverride, float difficulty,
                     Pronunciation pronunciation, List<Chant> chants, List<SpellEffect> effects,
                     ChantPolicy chantPolicy, List<SpellStage> chantStages) {
        super(id, schools, manaCost, cooldownTicks, pronunciation, origin, requiresLearning);
        this.thresholdOverride = thresholdOverride;
        this.difficulty = difficulty;
        this.effects = List.copyOf(effects);
        this.chants = List.copyOf(chants);
        this.chantPolicy = chantPolicy == null ? ChantPolicy.DEFAULT : chantPolicy;
        this.chantStages = List.copyOf(chantStages);
    }

    /** Per-spell matcher threshold override (passed through to the matchers). */
    @Override
    public float threshold() {
        return thresholdOverride;
    }

    @Override
    public float difficulty() {
        return difficulty;
    }

    @Override
    public ChantPolicy chantPolicy() {
        return chantPolicy;
    }

    @Override
    public List<SpellStage> chantStages() {
        return chantStages;
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
        List<SpellEffect> resolved = effects;
        int stage = ctx.stage();
        if (stage > 0 && stage <= chantStages.size()) {
            resolved = chantStages.get(stage - 1).effects();
        }
        for (SpellEffect effect : resolved) {
            try {
                effect.apply(ctx);
            } catch (Throwable t) {
                WizardReal.LOGGER.error("Effect {} of spell {} failed",
                        effect.effectId(), id, t);
            }
        }
    }
}
