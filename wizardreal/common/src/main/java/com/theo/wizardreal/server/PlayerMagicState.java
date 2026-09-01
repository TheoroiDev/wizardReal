package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Server-side persistent data holding per-player magic state (mana, known spells,
 * cooldowns). Backed by a file in the world directory instead of
 * {@link net.minecraft.world.level.saveddata.SavedData} to avoid API churn across versions.
 *
 * <p>Auto-saved on server stop and periodically every 5 minutes
 * (the periodic hook lives in {@link ManaManager}).
 */
public final class PlayerMagicState {

    private static final float DEFAULT_MAX_MANA = 200f;
    private static final float MANA_PER_TICK = 0.1f; // 2 per second at 20 tps

    private static final Set<String> DEFAULT_SPELLS = Set.of(
            "wizardreal:ignis", "wizardreal:fulmen", "wizardreal:vitae",
            "wizardreal:aegis", "wizardreal:ictus"
    );

    // player uuid -> current mana
    private final Map<UUID, Float> mana = new HashMap<>();
    // player uuid -> max mana
    private final Map<UUID, Float> maxMana = new HashMap<>();
    // player uuid -> known spell ids
    private final Map<UUID, Set<String>> knownSpells = new HashMap<>();
    // player uuid -> spell id -> world time when cooldown ends
    private final Map<UUID, Map<String, Long>> cooldownUntil = new HashMap<>();

    private static PlayerMagicState INSTANCE;
    private static Path savePath;

    private PlayerMagicState() {}

