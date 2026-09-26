package com.theo.wizardreal.net;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.server.ChantManager;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ritual chant channel. The server owns all chant state; the client only
 * renders what it receives and never casts on its own (casting stays
 * server-authoritative).
 *
 * <p>Packets on {@code wizardreal:chant}:
 * <ul>
 *   <li>S2C {@link #START} — spell id + every chant variant's display keys.</li>
 *   <li>S2C {@link #PROGRESS} — locked variant / line index / error flash.</li>
 *   <li>S2C {@link #END} — chant finished, cancelled, or timed out.</li>
 *   <li>S2C {@link #NOTICE} — idle-recognition feedback (issue #41): a
 *       rejected utterance surfaces as a whisper / dissipate line.</li>
 *   <li>C2S {@link #CANCEL} — player-initiated cancel (left-click while
 *       channeling the staff).</li>
 * </ul>
 */
public final class ChantNetwork {
    public static final ResourceLocation CHANNEL_CHANT = new ResourceLocation(WizardReal.MOD_ID, "chant");

    public static final int START = 1;
    public static final int PROGRESS = 2;
    public static final int END = 3;
    public static final int CANCEL = 4;
    /** S2C idle-recognition feedback (issue #41): rejected/whisper notice. */
    public static final int NOTICE = 5;

    /** Notice kinds (see {@link #sendNotice}). */
    public static final String NOTICE_WHISPER = "whisper";
    public static final String NOTICE_DISSIPATE = "dissipate";

    private ChantNetwork() {}

    /**
     * Registers the C2S cancel receiver. The client also holds an inert C2S
     * entry (registering is harmless there) which — via Architectury's login
     * sync — advertises that the client can send cancels.
     */
    public static void registerServerReceiver() {
        NetworkManager.registerReceiver(NetworkManager.c2s(), CHANNEL_CHANT, (buf, ctx) -> {
            int action = buf.readByte();
            if (action != CANCEL) return;
            if (!(ctx.getPlayer() instanceof ServerPlayer sender)) return;
            ctx.queue(() -> ChantManager.get().cancel(sender, false));
        });
    }

    /** Enter chanting state: spell id + every chant variant's display keys. */
    public static void sendStart(ServerPlayer player, String spellId, List<List<String>> variantLines) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(START);
        buf.writeUtf(spellId, 64);
        buf.writeVarInt(variantLines.size());
        for (List<String> lines : variantLines) {
            buf.writeVarInt(lines.size());
            for (String key : lines) buf.writeUtf(key, 128);
        }
        NetworkManager.sendToPlayer(player, CHANNEL_CHANT, buf);
    }

    /** Locked a chant variant / advanced to a line (or failed the current line). */
    public static void sendProgress(ServerPlayer player, int variantIndex, int lineIndex, boolean error) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(PROGRESS);
        buf.writeVarInt(variantIndex);
        buf.writeVarInt(lineIndex);
        buf.writeBoolean(error);
        NetworkManager.sendToPlayer(player, CHANNEL_CHANT, buf);
    }

    /** Leave chanting state (spell completed, cancelled, or timed out). */
    public static void sendEnd(ServerPlayer player, boolean success) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(END);
        buf.writeBoolean(success);
        NetworkManager.sendToPlayer(player, CHANNEL_CHANT, buf);
    }

    /**
     * Idle-recognition feedback (issue #41 拒识有反馈): the utterance heard
     * something but the semantic gate ruled it out.
     *
     * @param kind    {@link #NOTICE_WHISPER} (no match, but a runner-up
     *                candidate exists — the HUD names it) or
     *                {@link #NOTICE_DISSIPATE} (the adjudicator rejected the
     *                utterance outright)
     * @param nameKey the runner-up spell's lang key ({@code spell.<id>.name});
     *                empty for {@code dissipate}
     */
    public static void sendNotice(ServerPlayer player, String kind, String nameKey) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(NOTICE);
        buf.writeUtf(kind, 16);
        buf.writeUtf(nameKey, 128);
        NetworkManager.sendToPlayer(player, CHANNEL_CHANT, buf);
    }

    /** Client → server: cancel the sender's active chant (left-click cancel). */
    public static void sendCancel() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(CANCEL);
        NetworkManager.sendToServer(CHANNEL_CHANT, buf);
    }
}
