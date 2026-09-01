package com.theo.wizardreal.client;

import com.theo.wizardreal.net.ChantNetwork;
import dev.architectury.networking.NetworkManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Client-side chant state holder + S2C receiver registration. Client-only; the
 * server never sends these to itself so registration here is safe in common
 * (the receiver is only registered from the client entrypoint).
 */
public final class ChantClient {
    public static final ChantState CHANT = new ChantState();

    private static boolean initialized;

    private ChantClient() {}

    /** Register the chant S2C receiver. Call once on the client. */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        NetworkManager.registerReceiver(NetworkManager.s2c(), ChantNetwork.CHANNEL_CHANT, (buf, ctx) -> {
            int action = buf.readByte();
            switch (action) {
                case ChantNetwork.START -> {
                    String spellId = buf.readUtf(64);
                    int variants = buf.readVarInt();
                    List<List<String>> lines = new ArrayList<>();
                    for (int v = 0; v < variants; v++) {
                        int n = buf.readVarInt();
                        List<String> vLines = new ArrayList<>();
                        for (int i = 0; i < n; i++) vLines.add(buf.readUtf(128));
                        lines.add(vLines);
                    }
                    ctx.queue(() -> CHANT.onStart(spellId, lines));
                }
                case ChantNetwork.PROGRESS -> {
                    int variant = buf.readVarInt();
                    int lineIndex = buf.readVarInt();
                    boolean error = buf.readBoolean();
                    ctx.queue(() -> CHANT.onProgress(variant, lineIndex, error));
                }
                case ChantNetwork.END -> {
                    boolean success = buf.readBoolean();
                    ctx.queue(() -> CHANT.onEnd(success));
                }
                default -> { }
            }
        });
    }

    public static void render(net.minecraft.client.gui.GuiGraphics drawContext, float tickDelta) {
        ChantHud.render(drawContext, Minecraft.getInstance(), CHANT);
    }
}
