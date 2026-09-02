package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.catalog.CatalogPayload;
import com.theo.wizardreal.net.SpellCatalogNetwork;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Single funnel for all spell-catalog publication points. Whatever is built
 * for a player is sent to that player's client via
 * {@code wizardreal:spell_catalog} (the client caches + exports it).
 *
 * <p>Publication points (per the catalog contract):
 * <ul>
 *   <li>{@code PLAYER_JOIN} — every joining player;</li>
 *   <li>successful tome learning ({@code SpellTomeItem.use});</li>
 *   <li>spell datapack reload ({@code SpellDataLoader}).</li>
 * </ul>
 */
public final class SpellCatalogService {

    /** Server captured at SERVER_STARTING (reload may run before STARTED). */
    private static volatile MinecraftServer server;

    private SpellCatalogService() {}

    public static void register() {
        LifecycleEvent.SERVER_STARTING.register(s -> server = s);
        LifecycleEvent.SERVER_STARTED.register(s -> server = s);
        LifecycleEvent.SERVER_STOPPED.register(s -> server = null);
        PlayerEvent.PLAYER_JOIN.register(SpellCatalogService::publish);
    }

    /** Publish the full catalog snapshot for one player (server thread). */
    public static void publish(ServerPlayer player) {
        MinecraftServer s = server != null ? server : player.getServer();
        if (s == null) return;
        UUID playerUuid = player.getUUID();
        CatalogPayload payload = SpellCatalogBuilder.build(s, playerUuid);
        SpellCatalogNetwork.send(player, payload);
        WizardReal.LOGGER.debug("Spell catalog pushed to {}", player.getName().getString());
    }

    /** Publish to every online player (marshal onto the server thread). */
    public static void publishAll() {
        MinecraftServer s = server;
        if (s == null) return;
        s.execute(() -> {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                publish(player);
            }
        });
    }
}
