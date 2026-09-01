package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.item.SpellTomeItem;
import com.theo.wizardreal.item.WizardRealItems;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Drops spell tomes when specific mobs are killed by spell damage.
 */
public final class SpellKillDrops {

    private static final Random RANDOM = new Random();

    private SpellKillDrops() {}

    public static void init() {
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            onDeath(entity, source);
            return EventResult.pass();
        });
    }

    public static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity.level() instanceof ServerLevel)) return;
        if (!(source.getEntity() instanceof ServerPlayer)) return;

        // Only drop when killed by a player (spell or otherwise; M6 may refine
        // to require spell-projectile damage source).

        ItemStack drop = null;
        float chance = 0f;

        if (entity.getType() == EntityType.BLAZE) {
            drop = new ItemStack(WizardRealItems.SPELL_TOME.get());
            SpellTomeItem.setSpellId(drop, "wizardreal:ignis");
            chance = 0.15f;
        } else if (entity.getType() == EntityType.EVOKER) {
            drop = new ItemStack(WizardRealItems.SPELL_TOME.get());
            SpellTomeItem.setSpellId(drop, "wizardreal:explosion");
            chance = 0.25f;
        } else if (entity.getType() == EntityType.WITCH) {
            drop = new ItemStack(WizardRealItems.SPELL_TOME.get());
            // random spell from the basic set
            String[] basic = {"wizardreal:ignis", "wizardreal:fulmen", "wizardreal:vitae",
                    "wizardreal:aegis", "wizardreal:ictus"};
            SpellTomeItem.setSpellId(drop, basic[RANDOM.nextInt(basic.length)]);
            chance = 0.10f;
        } else if (entity.getType() == EntityType.WITHER_SKELETON) {
            drop = new ItemStack(WizardRealItems.SPELL_TOME.get());
            SpellTomeItem.setSpellId(drop, "wizardreal:explosion");
            chance = 0.08f;
        }

        if (drop != null && RANDOM.nextFloat() < chance) {
            entity.spawnAtLocation(drop);
            WizardReal.LOGGER.debug("Spell kill drop: {} from {}", drop, entity.getType());
        }
    }
}
