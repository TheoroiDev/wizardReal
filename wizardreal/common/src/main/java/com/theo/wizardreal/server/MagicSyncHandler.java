package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.net.MagicSyncNetwork;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side handler that pushes magic state (mana, cooldowns) to clients.
 * Sends on login and periodically every 40 ticks (2 seconds).
 */
public final class MagicSyncHandler {

    private static boolean registered;

    private MagicSyncHandler() {}

    public static void register() {
        if (registered) return;
        registered = true;

        PlayerEvent.PLAYER_JOIN.register(player -> {
            if (player.getServer() == null) return;
            PlayerMagicState state = PlayerMagicState.get(player.getServer());
            UUID uuid = player.getUUID();
            send(player, state);
        });

        TickEvent.SERVER_POST.register(server -> {
            if (server.getTickCount() % 40 != 0) return;
            PlayerMagicState state = PlayerMagicState.get(server);
            long now = server.overworld().getGameTime();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                send(player, state);
            }
        });

        WizardReal.LOGGER.info("MagicSyncHandler registered");
    }

    public static void send(ServerPlayer player, PlayerMagicState state) {
        UUID uuid = player.getUUID();
        long worldTime = player.getServer().overworld().getGameTime();
        Map<String, Long> cds = state.getCooldowns(uuid);
        MagicSyncNetwork.sendFull(player, state.getMana(uuid), state.getMaxMana(uuid), cds, worldTime);
    }
}
