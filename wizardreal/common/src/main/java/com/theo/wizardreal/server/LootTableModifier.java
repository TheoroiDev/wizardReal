package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.item.WizardRealItems;
import dev.architectury.event.events.common.LootEvent;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Injects WizardReal items (spell tomes, scrolls, staves) into vanilla
 * structure chest loot tables. Cross-platform via Architectury LootEvent.
 */
public final class LootTableModifier {

    private static final Set<ResourceLocation> CHEST_LOOT_TABLES = Set.of(
            new ResourceLocation("minecraft", "chests/desert_pyramid"),
            new ResourceLocation("minecraft", "chests/jungle_temple"),
            new ResourceLocation("minecraft", "chests/woodland_mansion"),
            new ResourceLocation("minecraft", "chests/stronghold_library"),
            new ResourceLocation("minecraft", "chests/stronghold_crossing"),
            new ResourceLocation("minecraft", "chests/abandoned_mineshaft"),
            new ResourceLocation("minecraft", "chests/simple_dungeon")
    );

    private LootTableModifier() {}

    public static void init() {
        LootEvent.MODIFY_LOOT_TABLE.register((lootTable, id, context, builtin) -> {
            if (!builtin || !CHEST_LOOT_TABLES.contains(id)) return;

            // Low chance to find a blank scroll
            context.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(WizardRealItems.SCROLL_BLANK.get()))
                    .when(LootItemRandomChanceCondition.randomChance(0.15f))
                    .build());

            // Rare chance to find a spell tome
            context.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(WizardRealItems.SPELL_TOME.get()))
                    .when(LootItemRandomChanceCondition.randomChance(0.08f))
                    .build());

            // Very rare chance to find a fire or lightning staff
            context.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(WizardRealItems.STAFF_FIRE.get()))
                    .add(LootItem.lootTableItem(WizardRealItems.STAFF_LIGHTNING.get()))
                    .when(LootItemRandomChanceCondition.randomChance(0.03f))
                    .build());
        });
        WizardReal.LOGGER.info("LootTableModifier registered");
    }
}
