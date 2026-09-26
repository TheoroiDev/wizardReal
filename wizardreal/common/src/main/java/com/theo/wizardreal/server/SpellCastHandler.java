package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.CastContext;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellStage;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.api.event.SpellCastEvent;
import com.theo.wizardreal.api.event.SpellEvents;
import com.theo.wizardreal.config.WizardRealConfig;
import com.theo.wizardreal.item.StaffItem;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side validation and execution of cast requests.
 *
 * <p>M5 validation chain (flags may skip steps for alternative cast paths):
 * <ol>
 *   <li>Spell must be known (if requiresLearning; skipped by {@link CastFlag#SKIP_LEARNING}
 *       or by a bypass-all staff, e.g. the dev staff)</li>
 *   <li>Player must hold a staff in main hand (skipped by {@link CastFlag#SKIP_STAFF})</li>
 *   <li>Staff must allow the spell's origin</li>
 *   <li>Unsupported school incurs +50% mana penalty (warned, not blocked)</li>
 *   <li>Sufficient mana after staff modifiers</li>
 *   <li>Spell off cooldown</li>
 *   <li>SpellCastEvent (cancelable by other mods)</li>
 * </ol>
 */
public final class SpellCastHandler {

    private SpellCastHandler() {}

    /** Bypass flags for alternative cast paths (e.g. scrolls). */
    public enum CastFlag {
        /** No staff required; raw spell mana/cooldown, no origin/school modifiers. */
        SKIP_STAFF,
        /** Castable without having learned the spell (scrolls teach by doing). */
        SKIP_LEARNING
    }

    /** Standard path (voice casting, chant completion): no bypasses. */
    public static void handleCast(Player rawPlayer, String spellId, float power) {
        castValidated(rawPlayer, spellId, power, EnumSet.noneOf(CastFlag.class), 0);
    }

    /** Chant-stage path (magic_eco 03): the cast resolved to stage {@code stageIndex}
     *  (0 = base tier); stage mana/cooldown overrides and stage effects apply. */
    public static void handleCast(Player rawPlayer, String spellId, float power, int stageIndex) {
        castValidated(rawPlayer, spellId, power, EnumSet.noneOf(CastFlag.class), stageIndex);
    }

    /**
     * Read-only cast preflight (issue #41): the learning gate, staff/origin
     * and cooldown checks of the validated chain, run WITHOUT casting and
     * BEFORE a chant starts — so a player is told "you cannot learn-cast this
     * / wrong staff / on cooldown" up front instead of after reciting every
     * line. Mana is deliberately NOT a block here (空转咏唱: the chant may
     * complete and the cast degrades by stage — see {@link #affordableStage}).
     * Sends the same action-bar feedback the full chain would and returns the
     * blocked step, or {@code null} when the chant may start.
     */
    public static String preflight(ServerPlayer player, String spellId) {
        if (player.isSpectator()) return "spectator";
        Spell spell = SpellRegistry.get(spellId).orElse(null);
        if (spell == null) return "unknown";

        PlayerMagicState state = PlayerMagicState.get(player.getServer());
        UUID uuid = player.getUUID();
        ItemStack mainHand = player.getMainHandItem();
        boolean bypassStaff = mainHand.getItem() instanceof StaffItem staff && staff.bypassAll();
        float learningT = state.learningPercent(uuid, spellId, spell.difficulty());

        if (!bypassStaff && spell.requiresLearning() && !LearningCurve.castable(learningT)) {
            actionBar(player, Component.translatable("wizardreal.learning.not_ready",
                    String.format(java.util.Locale.ROOT, "%.0f", learningT)));
            return "learning";
        }
        if (!(mainHand.getItem() instanceof StaffItem staff)) {
            actionBar(player, Component.translatable("wizardreal.cast.needs_staff"));
            return "staff";
        }
        if (!staff.allowsSpell(spell)) {
            actionBar(player, Component.translatable("wizardreal.cast.wrong_origin",
                    Component.translatable("origin." + spell.origin().replace(':', '.'))));
            return "origin";
        }
        if (state.isOnCooldown(uuid, spellId, player.level().getGameTime())) {
            long remainingTicks = state.getCooldownEnd(uuid, spellId) - player.level().getGameTime();
            actionBar(player, Component.translatable("wizardreal.cast.cooldown", (remainingTicks + 19) / 20));
            return "cooldown";
        }
        return null;
    }

    /**
     * 空转咏唱 (issue #41): the highest chant stage {@code mana} can afford,
     * walking down from the intended stage to the base tier. A chant is never
     * wasted on an empty bar: the completed-lines tier degrades to what the
     * caster's current mana supports (base stage unaffordable = the fizzle
     * the validated chain reports).
     *
     * @return the affordable stage index, or -1 when even the base tier is
     *         out of reach
     */
    public static int affordableStage(ServerPlayer player, Spell spell, int intendedStage) {
        if (player.isCreative()) return Math.max(0, intendedStage);
        List<SpellStage> stages = spell.chantStages();
        ItemStack mainHand = player.getMainHandItem();
        StaffItem staff = mainHand.getItem() instanceof StaffItem s ? s : null;
        int top = Math.max(0, intendedStage);
        float[] costs = new float[top + 1];
        for (int i = 0; i <= top; i++) {
            float base = StageResolver.manaCost(stages, i, spell.manaCost());
            costs[i] = staff != null ? staff.getManaCost(spell, base) : base;
        }
        return cheapestAffordableStage(stateMana(player), costs);
    }

    /** Pure stage walk (unit-tested): the highest index whose cost the mana
     *  affords, or -1 when even index 0 is out of reach. */
    static int cheapestAffordableStage(float mana, float[] stageCosts) {
        for (int stage = stageCosts.length - 1; stage >= 0; stage--) {
            if (mana >= stageCosts[stage]) return stage;
        }
        return -1;
    }

    private static float stateMana(ServerPlayer player) {
        return PlayerMagicState.get(player.getServer()).getMana(player.getUUID());
    }


    /**
     * Full validated cast shared by every entry point. Sends action-bar
     * feedback on failure and fires the cancelable {@link SpellCastEvent}.
     * The incoming {@code power} (chant tier or 1.0) is multiplied by the
     * learning curve via {@link PowerResolver}; a successful standard cast
     * settles voice learning (D4: only success teaches).
     *
     * @return true if the spell was executed
     */
    public static boolean castValidated(Player rawPlayer, String spellId, float power,
                                        Set<CastFlag> flags) {
        return castValidated(rawPlayer, spellId, power, flags, 0);
    }

    /**
     * Full validated cast shared by every entry point. Sends action-bar
     * feedback on failure and fires the cancelable {@link SpellCastEvent}.
     * The incoming {@code power} (chant tier or 1.0) is multiplied by the
     * learning curve via {@link PowerResolver}; a successful standard cast
     * settles voice learning (D4: only success teaches).
     *
     * @param stageIndex resolved chant stage (0 = base; N = chant_stages[N-1])
     * @return true if the spell was executed
     */
    public static boolean castValidated(Player rawPlayer, String spellId, float power,
                                        Set<CastFlag> flags, int stageIndex) {
        if (!(rawPlayer instanceof ServerPlayer player)) return false;
        if (player.isSpectator()) return false;

        Spell spell = SpellRegistry.get(spellId).orElse(null);
        if (spell == null) {
            WizardReal.LOGGER.warn("Unknown spell id '{}' from {}", spellId, player.getName().getString());
            return false;
        }

        PlayerMagicState state = PlayerMagicState.get(player.getServer());
        UUID uuid = player.getUUID();
        long now = player.level().getGameTime();
        ItemStack mainHand = player.getMainHandItem();
        boolean bypassStaff = mainHand.getItem() instanceof StaffItem staff && staff.bypassAll();
        float learningT = state.learningPercent(uuid, spellId, spell.difficulty());

        // 1. Learning check (a bypass-all staff — dev/test — waives it).
        //    requires_learning spells need t >= 10% (D-D1 hard gate).
        if (!flags.contains(CastFlag.SKIP_LEARNING) && !bypassStaff && spell.requiresLearning()
                && !LearningCurve.castable(learningT)) {
            actionBar(player, Component.translatable("wizardreal.learning.not_ready",
                    String.format(java.util.Locale.ROOT, "%.0f", learningT)));
            return false;
        }

        // 2. Staff check + modifiers (scrolls and other staffless paths skip this)
        // Stage-aware base values (magic_eco 03): chant_stages may override the
        // tier's mana/cooldown; staff modifiers then apply as usual.
        List<SpellStage> stages = spell.chantStages();
        float stageBaseMana = StageResolver.manaCost(stages, stageIndex, spell.manaCost());
        int stageBaseCooldown = StageResolver.cooldownTicks(stages, stageIndex, spell.cooldownTicks());
        float cost;
        int cooldownTicks;
        if (flags.contains(CastFlag.SKIP_STAFF)) {
            cost = stageBaseMana;
            cooldownTicks = stageBaseCooldown;
        } else {
            if (!(mainHand.getItem() instanceof StaffItem staff)) {
                actionBar(player, Component.translatable("wizardreal.cast.needs_staff"));
                return false;
            }

            // 3. Origin restriction
            if (!staff.allowsSpell(spell)) {
                actionBar(player, Component.translatable("wizardreal.cast.wrong_origin",
                        Component.translatable("origin." + spell.origin().replace(':', '.'))));
                return false;
            }

            // 4. School penalty warning (non-blocking): any-match over the
            //    spell's schools; only when NONE are favored does +50% apply.
            if (!staff.supportsAny(spell.schools())) {
                actionBar(player, Component.translatable("wizardreal.cast.wrong_school"));
                // continue; penalty is baked into mana cost
            }

            cost = staff.getManaCost(spell, stageBaseMana);
            cooldownTicks = staff.getCooldownTicks(spell, stageBaseCooldown);
        }

        // 5. Mana check
        if (!player.isCreative() && state.getMana(uuid) < cost) {
            actionBar(player, Component.translatable("wizardreal.cast.no_mana", String.format(java.util.Locale.ROOT, "%.0f", cost)));
            return false;
        }

        // 6. Cooldown check
        if (state.isOnCooldown(uuid, spellId, now)) {
            long remainingTicks = state.getCooldownEnd(uuid, spellId) - now;
            actionBar(player, Component.translatable("wizardreal.cast.cooldown", (remainingTicks + 19) / 20));
            return false;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        // Power synthesis (voice overhaul): base x learning curve, global cap.
        boolean overlearning = WizardRealConfig.loadCached(
                player.getServer().getServerDirectory().toPath()).learning().overlearning();
        CastContext ctx = new CastContext(player, eye, look.normalize(),
                PowerResolver.resolve(power, learningT, overlearning), new java.util.HashMap<>(), stageIndex);

        SpellCastEvent event = new SpellCastEvent(spell, ctx);
        SpellEvents.postCast(event);
        if (event.canceled()) {
            actionBar(player, Component.translatable(event.reasonKey()));
            return false;
        }

        // Execute
        if (!player.isCreative()) {
            state.consumeMana(uuid, cost);
        }
        spell.cast(ctx);
        state.setCooldown(uuid, spellId, now + cooldownTicks);
        MagicSyncHandler.send(player, state);
        // D4: only successful casts teach (dev/command paths skip learning).
        if (!flags.contains(CastFlag.SKIP_LEARNING)) {
            LearningService.onCastSuccess(player, spell);
        }

        actionBar(player, Component.translatable("wizardreal.cast.success",
                Component.translatable(spell.nameKey())));
        WizardReal.LOGGER.info("{} cast {} (power={}, t={}, mana={}, flags={})",
                player.getName().getString(), spellId,
                String.format(java.util.Locale.ROOT, "%.2f", power),
                String.format(java.util.Locale.ROOT, "%.0f", learningT),
                cost,
                flags.isEmpty() ? "none" : flags);
        return true;
    }

    private static void actionBar(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }
}
