package com.theo.wizardreal.item;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.server.SpellCastHandler;
import java.util.EnumSet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A scroll casts its bound spell instantly (no staff, no learning required)
 * and is consumed. All validation (mana, cooldown, cancel event) goes through
 * the shared {@link SpellCastHandler} path with {@code SKIP_STAFF} +
 * {@code SKIP_LEARNING}.
 */
public class ScrollItem extends Item {

    public ScrollItem(Item.Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (world.isClientSide) {
            return InteractionResultHolder.pass(user.getItemInHand(hand));
        }
        if (!(user instanceof ServerPlayer player)) {
            return InteractionResultHolder.pass(user.getItemInHand(hand));
        }

        ItemStack stack = player.getItemInHand(hand);
        String spellId = getSpellId(stack);
        if (spellId == null || spellId.isBlank()) {
            return InteractionResultHolder.fail(stack);
        }

        Spell spell = SpellRegistry.get(spellId).orElse(null);
        if (spell == null) {
            return InteractionResultHolder.fail(stack);
        }

        // Full validated path: mana/cooldown checks and the cancelable
        // SpellCastEvent all happen inside; feedback messages are sent there.
        boolean cast = SpellCastHandler.castValidated(player, spell.id(), 1.0f,
                EnumSet.of(SpellCastHandler.CastFlag.SKIP_STAFF, SpellCastHandler.CastFlag.SKIP_LEARNING));
        if (!cast) {
            return InteractionResultHolder.fail(stack);
        }

        stack.shrink(1);
        world.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.4f, 1.5f);
        WizardReal.LOGGER.info("{} used scroll of {}", player.getName().getString(), spellId);

        return InteractionResultHolder.success(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        String spellId = getSpellId(stack);
        if (spellId == null || spellId.isBlank()) {
            return Component.translatable("item.wizardreal.scroll_blank");
        }
        Spell spell = SpellRegistry.get(spellId).orElse(null);
        if (spell != null) {
            return Component.translatable("item.wizardreal.scroll_named",
                    Component.translatable(spell.nameKey()));
        }
        // Dedicated server: client registry is empty — resolve via the synced
        // spell catalog so NBT scrolls don't read as "unknown" in MP.
        String nameKey = com.theo.wizardreal.net.SpellCatalogCache.nameKey(spellId);
        if (nameKey != null) {
            return Component.translatable("item.wizardreal.scroll_named",
                    Component.translatable(nameKey));
        }
        return Component.translatable("item.wizardreal.scroll_unknown");
    }

    public static void setSpellId(ItemStack stack, String spellId) {
        stack.getOrCreateTag().putString("SpellId", spellId);
    }

    public static String getSpellId(ItemStack stack) {
        if (!stack.hasTag()) return null;
        return stack.getTag().getString("SpellId");
    }
}
