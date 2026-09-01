package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.theo.wizardreal.WizardReal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry of spell-effect types. Built-in primitives and addon effects use
 * the same registration path; each type contributes a {@link MapCodec} for its
 * parameter record, and JSON dispatches on the {@code "type"} field.
 *
 * <p>The polymorphic codec is hand-rolled (decode: read "type", look up, feed
 * the full map to the type codec; encode: merge the type key with the type's
 * own fields) so we do not depend on DFU dispatch signature details.
 */
public final class EffectRegistry {
    private static final Map<ResourceLocation, MapCodec<? extends SpellEffect>> TYPES = new HashMap<>();
    private static volatile Codec<SpellEffect> codec;

    private EffectRegistry() {}

    /** Register an effect type. Call during mod init, before any datapack load. */
    public static synchronized void register(ResourceLocation id, MapCodec<? extends SpellEffect> typeCodec) {
        if (TYPES.containsKey(id)) {
            throw new IllegalStateException("Duplicate spell effect type: " + id);
        }
        TYPES.put(id, typeCodec);
        codec = null; // invalidate cached dispatch codec
    }

    public static synchronized MapCodec<? extends SpellEffect> get(ResourceLocation id) {
        return TYPES.get(id);
    }

    /** Polymorphic codec: {@code {"type": "<ns>:<id>", ...params}}. */
    public static Codec<SpellEffect> codec() {
        Codec<SpellEffect> c = codec;
        if (c == null) {
            synchronized (EffectRegistry.class) {
                c = codec;
                if (c == null) {
                    c = new DispatchCodec().codec();
                    codec = c;
                }
            }
        }
        return c;
    }

    /** Codec for a registry entry addressed by its id string (e.g. "minecraft:zoglin"). */
    public static <T> Codec<T> registryByName(Registry<T> registry) {
        return Codec.STRING.comapFlatMap(
                name -> {
                    ResourceLocation id = ResourceLocation.tryParse(name);
                    if (id == null) return DataResult.error(() -> "Invalid id: " + name);
                    T value = registry.get(id);
                    return value == null
                            ? DataResult.error(() -> "Unknown id: " + name)
                            : DataResult.success(value);
                },
                value -> {
                    ResourceLocation id = registry.getKey(value);
                    return id == null ? value.toString() : id.toString();
                });
    }

    private static final class DispatchCodec extends MapCodec<SpellEffect> {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of(ops.createString("type"));
        }

        @Override
        public <T> DataResult<SpellEffect> decode(DynamicOps<T> ops, MapLike<T> input) {
            T typeValue = input.get("type");
            if (typeValue == null) {
                return DataResult.error(() -> "Missing spell effect 'type' field");
            }
            return ResourceLocation.CODEC.parse(ops, typeValue).flatMap(id -> {
                MapCodec<? extends SpellEffect> type = EffectRegistry.get(id);
                if (type == null) {
                    WizardReal.LOGGER.warn("Unknown spell effect type: {}", id);
                    return DataResult.error(() -> "Unknown spell effect type: " + id);
                }
                return decodeTyped(type, ops, input);
            });
        }

        @SuppressWarnings("unchecked")
        private <T> DataResult<SpellEffect> decodeTyped(MapCodec<? extends SpellEffect> type,
                                                        DynamicOps<T> ops, MapLike<T> input) {
            return ((MapCodec<SpellEffect>) type).decode(ops, input);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> RecordBuilder<T> encode(SpellEffect effect, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            ResourceLocation id = effect.effectId();
            MapCodec<? extends SpellEffect> type = EffectRegistry.get(id);
            RecordBuilder<T> withType = prefix.add("type", ops.createString(id.toString()));
            if (type == null) {
                WizardReal.LOGGER.warn("Unregistered spell effect type: {}", id);
                return withType;
            }
            return ((MapCodec) type).encode(effect, ops, withType);
        }
    }
}
