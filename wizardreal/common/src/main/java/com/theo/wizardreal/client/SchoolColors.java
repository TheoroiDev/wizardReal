package com.theo.wizardreal.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.School;
import net.minecraft.ChatFormatting;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * Runtime school palette (wizardReal#44): the single source of truth lives in
 * the workspace asset plan ({@code assets/_plan/palette.json}); the
 * build-checked copy this class loads is
 * {@code assets/wizardreal/_gen/schools_palette.json} (regenerate via
 * {@code tools/palette/gen_schools.py} after editing the source). Each school
 * carries three tones: {@code accent} (the spell dust color), {@code glow}
 * (lighter) and {@code dark} (darker) — HUD text colors, particle dyeing and
 * icon recoloring all read from here instead of ad-hoc constants.
 */
public final class SchoolColors {

    public record Tones(int accent, int glow, int dark) {}

    private static final Map<School, Tones> TONES = new EnumMap<>(School.class);
    private static boolean loaded;

    private SchoolColors() {}

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = SchoolColors.class.getResourceAsStream(
                "/assets/wizardreal/_gen/schools_palette.json")) {
            if (in == null) {
                WizardReal.LOGGER.warn("schools_palette.json missing — school colors fall back to white");
                return;
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (School school : School.values()) {
                String name = school.name().toLowerCase(java.util.Locale.ROOT);
                if (!root.has("schools") || !root.getAsJsonObject("schools").has(name)) continue;
                JsonObject tones = root.getAsJsonObject("schools").getAsJsonObject(name);
                TONES.put(school, new Tones(
                        Integer.parseInt(tones.get("accent").getAsString().substring(1), 16),
                        Integer.parseInt(tones.get("glow").getAsString().substring(1), 16),
                        Integer.parseInt(tones.get("dark").getAsString().substring(1), 16)));
            }
        } catch (Exception e) {
            WizardReal.LOGGER.error("Failed to load school palette", e);
        }
    }

    /** Accent tone for the school, or {@code null} when uncolored. */
    public static Tones tones(School school) {
        load();
        return school == null ? null : TONES.get(school);
    }

    /** Accent as ARGB for particle dyeing / drawString (opaque). */
    public static int accent(School school) {
        Tones t = tones(school);
        return t == null ? 0xFFFFFF : 0xFF000000 | t.accent();
    }

    /** Nearest vanilla chat formatting for text: the accent mapped to the
     *  closest of the 16 legacy formatting colors. */
    public static ChatFormatting textFormatting(School school) {
        Tones t = tones(school);
        if (t == null) return ChatFormatting.WHITE;
        ChatFormatting best = ChatFormatting.WHITE;
        double bestD = Double.MAX_VALUE;
        int[] rgb = {(t.accent() >> 16) & 255, (t.accent() >> 8) & 255, t.accent() & 255};
        for (ChatFormatting f : ChatFormatting.values()) {
            Integer c = f.getColor();
            if (c == null) continue;
            int[] fc = {(c >> 16) & 255, (c >> 8) & 255, c & 255};
            double d = 0;
            for (int i = 0; i < 3; i++) d += Math.pow(rgb[i] - fc[i], 2);
            if (d < bestD) {
                bestD = d;
                best = f;
            }
        }
        return best;
    }
}
