package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * Weather dial (diluvium / tempestas): {@code rain}, {@code thunder} or
 * {@code clear} for {@code duration_seconds}. Only meaningful on overworld
 * style dimensions with weather; other levels ignore it.
 */
public record WeatherEffect(String mode, int durationSeconds) implements SpellEffect {
    public static final MapCodec<WeatherEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("mode", "rain").forGetter(WeatherEffect::mode),
                    Codec.INT.optionalFieldOf("duration_seconds", 120).forGetter(WeatherEffect::durationSeconds)
            ).apply(instance, WeatherEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.WEATHER;
    }

    @Override
    public void apply(CastContext ctx) {
        if (!(ctx.caster().level() instanceof ServerLevel level)) return;
        int ticks = (int) EffectPower.linear(ctx, durationSeconds * 20);
        switch (mode) {
            case "clear" -> level.setWeatherParameters(ticks, 0, false, false);
            case "thunder" -> level.setWeatherParameters(0, ticks, true, true);
            default -> level.setWeatherParameters(0, ticks, true, false);
        }
    }
}
