package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.item.FirstLessonTomeItem;
import com.theo.wizardreal.item.WizardRealItems;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * New-player onboarding (wizardReal#49 新手断点修复): every fresh player
 * receives the FIXED "First Lesson" tome on first join — the deterministic
 * starter that replaces the random tome loot binding (典籍 loot 随机绑定拿
 * 不到目标书). Once per player per world (persisted in PlayerMagicState).
 */
public final class Onboarding {

    private Onboarding() {}

    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(player -> {
            ServerPlayer sp = player;
            PlayerMagicState state = PlayerMagicState.get(sp.getServer());
            if (state.firstLessonGiven(sp.getUUID())) return;
            state.markFirstLessonGiven(sp.getUUID());
            state.save();
            var stack = new net.minecraft.world.item.ItemStack(
                    WizardRealItems.FIRST_LESSON_TOME.get());
            if (!sp.getInventory().add(stack)) {
                sp.drop(stack, false);
            }
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "wizardreal.first_lesson.gift"), false);
            WizardReal.LOGGER.info("{} received the First Lesson tome", sp.getName().getString());
        });
    }
}
