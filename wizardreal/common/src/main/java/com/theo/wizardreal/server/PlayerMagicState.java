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

    // player uuid -> current mana
    private final Map<UUID, Float> mana = new HashMap<>();
    // player uuid -> max mana
    private final Map<UUID, Float> maxMana = new HashMap<>();
    // player uuid -> spell id -> learning points (0.4.0 D-D2: single source of
    // truth for progression; absent entry = the 10% starting baseline).
    private final Map<UUID, Map<String, Float>> learning = new HashMap<>();
    // player uuid -> spell id -> world time when cooldown ends
    private final Map<UUID, Map<String, Long>> cooldownUntil = new HashMap<>();
    // wizardReal#43 practice daily cap: player uuid -> spell id -> [dayStamp, count]
    private final Map<UUID, Map<String, long[]>> practiceDaily = new HashMap<>();

    private static PlayerMagicState INSTANCE;
    private static Path savePath;

    PlayerMagicState() {}

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
    // Learning (0.4.0 D-D2: points are the single progression source;
    // known = t > [learning].knownThreshold, derived — never stored)
    // ------------------------------------------------------------------

    /** Raw stored points, or {@link Float#NaN} when the spell sits at the baseline. */
    public float learningPoints(UUID player, String spellId) {
        Map<String, Float> m = learning.get(player);
        Float v = m == null ? null : m.get(spellId);
        return v == null ? Float.NaN : v;
    }

    /** Mastery percentage: absent entries read as the 10% starting baseline. */
    public float learningPercent(UUID player, String spellId, float difficulty) {
        float stored = learningPoints(player, spellId);
        if (Float.isNaN(stored)) return LearningCurve.GATE_PERCENT;
        return stored / Math.max(0.01f, difficulty);
    }

    public void addLearningPercent(UUID player, String spellId, float deltaPercent, float difficulty) {
        setLearningPercent(player, spellId, learningPercent(player, spellId, difficulty) + deltaPercent, difficulty);
    }

    /** Set mastery directly (command/admin path); clamped to [0, 625]. */
    public void setLearningPercent(UUID player, String spellId, float percent, float difficulty) {
        float clamped = Math.max(0f, Math.min(percent, LearningCurve.MAX_PERCENT));
        learning.computeIfAbsent(player, k -> new HashMap<>()).put(spellId, clamped * Math.max(0.01f, difficulty));
    }

    /** Stored entries only (sync/debug); absent spells sit at the baseline. */
    public Map<String, Float> learningSnapshot(UUID player) {
        return Collections.unmodifiableMap(
                learning.getOrDefault(player, Collections.emptyMap()));
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

    public void clearCooldown(UUID player, String spellId) {
        Map<String, Long> map = cooldownUntil.get(player);
        if (map != null) map.remove(spellId);
    }

    public void clearAllCooldowns(UUID player) {
        cooldownUntil.remove(player);
    }

    // ------------------------- practice daily cap (wizardReal#43) ---------

    /** How many practice settlements {@code player} used for {@code spellId}
     *  on {@code dayStamp} (0 when none — a different day resets the count). */
    public int practiceAttemptsToday(UUID player, String spellId, long dayStamp) {
        Map<String, long[]> m = practiceDaily.get(player);
        if (m == null) return 0;
        long[] entry = m.get(spellId);
        return (entry != null && entry[0] == dayStamp) ? (int) entry[1] : 0;
    }

    /** Record one practice settlement (increments today's counter). */
    public void recordPracticeAttempt(UUID player, String spellId, long dayStamp) {
        Map<String, long[]> m = practiceDaily.computeIfAbsent(player, k -> new HashMap<>());
        long[] entry = m.get(spellId);
        if (entry != null && entry[0] == dayStamp) {
            entry[1]++;
        } else {
            m.put(spellId, new long[]{dayStamp, 1});
        }
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

        CompoundTag learningTag = new CompoundTag();
        for (Map.Entry<UUID, Map<String, Float>> e : learning.entrySet()) {
            CompoundTag inner = new CompoundTag();
            for (Map.Entry<String, Float> le : e.getValue().entrySet()) {
                inner.putFloat(le.getKey(), le.getValue());
            }
            learningTag.put(e.getKey().toString(), inner);
        }
        nbt.put("learning", learningTag);

        CompoundTag cdTag = new CompoundTag();
        for (Map.Entry<UUID, Map<String, Long>> e : cooldownUntil.entrySet()) {
            CompoundTag inner = new CompoundTag();
            for (Map.Entry<String, Long> ce : e.getValue().entrySet()) {
                inner.putLong(ce.getKey(), ce.getValue());
            }
            cdTag.put(e.getKey().toString(), inner);
        }
        nbt.put("cooldowns", cdTag);

        CompoundTag practiceTag = new CompoundTag();
        for (Map.Entry<UUID, Map<String, long[]>> e : practiceDaily.entrySet()) {
            CompoundTag inner = new CompoundTag();
            for (Map.Entry<String, long[]> pe : e.getValue().entrySet()) {
                inner.putLong(pe.getKey() + "|day", pe.getValue()[0]);
                inner.putInt(pe.getKey() + "|count", (int) pe.getValue()[1]);
            }
            practiceTag.put(e.getKey().toString(), inner);
        }
        nbt.put("practiceDaily", practiceTag);
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
        if (nbt.contains("learning", Tag.TAG_COMPOUND)) {
            CompoundTag tag = nbt.getCompound("learning");
            for (String key : tag.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    CompoundTag inner = tag.getCompound(key);
                    Map<String, Float> map = new HashMap<>();
                    for (String s : inner.getAllKeys()) map.put(s, inner.getFloat(s));
                    learning.put(uuid, map);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        // Legacy migration (0.3.x -> 0.4.0, D-D2): knownSpells carried the
        // castable set — every spell now sits at the 10% baseline by default,
        // so known entries need no storage. forgottenSpells meant "cannot
        // cast" -> store 0 points so the gate keeps blocking them.
        if (nbt.contains("forgottenSpells", Tag.TAG_COMPOUND)) {
            CompoundTag tag = nbt.getCompound("forgottenSpells");
            for (String key : tag.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ListTag list = tag.getList(key, Tag.TAG_STRING);
                    for (int i = 0; i < list.size(); i++) {
                        learning.computeIfAbsent(uuid, k -> new HashMap<>())
                                .put(list.getString(i), 0f);
                    }
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
        if (nbt.contains("practiceDaily", Tag.TAG_COMPOUND)) {
            CompoundTag tag = nbt.getCompound("practiceDaily");
            for (String key : tag.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    CompoundTag inner = tag.getCompound(key);
                    Map<String, long[]> map = new HashMap<>();
                    for (String s : inner.getAllKeys()) {
                        if (!s.endsWith("|day")) continue;
                        String spellId = s.substring(0, s.length() - 4);
                        map.put(spellId, new long[]{inner.getLong(s), inner.getInt(spellId + "|count")});
                    }
                    practiceDaily.put(uuid, map);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
}
