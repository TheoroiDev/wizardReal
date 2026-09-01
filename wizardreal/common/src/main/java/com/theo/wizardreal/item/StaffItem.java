package com.theo.wizardreal.item;

import com.theo.wizardreal.api.School;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.particle.WizardRealParticles;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A staff is required to cast spells. Each staff defines which spell origins
 * it can channel (hard restriction) and which schools it supports well
 * (unsupported schools incur a +50% mana penalty but are still allowed).
 */
public class StaffItem extends Item {

    private final Set<String> allowedOrigins;
    private final Set<School> allowedSchools;
    private final float manaCostMultiplier;
    private final float cooldownMultiplier;
    private final String translationKeySuffix;
    private final boolean bypassAll;

    public StaffItem(Item.Properties properties,
                     Set<String> allowedOrigins,
                     Set<School> allowedSchools,
                     float manaCostMultiplier,
                     float cooldownMultiplier,
                     String translationKeySuffix,
                     boolean bypassAll) {
        super(properties.stacksTo(1));
        this.allowedOrigins = Collections.unmodifiableSet(allowedOrigins);
        this.allowedSchools = Collections.unmodifiableSet(allowedSchools);
        this.manaCostMultiplier = manaCostMultiplier;
        this.cooldownMultiplier = cooldownMultiplier;
        this.translationKeySuffix = translationKeySuffix;
        this.bypassAll = bypassAll;
    }

    /** A generic apprentice staff that allows everything with no bonus. */
    public static StaffItem apprentice(Item.Properties properties) {
        return new StaffItem(properties,
                Set.of("wizardreal:wizardry"), // can be expanded later
                EnumSet.allOf(School.class),
                1.0f, 1.0f,
                "staff_apprentice", false);
    }

    /** Fire school staff: cheaper fire spells. */
    public static StaffItem fire(Item.Properties properties) {
        return new StaffItem(properties,
                Set.of("wizardreal:wizardry"),
                EnumSet.of(School.FIRE, School.ARCANE),
                0.9f, 1.0f,
                "staff_fire", false);
    }

    /** Lightning school staff. */
    public static StaffItem lightning(Item.Properties properties) {
        return new StaffItem(properties,
                Set.of("wizardreal:wizardry"),
                EnumSet.of(School.LIGHTNING, School.AIR),
                0.85f, 0.9f,
                "staff_lightning", false);
    }

    /** Developer / test staff: bypasses all restrictions, zero mana, zero cooldown. */
    public static StaffItem dev(Item.Properties properties) {
        return new StaffItem(properties,
                Set.of(),
                EnumSet.noneOf(School.class),
                0.0f, 0.0f,
                "staff_sdevv", true);
    }

    // ------------------------------------------------------------------
    // Checks
    // ------------------------------------------------------------------

    /** Dev/test staff flag: waives learning, origin, school penalty, mana
     *  and cooldown (the latter three fall out of the accessors below). */
    public boolean bypassAll() {
        return bypassAll;
    }

    /** Hard restriction: staff must support the spell's origin. */
    public boolean allowsSpell(Spell spell) {
        return bypassAll || allowedOrigins.contains(spell.origin());
    }

    /**
     * Whether the staff natively supports ANY of the spell's schools (no
     * penalty). A spell lists several schools meaning "any of these affinities";
     * only when none match does the +50% mana penalty apply.
     */
    public boolean supportsAny(Set<School> schools) {
        if (bypassAll) return true;
        for (School s : schools) {
            if (allowedSchools.contains(s)) return true;
        }
        return false;
    }

    /** Effective mana cost after staff modifiers and school penalty. */
    public float getManaCost(Spell spell) {
        if (bypassAll) return 0f;
        float cost = spell.manaCost();
        if (!supportsAny(spell.schools())) {
            cost *= 1.5f;
        }
        return cost * manaCostMultiplier;
    }

    /** Effective cooldown ticks after staff modifier. */
    public int getCooldownTicks(Spell spell) {
        if (bypassAll) return 0;
        return Math.round(spell.cooldownTicks() * cooldownMultiplier);
    }

    @Override
    public String getDescriptionId() {
        return "item.wizardreal." + translationKeySuffix;
    }

    /**
     * Holding right-click channels the staff (bow-like draw): the use state
     * stays active for as long as the button is held, the client keeps the
     * PTT open (see client {@code StaffCastHandler}), and casting particles
     * stream from the caster ({@link #onUseTick}). The explicit
     * {@code startUsingItem} is what puts the item into its use state —
     * returning consume alone never does (vanilla bows/shields do the same).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(user.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000; // bow-style: never auto-completes, released by the player
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    /** Casting particles while channeling. Spawned server-side so everyone
     *  nearby (including the caster's own client via the integrated/remote
     *  server) sees them. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        long tick = level.getGameTime();
        if (tick % 4 != 0) return;

        SimpleParticleType spark = (SimpleParticleType) WizardRealParticles.SPARK.get();
        SimpleParticleType rune = (SimpleParticleType) WizardRealParticles.RUNE.get();
        ServerLevel serverLevel = (ServerLevel) level;
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();
        // Two sparks orbiting the caster, rising slightly; a rune pulses in
        // every few beats.
        double angle = (tick % 32) * (Math.PI / 16.0);
        double radius = 0.55;
        serverLevel.sendParticles(spark,
                x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius,
                1, 0.0, 0.05, 0.0, 0.0);
        serverLevel.sendParticles(spark,
                x - Math.cos(angle) * radius, y + 0.35, z - Math.sin(angle) * radius,
                1, 0.0, 0.05, 0.0, 0.0);
        if (tick % 20 == 0) {
            serverLevel.sendParticles(rune, x, y + 0.9, z, 1, 0.15, 0.2, 0.15, 0.0);
        }
    }
}
