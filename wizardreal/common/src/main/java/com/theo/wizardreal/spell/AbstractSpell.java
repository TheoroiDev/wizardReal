package com.theo.wizardreal.spell;

import com.theo.voicecast.api.Pronunciation;
import com.theo.wizardreal.api.CastContext;
import com.theo.wizardreal.api.School;
import com.theo.wizardreal.api.Spell;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Convenience base holding the immutable spell metadata. */
public abstract class AbstractSpell implements Spell {
    protected final String id;
    protected final Set<School> schools;
    protected final int manaCost;
    protected final int cooldownTicks;
    protected final Pronunciation pronunciation;
    protected final String origin;
    protected final boolean requiresLearning;

    /** Full-id constructor: any namespace (datapack spells, addons). */
    protected AbstractSpell(ResourceLocation fullId, Set<School> schools, int manaCost, int cooldownTicks,
                            Pronunciation pronunciation, String origin, boolean requiresLearning) {
        this.id = fullId.toString();
        this.schools = normalize(schools);
        this.manaCost = manaCost;
        this.cooldownTicks = cooldownTicks;
        this.pronunciation = pronunciation;
        this.origin = origin;
        this.requiresLearning = requiresLearning;
    }

    protected AbstractSpell(String shortId, Set<School> schools, int manaCost, int cooldownTicks,
                            Pronunciation pronunciation, String origin, boolean requiresLearning) {
        this(new ResourceLocation("wizardreal", shortId), schools, manaCost, cooldownTicks,
                pronunciation, origin, requiresLearning);
    }

    /** Shorthand for spells that use the default origin and do not require learning. */
    protected AbstractSpell(String shortId, Set<School> schools, int manaCost, int cooldownTicks,
                            Pronunciation pronunciation) {
        this(shortId, schools, manaCost, cooldownTicks, pronunciation, "wizardreal:wizardry", false);
    }

    /** Immutable EnumSet copy (EnumSet.copyOf throws on empty, so guard it). */
    private static Set<School> normalize(Set<School> schools) {
        if (schools == null || schools.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(EnumSet.copyOf(schools));
    }

    @Override public String id() { return id; }
    @Override public String nameKey() { return "spell." + id + ".name"; }
    @Override public Set<School> schools() { return schools; }
    @Override public int manaCost() { return manaCost; }
    @Override public int cooldownTicks() { return cooldownTicks; }
    @Override public Pronunciation pronunciation() { return pronunciation; }
    @Override public String origin() { return origin; }
    @Override public boolean requiresLearning() { return requiresLearning; }

    @Override
    public void cast(CastContext context) {
        try {
            apply(context);
        } catch (Throwable t) {
            com.theo.wizardreal.WizardReal.LOGGER.error("Spell {} failed to cast", id, t);
        }
    }

    /** Implement the actual effect. Runs on the server main thread. */
    protected abstract void apply(CastContext context);
}
