package com.theo.wizardreal.item;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.server.LearningService;
import com.theo.wizardreal.server.SpellCatalogService;
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

import java.util.List;

/**
 * The "First Lesson" tome (wizardReal#49 新手断点修复): a FIXED three-spell
 * starter book given to every new player on first join — the deterministic
 * answer to the random tome loot never binding the spells a beginner needs.
 * One use teaches all three entry spells (ignis / celere / velum — the
 * shortest chant lines) and consumes the book.
 */
public class FirstLessonTomeItem extends Item {

    /** The entry trio: shortest chant lines, one per cast style. */
    public static final List<String> LESSON_SPELLS =
            List.of("wizardreal:ignis", "wizardreal:celere", "wizardreal:velum");

    public FirstLessonTomeItem(Item.Properties properties) {
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
        ItemStack stack = user.getItemInHand(hand);

        int learned = 0;
        for (String spellId : LESSON_SPELLS) {
            Spell spell = SpellRegistry.get(spellId).orElse(null);
            if (spell == null) {
                WizardReal.LOGGER.warn("First Lesson references unknown spell {}", spellId);
                continue;
            }
            float gain = LearningService.onTomeUse(player, spell);
            if (gain > 0f) learned++;
            player.displayClientMessage(Component.translatable(
                            gain > 0f ? "wizardreal.first_lesson.taught" : "wizardreal.first_lesson.progressed",
                            Component.translatable(spell.nameKey()),
                            String.format(java.util.Locale.ROOT, "%.0f",
                                    playerT(player, spellId, spell.difficulty())))
                    .withStyle(gain > 0f ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        }
        if (learned == 0) {
            // Everything already past the tome cap — nothing to teach.
            player.displayClientMessage(Component.translatable("wizardreal.first_lesson.done")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(stack);
        }
        stack.shrink(1);
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.5f, 1.2f);
        player.displayClientMessage(Component.translatable("wizardreal.first_lesson.done")
                .withStyle(ChatFormatting.GREEN), false);
        WizardReal.LOGGER.info("{} worked through the First Lesson ({} spells advanced)",
                player.getName().getString(), learned);
        // Catalog publication point: learned flags just changed for this player.
        SpellCatalogService.publish(player);
        return InteractionResultHolder.success(stack);
    }

    private static float playerT(ServerPlayer player, String spellId, float difficulty) {
        return com.theo.wizardreal.server.PlayerMagicState.get(player.getServer())
                .learningPercent(player.getUUID(), spellId, difficulty);
    }
}
