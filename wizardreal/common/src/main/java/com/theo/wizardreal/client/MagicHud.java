package com.theo.wizardreal.client;

import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.net.MagicClientState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Renders the mana bar (above the XP bar) and active cooldown icons.
 *
 * <p>Mana bar: blue gradient, width matches XP bar, shows current / max.
 * Cooldowns: small spell-initial letters above the mana bar, fading as they expire.
 */
public final class MagicHud {

    private MagicHud() {}

    private static final int BAR_HEIGHT = 5;
    private static final int BAR_COLOR_BG = 0xFF1a1a2e;
    private static final int BAR_COLOR_FILL = 0xFF4a90e2;
    private static final int BAR_COLOR_FILL_BRIGHT = 0xFF87ceeb;

    public static void render(GuiGraphics ctx, Minecraft mc) {
        if (mc.options.hideGui || mc.player == null) return;

        Font tr = mc.font;
        int sw = ctx.guiWidth();
        int sh = ctx.guiHeight();

        // Position: directly above the XP bar (or above the hotbar if no XP)
        int barY = sh - 32;
        if (mc.player.experienceProgress > 0 || mc.player.experienceLevel > 0) {
            barY = sh - 32; // above XP bar
        } else {
            barY = sh - 24; // above hotbar when no XP
        }

        int barW = 180;
        int barX = (sw - barW) / 2;

        float ratio = MagicClientState.maxMana > 0
                ? MagicClientState.mana / MagicClientState.maxMana
                : 0f;
        int fillW = Math.round(barW * Math.min(1f, ratio));

        // Background
        ctx.fill(barX, barY, barX + barW, barY + BAR_HEIGHT, BAR_COLOR_BG);
        // Fill (simple gradient via two segments)
        if (fillW > 0) {
            int mid = fillW / 2;
            ctx.fill(barX, barY, barX + mid, barY + BAR_HEIGHT, BAR_COLOR_FILL);
            ctx.fill(barX + mid, barY, barX + fillW, barY + BAR_HEIGHT, BAR_COLOR_FILL_BRIGHT);
        }

        // Text: "Mana: 156/200" (localized) centered above bar
        Component label = Component.translatable("wizardreal.hud.mana",
                String.format(java.util.Locale.ROOT, "%.0f", MagicClientState.mana),
                String.format(java.util.Locale.ROOT, "%.0f", MagicClientState.maxMana))
                .withStyle(ChatFormatting.AQUA);
        int textW = tr.width(label);
        ctx.drawString(tr, label, barX + (barW - textW) / 2, barY - 10, 0x55FFFF);

        // Cooldown icons
        renderCooldowns(ctx, tr, barX, barY - 22, barW);
    }

    private static void renderCooldowns(GuiGraphics ctx, Font tr, int x, int y, int maxW) {
        long now = System.currentTimeMillis();
        List<Map.Entry<String, Long>> active = new ArrayList<>();
        for (Map.Entry<String, Long> e : MagicClientState.snapshotCooldowns().entrySet()) {
            if (e.getValue() > now) active.add(e);
        }
        if (active.isEmpty()) return;

        int iconSize = 12;
        int gap = 4;
        int totalW = active.size() * iconSize + (active.size() - 1) * gap;
        int startX = x + (maxW - totalW) / 2;

        int idx = 0;
        for (Map.Entry<String, Long> e : active) {
            int ix = startX + idx * (iconSize + gap);
            float remainingSec = (e.getValue() - now) / 1000f;

            Spell spell = SpellRegistry.get(e.getKey()).orElse(null);
            String initial = spell == null ? "?" : spell.nameKey().replace("spell.", "").replace("wizardreal:", "").substring(0, 1).toUpperCase();

            // Icon background
            ctx.fill(ix, y, ix + iconSize, y + iconSize, 0xFF222222);
            // Text
            String txt = String.format(java.util.Locale.ROOT, "%.0f", remainingSec);
            int tw = tr.width(txt);
            ctx.drawString(tr, Component.literal(txt).withStyle(ChatFormatting.WHITE), ix + (iconSize - tw) / 2, y + 2, 0xFFFFFF, false);

            idx++;
        }
    }
}
