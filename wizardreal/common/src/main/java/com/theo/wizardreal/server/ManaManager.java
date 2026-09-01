package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;

/**
 * Server-side mana regeneration and cooldown pruning.
 * Registered once on {@link TickEvent#SERVER_POST}.
 */
public final class ManaManager {

    private static final ManaManager INSTANCE = new ManaManager();
    public static ManaManager get() { return INSTANCE; }

    private ManaManager() {}

    private boolean registered;

    public void register() {
        if (registered) return;
        registered = true;
        TickEvent.SERVER_POST.register(this::tick);
        WizardReal.LOGGER.info("ManaManager registered (recovery + cooldown pruning)");
    }

    private void tick(MinecraftServer server) {
        PlayerMagicState state = PlayerMagicState.get(server);
        // Mana recovery for all online players
        state.tickRecovery(server.getPlayerList().getPlayers());
        // Prune expired cooldowns occasionally (every 5 seconds = 100 ticks)
        if (server.getTickCount() % 100 == 0) {
            state.pruneCooldowns(server.overworld().getGameTime());
        }
    }
}
