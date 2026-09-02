package com.theo.wizardreal.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PlayerMagicStateTest {

    private static final String IGNIS = "wizardreal:ignis";
    private static final String FULMEN = "wizardreal:fulmen";
    private static final String VITAE = "wizardreal:vitae";
    private static final String EXPLOSION = "wizardreal:explosion";

    @Test
    void forgetDefaultSpellHidesItUntilRelearned() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();

        assertTrue(state.knowsSpell(player, IGNIS));
        state.forgetSpell(player, IGNIS);
        assertFalse(state.knowsSpell(player, IGNIS));
        assertFalse(state.getKnownSpells(player).contains(IGNIS));
        assertTrue(state.isForgotten(player, IGNIS));

        state.learnSpell(player, IGNIS);
        assertTrue(state.knowsSpell(player, IGNIS));
        assertFalse(state.isForgotten(player, IGNIS));
    }

    @Test
    void forgetLearnedSpellRemovesIt() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();

        state.learnSpell(player, EXPLOSION);
        assertTrue(state.knowsSpell(player, EXPLOSION));

        state.forgetSpell(player, EXPLOSION);
        assertFalse(state.knowsSpell(player, EXPLOSION));
        assertFalse(state.getKnownSpells(player).contains(EXPLOSION));
    }

    @Test
    void otherDefaultSpellsSurviveForgettingOne() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();

        state.forgetSpell(player, FULMEN);
        assertFalse(state.knowsSpell(player, FULMEN));
        assertTrue(state.knowsSpell(player, IGNIS));
        assertTrue(state.knowsSpell(player, VITAE));

        assertEquals(4, state.getKnownSpells(player).size());
    }

    @Test
    void forgottenStateSurvivesNbtRoundtrip() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();
        state.forgetSpell(player, FULMEN);
        state.learnSpell(player, EXPLOSION);

        CompoundTag nbt = state.writeNbt();
        PlayerMagicState reloaded = new PlayerMagicState();
        reloaded.fromNbt(nbt);

        assertFalse(reloaded.knowsSpell(player, FULMEN));
        assertTrue(reloaded.knowsSpell(player, EXPLOSION));
        assertTrue(reloaded.knowsSpell(player, IGNIS));
        assertTrue(reloaded.isForgotten(player, FULMEN));
    }

    @Test
    void cooldownClearRemovesEntries() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();

        state.setCooldown(player, IGNIS, 1000L);
        state.setCooldown(player, FULMEN, 2000L);
        state.clearCooldown(player, IGNIS);
        assertEquals(0L, state.getCooldownEnd(player, IGNIS));
        assertEquals(2000L, state.getCooldownEnd(player, FULMEN));

        state.clearAllCooldowns(player);
        assertEquals(0L, state.getCooldownEnd(player, FULMEN));
        assertTrue(state.getCooldowns(player).isEmpty());
    }
}
