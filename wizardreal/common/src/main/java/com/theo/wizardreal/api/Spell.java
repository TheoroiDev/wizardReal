package com.theo.wizardreal.api;

import com.theo.voicecast.api.Pronunciation;

import java.util.List;
import java.util.Set;

public interface Spell {

    /** Unique namespaced id, e.g. {@code "wizardreal:ignis"}. */
    String id();

    /** Display key used in HUD/tooltips. */
    String nameKey();

    /**
     * Schools this spell belongs to. A staff supports the spell when it
     * supports ANY of these schools (any-match); if none are supported the
     * spell costs +50% mana but can still be cast.
     */
    Set<School> schools();

    int manaCost();

    int cooldownTicks();

    /** IPA templates + text aliases used by the recognition matcher. */
    Pronunciation pronunciation();

    /** 0..1 matcher confidence threshold override; &lt;0 means use global default. */
    default float threshold() { return -1f; }

    /**
     * Origin / source of the spell. Used by staves to restrict which origins
     * a caster may channel. All built-in spells use {@code "wizardreal:wizardry"}.
     */
    String origin();

    /** Whether the spell must be learned before it can be cast. */
    default boolean requiresLearning() { return false; }

    /** Cast the spell on the server. Called after all validation has passed. */
    void cast(CastContext context);
    /** Optional additional tooltip lines (translation keys). */
    default List<String> tooltipKeys() {
        return List.of();
    }

    /**
     * Long incantations for a ritual spell. Non-empty means saying the trigger
     * word enters chanting state and these lines must be spoken (sentence by
     * sentence) before the spell casts. Empty (default) = instant spell.
     */
    default List<Chant> chants() {
        return List.of();
    }
}