    /**
     * Register the lifecycle hooks. The cached instance is cleared on server
     * stop so a new world (singleplayer save switch, or server restart with a
     * different save) always loads its own data — otherwise the previous
     * world's mana/spells would leak into the next one and be saved there.
     */
    public static void registerHooks() {
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            PlayerMagicState st = INSTANCE;
            if (st != null) st.save();
            INSTANCE = null;
            savePath = null;
        });
    }

    public static synchronized PlayerMagicState get(MinecraftServer server) {
        if (INSTANCE == null) {
            INSTANCE = new PlayerMagicState();
            savePath = server.getWorldPath(LevelResource.ROOT).resolve("wizardreal_player_magic.nbt");
            INSTANCE.load();
        }
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Mana
    // ------------------------------------------------------------------
    public float getMana(UUID player) {
        return mana.getOrDefault(player, DEFAULT_MAX_MANA);
    }

    public float getMaxMana(UUID player) {
        return maxMana.getOrDefault(player, DEFAULT_MAX_MANA);
    }

    public void setMana(UUID player, float value) {
        mana.put(player, Math.min(value, getMaxMana(player)));
    }

    public void consumeMana(UUID player, float amount) {
        float current = getMana(player);
        mana.put(player, Math.max(0f, current - amount));
    }

    /** Tick recovery: called from ManaManager each server tick. */
    public void tickRecovery(Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            UUID uuid = p.getUUID();
            float current = getMana(uuid);
            float cap = getMaxMana(uuid);
            if (current < cap) {
                mana.put(uuid, Math.min(cap, current + MANA_PER_TICK));
            }
        }
    }

    // ------------------------------------------------------------------
    // Known spells
    // ------------------------------------------------------------------
    public boolean knowsSpell(UUID player, String spellId) {
        return knownSpells.computeIfAbsent(player, k -> {
            HashSet<String> set = new HashSet<>(DEFAULT_SPELLS);
            return set;
        }).contains(spellId);
    }

    public void learnSpell(UUID player, String spellId) {
        knownSpells.computeIfAbsent(player, k -> new HashSet<>(DEFAULT_SPELLS)).add(spellId);
    }

    public Set<String> getKnownSpells(UUID player) {
        return Collections.unmodifiableSet(
                knownSpells.computeIfAbsent(player, k -> new HashSet<>(DEFAULT_SPELLS)));
    }

    // ------------------------------------------------------------------
    // Cooldowns
    // ------------------------------------------------------------------
    public boolean isOnCooldown(UUID player, String spellId, long now) {
        return cooldownUntil.getOrDefault(player, Collections.emptyMap()).getOrDefault(spellId, 0L) > now;
    }

    public long getCooldownEnd(UUID player, String spellId) {
        return cooldownUntil.getOrDefault(player, Collections.emptyMap()).getOrDefault(spellId, 0L);
    }

    public Map<String, Long> getCooldowns(UUID player) {
        return Collections.unmodifiableMap(
                cooldownUntil.getOrDefault(player, Collections.emptyMap()));
    }

    public void setCooldown(UUID player, String spellId, long until) {
        cooldownUntil.computeIfAbsent(player, k -> new HashMap<>()).put(spellId, until);
    }

    /** Remove expired cooldowns to keep data small. */
    public void pruneCooldowns(long now) {
        for (Map<String, Long> map : cooldownUntil.values()) {
            map.values().removeIf(end -> end <= now);
        }
    }

    // ------------------------------------------------------------------
    // Save / Load
    // ------------------------------------------------------------------
    public void save() {
        if (savePath == null) return;
        try {
            CompoundTag nbt = writeNbt();
            Files.createDirectories(savePath.getParent());
            NbtIo.write(nbt, savePath.toFile());
            WizardReal.LOGGER.debug("Saved PlayerMagicState");
        } catch (IOException e) {
            WizardReal.LOGGER.error("Failed to save PlayerMagicState", e);
        }
    }

    private void load() {
        if (savePath == null || !Files.exists(savePath)) return;
        try {
            CompoundTag nbt = NbtIo.read(savePath.toFile());
            if (nbt != null) fromNbt(nbt);
            WizardReal.LOGGER.info("Loaded PlayerMagicState from {}", savePath);
        } catch (IOException e) {
            WizardReal.LOGGER.error("Failed to load PlayerMagicState", e);
        }
    }

    CompoundTag writeNbt() {
        CompoundTag nbt = new CompoundTag();
        CompoundTag manaTag = new CompoundTag();
        for (Map.Entry<UUID, Float> e : mana.entrySet()) {
            manaTag.putFloat(e.getKey().toString(), e.getValue());
        }
        nbt.put("mana", manaTag);

        CompoundTag maxTag = new CompoundTag();
        for (Map.Entry<UUID, Float> e : maxMana.entrySet()) {
            maxTag.putFloat(e.getKey().toString(), e.getValue());
        }
        nbt.put("maxMana", maxTag);

        CompoundTag knownTag = new CompoundTag();
        for (Map.Entry<UUID, Set<String>> e : knownSpells.entrySet()) {
            ListTag list = new ListTag();
            for (String s : e.getValue()) list.add(StringTag.valueOf(s));
            knownTag.put(e.getKey().toString(), list);
        }
        nbt.put("knownSpells", knownTag);

        CompoundTag cdTag = new CompoundTag();
        for (Map.Entry<UUID, Map<String, Long>> e : cooldownUntil.entrySet()) {
            CompoundTag inner = new CompoundTag();
            for (Map.Entry<String, Long> ce : e.getValue().entrySet()) {
                inner.putLong(ce.getKey(), ce.getValue());
            }
            cdTag.put(e.getKey().toString(), inner);
        }
        nbt.put("cooldowns", cdTag);
        return nbt;
    }

    void fromNbt(CompoundTag nbt) {
        if (nbt.contains("mana", Tag.TAG_COMPOUND)) {
            CompoundTag tag = nbt.getCompound("mana");
            for (String key : tag.getAllKeys()) {
                try { mana.put(UUID.fromString(key), tag.getFloat(key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        if (nbt.contains("maxMana", Tag.TAG_COMPOUND)) {
            CompoundTag tag = nbt.getCompound("maxMana");
            for (String key : tag.getAllKeys()) {
                try { maxMana.put(UUID.fromString(key), tag.getFloat(key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        if (nbt.contains("knownSpells", Tag.TAG_COMPOUND)) {
            CompoundTag tag = nbt.getCompound("knownSpells");
            for (String key : tag.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ListTag list = tag.getList(key, Tag.TAG_STRING);
                    Set<String> set = new HashSet<>();
                    for (int i = 0; i < list.size(); i++) set.add(list.getString(i));
                    knownSpells.put(uuid, set);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (nbt.contains("cooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag tag = nbt.getCompound("cooldowns");
            for (String key : tag.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    CompoundTag inner = tag.getCompound(key);
                    Map<String, Long> map = new HashMap<>();
                    for (String s : inner.getAllKeys()) map.put(s, inner.getLong(s));
                    cooldownUntil.put(uuid, map);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
}
