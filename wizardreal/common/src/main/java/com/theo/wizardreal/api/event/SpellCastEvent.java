package com.theo.wizardreal.api.event;

import com.theo.wizardreal.api.CastContext;
import com.theo.wizardreal.api.Spell;

/**
 * Fired server-side before a spell is executed. Cancel by setting
 * {@link #canceled(boolean)}; {@link #reasonKey} is shown to the caster.
 */
public final class SpellCastEvent {
    private final Spell spell;
    private final CastContext context;
    private boolean canceled;
    private String reasonKey = "wizardreal.cast.canceled";

    public SpellCastEvent(Spell spell, CastContext context) {
        this.spell = spell;
        this.context = context;
    }

    public Spell spell() { return spell; }
    public CastContext context() { return context; }
    public boolean canceled() { return canceled; }
    public void canceled(boolean c) { this.canceled = c; }
    public String reasonKey() { return reasonKey; }
    public void reasonKey(String key) { this.reasonKey = key; }
}
