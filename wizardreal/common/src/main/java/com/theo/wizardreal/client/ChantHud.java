package com.theo.wizardreal.client;

import com.theo.wizardreal.api.catalog.CatalogPayload;
import com.theo.wizardreal.config.WizardRealConfig;
import com.theo.wizardreal.net.ChantNetwork;
import com.theo.wizardreal.net.MagicClientState;

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
 *
 * <p>Annotation layer (R-B, D4 default on; ruby-above per user ruling
 * 2026-09-22): the CURRENT line gets a small gray reading row above it
 * (payload-derived readings via {@link ChantReadings}, {@code [chantReadings]}
 * config). The current line and everything below it shift down by
 * {@link #RUBY_SHIFT} only while a reading is shown; with the switch off, no
 * mapping, or an error retry hint occupying the space above the line, the
 * layout is byte-identical to the pre-annotation HUD (see {@link #lineY}).
 */
public final class ChantHud {
    private ChantHud() {}

    // Anchor: crosshair right.
    private static final int X_OFFSET = 16;
    private static final int LINE_H = 11;
    private static final int FADE_MS = 2500;
    /** Extra stride for the current line while its reading row is shown. */
    static final int RUBY_SHIFT = 6;
    /** Reading row tone (gray, matches the dimmed upcoming-line family). */
    private static final int READING_COLOR = 0x999999;
    /** Indent so the reading sits over the line TEXT, not the "► " prefix. */
    private static final String CURRENT_PREFIX = "► ";

    public static void render(GuiGraphics ctx, Minecraft mc, ChantState state) {
        if (state == null) return;
        long now = System.currentTimeMillis();

        // Idle-recognition notice (issue #41 拒识有反馈): a transient line at
        // the chant anchor, rendered independently of any active chant — the
        // rejection feedback must reach an idle player too.
        if (state.noticeKind != null && now - state.noticeMs <= ChantState.NOTICE_TTL_MS) {
            boolean whisper = ChantNetwork.NOTICE_WHISPER.equals(state.noticeKind)
                    && state.noticeNameKey != null && !state.noticeNameKey.isEmpty();
            Component notice = whisper
                    ? Component.translatable("wizardreal.chant.whisper",
                            Component.translatable(state.noticeNameKey))
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC)
                    : Component.translatable("wizardreal.chant.dissipate")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            ctx.drawString(mc.font, notice, ctx.guiWidth() / 2 + X_OFFSET,
                    ctx.guiHeight() / 2 - 24, 0xFFFFFF);
        }

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

        // Title: spell name + mastery percent (0.4.0 learning sync). spellId
        // already carries its namespace; lang keys are "spell.<id>.name".
        float mastery = MagicClientState.learningPercent(state.spellId);
        Component title = Component.translatable("spell." + state.spellId + ".name")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .copy()
                .append(Component.literal(String.format("  %.0f%%", mastery))
                        .withStyle(mastery >= 100f ? ChatFormatting.GREEN : ChatFormatting.GRAY));
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

        String reading = currentReading(mc, state, lines);
        // The error-retry hint is drawn ABOVE the current line (y-9) — the
        // same space the ruby row uses, so on a wrong line the hint wins and
        // the layout falls back to the pre-annotation geometry entirely.
        boolean ruby = reading != null && !state.error;
        for (int i = 0; i < total; i++) {
            int y = lineY(startY, i, state.lineIndex, ruby);
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
                if (ruby) {
                    int anchorX = crossX + tr.width(CURRENT_PREFIX);
                    // half-scale row: glyph budget is twice the screen-space
                    // width right of the anchor (ChantRuby maxWidth*2 semantics)
                    drawReading(ctx, tr, reading, anchorX, y,
                            Math.max(1, (screenW - anchorX) * 2));
                }
            } else {
                ctx.drawString(tr, Component.literal("   ").append(text.copy().withStyle(ChatFormatting.GRAY)),
                        crossX, y, 0x888888);
            }
        }
    }

    /** Vertical position of one line. Unchanged from the pre-annotation HUD
     *  except for the {@link #RUBY_SHIFT} push of the current line AND
     *  everything below it while the current line shows its reading row
     *  above itself (zero shift otherwise — the reading then fits in the
     *  gap the push opens, clear of the previous line's text). */
    static int lineY(int startY, int index, int currentIndex, boolean rubyAboveCurrent) {
        return startY + index * LINE_H
                + (rubyAboveCurrent && index >= currentIndex ? RUBY_SHIFT : 0);
    }

    /** The current line's annotation (D2-selected, may be {@code null}):
     *  config switch (D4 default on) → payload spell lookup → variant/bucket
     *  alignment with per-line text fallback → policy selection. */
    private static String currentReading(Minecraft mc, ChantState state, List<String> lines) {
        WizardRealConfig.ReadingsSettings settings =
                WizardRealConfig.loadCached(mc.gameDirectory.toPath()).chantReadings();
        if (!settings.hud()) return null;
        CatalogPayload.CatalogSpell spell =
                ChantReadings.findSpell(SpellCatalogState.last(), state.spellId);
        ChantReadings.Resolved resolved = ChantReadings.resolve(spell, lines, state.lineIndex);
        return ChantReadings.select(resolved.bucket(), mc.getLanguageManager().getSelected(),
                settings, resolved.readings());
    }

    /** Half-size gray reading row above the given anchor line, clipped to
     *  {@code maxGlyphWidth} glyphs (the row is drawn at half scale, so its
     *  glyph budget is twice the screen-space width — ChantRuby's
     *  maxWidth*2 semantics). */
    private static void drawReading(GuiGraphics ctx, Font font, String reading, int x, int y,
                                    int maxGlyphWidth) {
        String clipped = font.plainSubstrByWidth(reading, maxGlyphWidth);
        ctx.pose().pushPose();
        ctx.pose().translate(x, y - RUBY_SHIFT, 0);
        ctx.pose().scale(0.5f, 0.5f, 1f);
        ctx.drawString(font, clipped, 0, 0, READING_COLOR, false);
        ctx.pose().popPose();
    }
}
