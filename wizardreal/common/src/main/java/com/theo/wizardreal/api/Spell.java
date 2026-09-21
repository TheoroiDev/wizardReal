package com.theo.wizardreal.api;

import com.theo.wizardreal.api.Pronunciation;

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
     * Learning difficulty (D4): the mastery requirement scales as
     * {@code 100 x difficulty} points, so the same effort converts into less
     * mastery. Range 0.5-3.0 (forbidden chants 2.0-3.0).
     */
    default float difficulty() { return 1.0f; }

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
     * Long incantations for a ritual spell. Non-empty means the spell is cast
     * through chanting: the first spoken line (L1) enters the chant and locks
     * the variant (D9 首行即门), speaking the last line (spell name) mid-chant
     * releases early at the completed-lines power tier (咒名跳章), and finishing
     * every line casts at full power. Empty (default) = instant spell.
     */
    default List<Chant> chants() {
        return List.of();
    }

    /**
     * Chant tuning (power tiers / skip permission / interruptibility / pact),
     * parsed from the {@code chant_policy} JSON block. Default = full power on
     * every completion, skipping allowed, interruptible, no pact.
     */
    default ChantPolicy chantPolicy() {
        return ChantPolicy.DEFAULT;
    }

    /**
     * Chant-stage ladder (magic_eco 03), ordered by ascending {@code after_lines}.
     * Casts resolved at stage N use stage N's effects (and optional mana/cooldown
     * overrides) instead of the base ones; stage 0 = the base spell. Stage
     * resolution is dual-gated: completed chant lines AND mastery percent
     * ({@code chant_stages[].mastery}). Empty (default) = single-tier spell.
     */
    default java.util.List<SpellStage> chantStages() {
        return java.util.List.of();
    }
}