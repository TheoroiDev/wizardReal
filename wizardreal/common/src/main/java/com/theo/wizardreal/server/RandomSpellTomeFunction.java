package com.theo.wizardreal.server;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.item.SpellTomeItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.Serializer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;

/**
 * Runtime-only loot function that binds a tome to a random learnable spell
 * (a {@link Spell} with {@code requiresLearning()}), picked with the loot
 * context's seeded {@code RandomSource} so the same roll reproduces under the
 * same seed. The pool is injected at loot-table modify time and never
 * serialized, so the dummy serializer below is unreachable.
 */
public final class RandomSpellTomeFunction implements LootItemFunction {

    private static final LootItemFunctionType TYPE = new LootItemFunctionType(
            new Serializer<RandomSpellTomeFunction>() {
                @Override
                public void serialize(JsonObject json, RandomSpellTomeFunction function,
                                      JsonSerializationContext context) {
                    throw new UnsupportedOperationException("runtime-only loot function");
                }

                @Override
                public RandomSpellTomeFunction deserialize(JsonObject json,
                                                           JsonDeserializationContext context) {
                    throw new UnsupportedOperationException("runtime-only loot function");
                }
            });

    private RandomSpellTomeFunction() {}

    /** Loot item builder hook. */
    public static LootItemFunction.Builder builder() {
        return RandomSpellTomeFunction::new;
    }

    @Override
    public LootItemFunctionType getType() {
        return TYPE;
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        List<Spell> learnable = new ArrayList<>();
        for (Spell spell : SpellRegistry.all()) {
            if (spell.requiresLearning()) learnable.add(spell);
        }
        if (learnable.isEmpty()) return stack;
        Spell spell = learnable.get(context.getRandom().nextInt(learnable.size()));
        SpellTomeItem.setSpellId(stack, spell.id());
        return stack;
    }
}
