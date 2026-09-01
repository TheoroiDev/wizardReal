package com.theo.wizardreal.net;

import com.theo.wizardreal.WizardReal;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * S2C network channel for syncing player magic state (mana, max mana, cooldowns)
 * from server to client.
 *
 * <p>Packets:
 * <ul>
 *   <li>{@code SYNC_FULL} — sent on login and periodically; contains mana/maxMana
 *       plus all active cooldowns.</li>
 * </ul>
 */
public final class MagicSyncNetwork {

    public static final ResourceLocation CHANNEL = new ResourceLocation(WizardReal.MOD_ID, "magic_sync");

    private MagicSyncNetwork() {}

    public static void registerClientReceiver() {
        NetworkManager.registerReceiver(NetworkManager.s2c(), CHANNEL, (buf, ctx) -> {
            // Read the packet on the Netty thread, then marshal the state
            // update onto the main thread (HUD reads it during render).
            float mana = buf.readFloat();
            float maxMana = buf.readFloat();
            int cdCount = buf.readVarInt();
            Map<String, Integer> cooldowns = new HashMap<>();
            for (int i = 0; i < cdCount; i++) {
                cooldowns.put(buf.readUtf(64), buf.readVarInt());
            }
            ctx.queue(() -> MagicClientState.apply(mana, maxMana, cooldowns));
        });
    }

    public static void sendFull(ServerPlayer player, float mana, float maxMana,
                                 Map<String, Long> cooldownEnds, long worldTime) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeFloat(mana);
        buf.writeFloat(maxMana);
        buf.writeVarInt(cooldownEnds.size());
        for (Map.Entry<String, Long> e : cooldownEnds.entrySet()) {
            buf.writeUtf(e.getKey());
            long remaining = Math.max(0, e.getValue() - worldTime);
            buf.writeVarInt((int) remaining);
        }
        NetworkManager.sendToPlayer(player, CHANNEL, buf);
    }
}
