package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.CastContext;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.api.event.SpellCastEvent;
import com.theo.wizardreal.api.event.SpellEvents;
import com.theo.wizardreal.item.StaffItem;
import java.util.EnumSet;
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
    public static void handleCast(Player rawPlayer, String spellId, float confidence) {
        castValidated(rawPlayer, spellId, confidence, EnumSet.noneOf(CastFlag.class));
    }

    /**
     * Full validated cast shared by every entry point. Sends action-bar
     * feedback on failure and fires the cancelable {@link SpellCastEvent}.
     *
     * @return true if the spell was executed
     */
    public static boolean castValidated(Player rawPlayer, String spellId, float confidence,
                                        Set<CastFlag> flags) {
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

        // 1. Learning check (a bypass-all staff — dev/test — waives it)
        if (!flags.contains(CastFlag.SKIP_LEARNING) && !bypassStaff && spell.requiresLearning()
                && !state.knowsSpell(uuid, spellId)) {
            actionBar(player, Component.translatable("wizardreal.cast.unknown_spell"));
            return false;
        }

        // 2. Staff check + modifiers (scrolls and other staffless paths skip this)
        float cost;
        int cooldownTicks;
        if (flags.contains(CastFlag.SKIP_STAFF)) {
            cost = spell.manaCost();
            cooldownTicks = spell.cooldownTicks();
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

            cost = staff.getManaCost(spell);
            cooldownTicks = staff.getCooldownTicks(spell);
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
        CastContext ctx = new CastContext(player, eye, look.normalize(), 1.0f);

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

        actionBar(player, Component.translatable("wizardreal.cast.success",
                Component.translatable(spell.nameKey())));
        WizardReal.LOGGER.info("{} cast {} (conf={}, mana={}, flags={})",
                player.getName().getString(), spellId,
                String.format(java.util.Locale.ROOT, "%.2f", confidence),
                cost,
                flags.isEmpty() ? "none" : flags);
        return true;
    }

    private static void actionBar(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }
}
