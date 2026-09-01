package com.theo.wizardreal.client;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Renders the in-progress ritual incantation to the right of the crosshair:
 * the spell name, each line done (green check), the current line highlighted
 * (red flash on a wrong line), and upcoming lines dimmed. Fades out after the
 * chant ends. Anchor is a constant for now (crosshair-right); it's structured
 * to become a configurable anchor later, like the VoiceCast HUD.
 */
public final class ChantHud {
    private ChantHud() {}

    // Anchor: crosshair right.
    private static final int X_OFFSET = 16;
    private static final int LINE_H = 11;
    private static final int FADE_MS = 2500;

    public static void render(GuiGraphics ctx, Minecraft mc, ChantState state) {
        if (state == null) return;
        long now = System.currentTimeMillis();

        if (!state.active) {
            if (state.endedMs == 0 || now - state.endedMs > FADE_MS) return;
            // brief end flash handled by callers via result text; nothing persistent
            return;
        }

        List<String> lines = null;
        if (state.variant >= 0 && state.variantLines != null && state.variant < state.variantLines.size()) {
            lines = state.variantLines.get(state.variant);
        }

        Font tr = mc.font;
        int screenW = ctx.guiWidth();
        int screenH = ctx.guiHeight();
        int crossX = screenW / 2 + X_OFFSET;
        int crossY = screenH / 2;

        // Title: spell name. spellId already carries its namespace (e.g.
        // "wizardreal:explosion"); lang keys are "spell.<id>.name".
        Component title = Component.translatable("spell." + state.spellId + ".name")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (lines == null) {
            // no chant variant loaded (shouldn't normally happen)
            ctx.drawString(tr, title, crossX, crossY - 14, 0xFFFFFF);
            ctx.drawString(tr,
                    Component.translatable("wizardreal.chant.prompt").withStyle(ChatFormatting.YELLOW),
                    crossX, crossY, 0xFFFFFF);
            return;
        }

        int total = lines.size();
        int startY = crossY - (total * LINE_H) / 2;
        ctx.drawString(tr, title, crossX, startY - LINE_H - 2, 0xFFFFFF);

        for (int i = 0; i < total; i++) {
            int y = startY + i * LINE_H;
            Component text = Component.translatable(lines.get(i));
            if (i < state.lineIndex) {
                ctx.drawString(tr, Component.literal("✓ ").withStyle(ChatFormatting.GREEN)
                        .append(text.copy().withStyle(ChatFormatting.GREEN, ChatFormatting.STRIKETHROUGH)),
                        crossX, y, 0x55FF55);
            } else if (i == state.lineIndex) {
                ChatFormatting f = state.error ? ChatFormatting.RED : ChatFormatting.AQUA;
                int color = state.error ? 0xFF5555 : 0x55FFFF;
                ctx.drawString(tr, Component.literal("► ").withStyle(f).append(text.copy().withStyle(f, ChatFormatting.BOLD)),
                        crossX, y, color);
                if (state.error) {
                    ctx.drawString(tr, Component.translatable("wizardreal.chant.retry").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC),
                            crossX, y - 9, 0xFF5555);
                }
            } else {
                ctx.drawString(tr, Component.literal("   ").append(text.copy().withStyle(ChatFormatting.GRAY)),
                        crossX, y, 0x888888);
            }
        }
    }
}
