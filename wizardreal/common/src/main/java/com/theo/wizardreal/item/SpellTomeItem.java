package com.theo.wizardreal.item;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.server.PlayerMagicState;
import net.minecraft.ChatFormatting;
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
 * A spell tome teaches the player a new spell when used.
 * The spell id is stored in NBT ({@code SpellId} tag).
 */
public class SpellTomeItem extends Item {

    public SpellTomeItem(Item.Properties properties) {
        super(properties.stacksTo(1));
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
            player.displayClientMessage(Component.translatable("wizardreal.tome.blank").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        Spell spell = SpellRegistry.get(spellId).orElse(null);
        if (spell == null) {
            player.displayClientMessage(Component.translatable("wizardreal.tome.invalid").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        PlayerMagicState state = PlayerMagicState.get(player.getServer());
        if (state.knowsSpell(player.getUUID(), spellId)) {
            player.displayClientMessage(Component.translatable("wizardreal.tome.already_known",
                    Component.translatable(spell.nameKey())).withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(stack);
        }

        state.learnSpell(player.getUUID(), spellId);
        stack.shrink(1);
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.5f, 1.2f);
        player.displayClientMessage(Component.translatable("wizardreal.tome.learn",
                Component.translatable(spell.nameKey())).withStyle(ChatFormatting.GREEN), true);
        WizardReal.LOGGER.info("{} learned spell {} from tome", player.getName().getString(), spellId);

        return InteractionResultHolder.success(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        String spellId = getSpellId(stack);
        if (spellId == null || spellId.isBlank()) {
            return Component.translatable("item.wizardreal.spell_tome.blank");
        }
        Spell spell = SpellRegistry.get(spellId).orElse(null);
        if (spell == null) {
            return Component.translatable("item.wizardreal.spell_tome.unknown");
        }
        return Component.translatable("item.wizardreal.spell_tome.named",
                Component.translatable(spell.nameKey()));
    }

    public static void setSpellId(ItemStack stack, String spellId) {
        stack.getOrCreateTag().putString("SpellId", spellId);
    }

    public static String getSpellId(ItemStack stack) {
        if (!stack.hasTag()) return null;
        return stack.getTag().getString("SpellId");
    }
}
