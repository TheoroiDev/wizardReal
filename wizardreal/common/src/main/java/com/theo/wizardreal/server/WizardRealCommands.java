package com.theo.wizardreal.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.School;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.config.WizardRealConfig;
import com.theo.wizardreal.effect.SpellEffect;
import com.theo.wizardreal.spell.DataSpell;
import com.theo.wizardreal.api.Pronunciation;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ops cheat/debug commands under {@code /wizardreal} (alias {@code /wr}),
 * permission level 2. Registered once in common via the Architectury
 * {@link CommandRegistrationEvent} so both loaders get the same tree.
 *
 * <p>Target resolution: every state-affecting subcommand takes an optional
 * {@code target} player argument (required from the server console, where
 * there is no sender player). {@code cast} always targets the executing
 * player — a spell needs a physical position/look vector, so console use
 * makes no sense.
 *
 * <p>learn/unlearn publish the spell catalog afterwards (learned flags reach
 * clients only through the catalog payload) and force a state save.
 */
public final class WizardRealCommands {

    private static final SuggestionProvider<CommandSourceStack> SPELL_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (Spell spell : SpellRegistry.all()) {
            if (spell.id().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(spell.id());
            }
        }
        return builder.buildFuture();
    };

    private WizardRealCommands() {}

    @FunctionalInterface
    private interface PlayerCommand {
        int run(CommandContext<CommandSourceStack> ctx, ServerPlayer target) throws CommandSyntaxException;
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register(WizardRealCommands::build);
    }

    private static void build(CommandDispatcher<CommandSourceStack> dispatcher,
                              CommandBuildContext buildContext, Commands.CommandSelection selection) {
        dispatcher.register(tree("wizardreal"));
        dispatcher.register(tree("wr"));
        // wr#43: practice is a PLAYER feature — the /wr tree is op-gated, so
        // the practice loop gets its own permission-0 root (/wr practice
        // remains for ops as the same executors).
        dispatcher.register(Commands.literal("wrpractice")
                .executes(WizardRealCommands::practiceStop)
                .then(Commands.literal("stop").executes(WizardRealCommands::practiceStop))
                .then(spellArg("spell").executes(WizardRealCommands::practiceStart)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("practice")
                        .executes(WizardRealCommands::practiceStop)
                        .then(spellArg("spell").executes(WizardRealCommands::practiceStart)))
                .then(Commands.literal("learn")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(spellArg("spell")
                                        .executes(ctx -> learn(ctx, EntityArgument.getPlayer(ctx, "target"), null))
                                        .then(amountArg()
                                                .executes(ctx -> learn(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "amount"))))))
                        .then(spellArg("spell")
                                .executes(ctx -> learn(ctx, self(ctx), null))
                                .then(amountArg()
                                        .executes(ctx -> learn(ctx, self(ctx),
                                                StringArgumentType.getString(ctx, "amount"))))))
                .then(Commands.literal("unlearn")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(spellArg("spell")
                                        .executes(ctx -> unlearn(ctx, EntityArgument.getPlayer(ctx, "target"), null))
                                        .then(amountArg()
                                                .executes(ctx -> unlearn(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "amount"))))))
                        .then(spellArg("spell")
                                .executes(ctx -> unlearn(ctx, self(ctx), null))
                                .then(amountArg()
                                        .executes(ctx -> unlearn(ctx, self(ctx),
                                                StringArgumentType.getString(ctx, "amount"))))))
                .then(Commands.literal("cast")
                        .then(spellArg("spell")
                                .executes(ctx -> cast(ctx, 1.0f))
                                .then(Commands.argument("confidence", FloatArgumentType.floatArg(0.0f, 1.0f))
                                        .executes(ctx -> cast(ctx, FloatArgumentType.getFloat(ctx, "confidence"))))))
                .then(Commands.literal("spells")
                        .executes(ctx -> listSpells(ctx, ""))
                        .then(Commands.argument("filter", StringArgumentType.greedyString())
                                .executes(ctx -> listSpells(ctx, StringArgumentType.getString(ctx, "filter")))))
                .then(Commands.literal("spellinfo")
                        .then(spellArg("spell").executes(WizardRealCommands::spellInfo)))
                .then(Commands.literal("known")
                        .executes(ctx -> known(ctx, self(ctx)))
                        .then(targetThen(WizardRealCommands::known)))
                .then(Commands.literal("mana")
                        .then(Commands.literal("get")
                                .executes(ctx -> manaGet(ctx, self(ctx)))
                                .then(targetThen(WizardRealCommands::manaGet)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0f))
                                        .executes(ctx -> manaSet(ctx, FloatArgumentType.getFloat(ctx, "amount"), self(ctx)))
                                        .then(targetThen((ctx, t) -> manaSet(ctx, FloatArgumentType.getFloat(ctx, "amount"), t)))))
                        .then(Commands.literal("reset")
                                .executes(ctx -> manaReset(ctx, self(ctx)))
                                .then(targetThen(WizardRealCommands::manaReset))))
                .then(Commands.literal("cooldown")
                        .then(Commands.literal("clear")
                                .then(Commands.literal("all")
                                        .executes(ctx -> cooldownClear(ctx, null, self(ctx)))
                                        .then(targetThen((ctx, t) -> cooldownClear(ctx, null, t))))
                                .then(spellArg("spell")
                                        .executes(ctx -> cooldownClear(ctx, spellId(ctx, "spell"), self(ctx)))
                                        .then(targetThen((ctx, t) -> cooldownClear(ctx, spellId(ctx, "spell"), t)))))
                        .then(Commands.literal("set")
                                .then(spellArg("spell")
                                        .then(Commands.argument("seconds", FloatArgumentType.floatArg(0.0f))
                                                .executes(ctx -> cooldownSet(ctx, spellId(ctx, "spell"),
                                                        FloatArgumentType.getFloat(ctx, "seconds"), self(ctx)))
                                                .then(targetThen((ctx, t) -> cooldownSet(ctx, spellId(ctx, "spell"),
                                                        FloatArgumentType.getFloat(ctx, "seconds"), t)))))))
                .then(Commands.literal("sync")
                        .executes(ctx -> sync(ctx, self(ctx)))
                        .then(targetThen(WizardRealCommands::sync)))
                .then(Commands.literal("state")
                        .then(Commands.literal("save").executes(WizardRealCommands::stateSave))
                        .then(Commands.literal("dump")
                                .executes(ctx -> stateDump(ctx, self(ctx)))
                                .then(targetThen(WizardRealCommands::stateDump))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> spellArg(String name) {
        return Commands.argument(name, ResourceLocationArgument.id()).suggests(SPELL_SUGGESTIONS);
    }

    /** Optional learn/unlearn amount: bare number = +/- percentage points, "N%" = set absolute. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> amountArg() {
        return Commands.argument("amount", StringArgumentType.word());
    }

    private static String spellId(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, ResourceLocation.class).toString();
    }

    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> targetThen(PlayerCommand action) {
        return Commands.argument("target", EntityArgument.player())
                .executes(ctx -> action.run(ctx, EntityArgument.getPlayer(ctx, "target")));
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static Spell requireSpell(CommandSourceStack source, String spellId) {
        Spell spell = SpellRegistry.get(spellId).orElse(null);
        if (spell == null) {
            source.sendFailure(Component.translatable("wizardreal.cmd.unknown_spell", spellId));
        }
        return spell;
    }

    /** Difficulty for a stored spell id (unregistered ids read as 1.0). */
    private static float difficultyOf(String spellId) {
        return SpellRegistry.get(spellId).map(Spell::difficulty).orElse(1.0f);
    }

    private static Component spellDisplayName(Spell spell) {
        return Component.translatable(spell.nameKey());
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(value ? "wizardreal.cmd.common.yes" : "wizardreal.cmd.common.no");
    }

    private static Component joinOrNone(List<String> values) {
        return values.isEmpty()
                ? Component.translatable("wizardreal.cmd.common.none")
                : Component.literal(String.join(", ", values));
    }

    private static String formatMana(float value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0f);
    }

    /** D-D2 command semantics: bare amount = +/- percentage points (default
     * +10/-10), "N%" = set absolute mastery. Learning is the single source of
     * truth; known is derived. */
    private static int learn(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String amount)
            throws CommandSyntaxException {
        String spellId = spellId(ctx, "spell");
        Spell spell = requireSpell(ctx.getSource(), spellId);
        if (spell == null) return 0;
        PlayerMagicState state = PlayerMagicState.get(target.getServer());
        if (!applyLearningDelta(ctx, state, target, spell, amount, +1)) return 0;
        state.save();
        SpellCatalogService.publish(target);
        float t = state.learningPercent(target.getUUID(), spellId, spell.difficulty());
        ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.learning_set",
                spellDisplayName(spell), target.getName(),
                String.format(Locale.ROOT, "%.0f", t)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int unlearn(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String amount)
            throws CommandSyntaxException {
        String spellId = spellId(ctx, "spell");
        Spell spell = requireSpell(ctx.getSource(), spellId);
        if (spell == null) return 0;
        PlayerMagicState state = PlayerMagicState.get(target.getServer());
        if (!applyLearningDelta(ctx, state, target, spell, amount, -1)) return 0;
        state.save();
        SpellCatalogService.publish(target);
        float t = state.learningPercent(target.getUUID(), spellId, spell.difficulty());
        ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.learning_set",
                spellDisplayName(spell), target.getName(),
                String.format(Locale.ROOT, "%.0f", t)), true);
        return Command.SINGLE_SUCCESS;
    }

    /** @return false when the amount string is malformed (feedback sent). */
    private static boolean applyLearningDelta(CommandContext<CommandSourceStack> ctx, PlayerMagicState state,
                                              ServerPlayer target, Spell spell, String amount, int sign) {
        float difficulty = spell.difficulty();
        try {
            if (amount == null) {
                state.addLearningPercent(target.getUUID(), spell.id(), sign * 10f, difficulty);
            } else if (amount.endsWith("%")) {
                float percent = Float.parseFloat(amount.substring(0, amount.length() - 1).trim());
                state.setLearningPercent(target.getUUID(), spell.id(), percent, difficulty);
            } else {
                state.addLearningPercent(target.getUUID(), spell.id(), sign * Float.parseFloat(amount.trim()), difficulty);
            }
            return true;
        } catch (NumberFormatException e) {
            ctx.getSource().sendFailure(Component.translatable("wizardreal.cmd.invalid_amount", amount));
            return false;
        }
    }

    private static int cast(CommandContext<CommandSourceStack> ctx, float confidence) throws CommandSyntaxException {
        String spellId = spellId(ctx, "spell");
        ServerPlayer player = self(ctx);
        Spell spell = requireSpell(ctx.getSource(), spellId);
        if (spell == null) return 0;
        boolean ok = SpellCastHandler.castValidated(player, spellId, confidence,
                EnumSet.of(SpellCastHandler.CastFlag.SKIP_STAFF, SpellCastHandler.CastFlag.SKIP_LEARNING));
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.cast.ok",
                    spellDisplayName(spell), player.getName()), true);
        } else {
            ctx.getSource().sendFailure(Component.translatable("wizardreal.cmd.cast.fail",
                    spellDisplayName(spell), player.getName()));
        }
        return ok ? Command.SINGLE_SUCCESS : 0;
    }

    /** wr#43 practice loop entry: zero-stake rehearsal of one spell — no
     *  mana, no cooldown, no effects; strict bands, per-word HUD feedback,
     *  half-rate daily-capped mastery. */
    private static int practiceStart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String spellId = spellId(ctx, "spell");
        ServerPlayer player = self(ctx);
        Spell spell = requireSpell(ctx.getSource(), spellId);
        if (spell == null) return 0;
        ChantManager.get().startPractice(player, spell);
        ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.practice.start",
                spellDisplayName(spell)), false);
        return Command.SINGLE_SUCCESS;
    }

    /** `/wrpractice` bare / `stop`: leave practice (no penalty). */
    private static int practiceStop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ChantManager.get().stopPractice(self(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private static int listSpells(CommandContext<CommandSourceStack> ctx, String filter) {
        ServerPlayer viewer = ctx.getSource().getEntity() instanceof ServerPlayer player ? player : null;
        PlayerMagicState state = viewer != null ? PlayerMagicState.get(viewer.getServer()) : null;
        UUID viewerId = viewer != null ? viewer.getUUID() : null;
        String needle = filter.toLowerCase(Locale.ROOT);
        List<Spell> matches = new ArrayList<>();
        for (Spell spell : SpellRegistry.all()) {
            if (needle.isEmpty() || spell.id().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(spell);
            }
        }
        if (matches.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("wizardreal.cmd.spells.none", filter));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.spells.header", matches.size()), false);
        float knownThreshold = viewer == null ? LearningCurve.DEFAULT_KNOWN_THRESHOLD
                : WizardRealConfig.loadCached(viewer.getServer().getServerDirectory().toPath())
                        .learning().knownThreshold();
        for (Spell spell : matches) {
            Component knownText = state == null
                    ? Component.translatable("wizardreal.cmd.common.na")
                    : yesNo(LearningCurve.known(
                            state.learningPercent(viewerId, spell.id(), spell.difficulty()), knownThreshold));
            ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.spells.entry",
                    spellDisplayName(spell),
                    spell.id(),
                    spell.manaCost(),
                    formatSeconds(spell.cooldownTicks()),
                    yesNo(spell.requiresLearning()),
                    knownText), false);
        }
        return matches.size();
    }

    private static int spellInfo(CommandContext<CommandSourceStack> ctx) {
        String spellId = spellId(ctx, "spell");
        Spell spell = requireSpell(ctx.getSource(), spellId);
        if (spell == null) return 0;
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.header",
                spellDisplayName(spell), spell.id()), false);
        source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.schools", schoolsText(spell)), false);
        source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.origin",
                Component.translatable("origin." + spell.origin().replace(':', '.'))), false);
        source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.mana",
                spell.manaCost(), formatSeconds(spell.cooldownTicks())), false);
        source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.learning",
                yesNo(spell.requiresLearning())), false);
        ServerPlayer viewer = ctx.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        Component masteryText = viewer == null
                ? Component.translatable("wizardreal.cmd.common.na")
                : Component.literal(String.format(Locale.ROOT, "%.0f%%",
                        PlayerMagicState.get(viewer.getServer())
                                .learningPercent(viewer.getUUID(), spellId, spell.difficulty())));
        source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.difficulty",
                String.format(Locale.ROOT, "%.1f", spell.difficulty()), masteryText), false);
        float threshold = spell.threshold();
        Component thresholdText = threshold < 0
                ? Component.translatable("wizardreal.cmd.common.default")
                : Component.literal(String.format(Locale.ROOT, "%.2f", threshold));
        source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.threshold", thresholdText), false);
        Pronunciation pronunciation = spell.pronunciation();
        Component triggerText = pronunciation.aliases().isEmpty()
                ? Component.translatable("wizardreal.cmd.common.none")
                : Component.literal(String.join(", ", pronunciation.aliases()));
        source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.trigger", triggerText), false);
        List<Chant> chants = spell.chants();
        if (chants.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.chants.none"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.chants", chants.size()), false);
            for (int v = 0; v < chants.size(); v++) {
                List<ChantLine> lines = chants.get(v).lines();
                for (int i = 0; i < lines.size(); i++) {
                    ChantLine line = lines.get(i);
                    final int variant = v + 1;
                    final int index = i + 1;
                    source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.chantline",
                            variant, index, Component.literal(line.displayText())), false);
                }
            }
        }
        List<SpellEffect> effects = spell instanceof DataSpell data ? data.effects() : List.of();
        if (effects.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.effects.none"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.effects", effects.size()), false);
            for (SpellEffect effect : effects) {
                source.sendSuccess(() -> Component.translatable("wizardreal.cmd.spellinfo.effect",
                        effect.effectId().toString()), false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static Component schoolsText(Spell spell) {
        List<String> parts = new ArrayList<>();
        for (School school : spell.schools()) {
            parts.add(Component.translatable("school." + school.name().toLowerCase(Locale.ROOT)).getString());
        }
        return Component.literal(String.join(", ", parts));
    }

    private static int known(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        PlayerMagicState state = PlayerMagicState.get(target.getServer());
        float knownThreshold = WizardRealConfig.loadCached(target.getServer().getServerDirectory().toPath())
                .learning().knownThreshold();
        List<String> ids = new ArrayList<>();
        for (Spell spell : SpellRegistry.all()) {
            if (LearningCurve.known(state.learningPercent(target.getUUID(), spell.id(), spell.difficulty()),
                    knownThreshold)) {
                ids.add(spell.id());
            }
        }
        Collections.sort(ids);
        ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.known.header",
                target.getName(), ids.size(), joinOrNone(ids)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int manaGet(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        PlayerMagicState state = PlayerMagicState.get(target.getServer());
        UUID uuid = target.getUUID();
        ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.mana.get",
                target.getName(), formatMana(state.getMana(uuid)), formatMana(state.getMaxMana(uuid))), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int manaSet(CommandContext<CommandSourceStack> ctx, float amount, ServerPlayer target) {
        PlayerMagicState state = PlayerMagicState.get(target.getServer());
        UUID uuid = target.getUUID();
        state.setMana(uuid, amount);
        MagicSyncHandler.send(target, state);
        ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.mana.set",
                target.getName(), formatMana(state.getMana(uuid))), true);
        return Command.SINGLE_SUCCESS;
    }

        /** wr#49: permanent max-mana bonus (advancement reward path, capped at
     *  {@code PlayerMagicState.MAX_MANA_CAP}). Advancement reward functions
     *  run at permission 2 as the player, so this is callable from datapack. */
    private static int manaBonus(CommandContext<CommandSourceStack> ctx, float amount,
                                 ServerPlayer target) {
        PlayerMagicState state = PlayerMagicState.get(target.getServer());
        float granted = state.grantMaxMana(target.getUUID(), amount);
        float cap = state.getMaxMana(target.getUUID());
        if (granted > 0f) {
            state.save();
            MagicSyncHandler.send(target, state);
            ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.mana.bonus.ok",
                    String.format(java.util.Locale.ROOT, "%.0f", granted),
                    String.format(java.util.Locale.ROOT, "%.0f", cap)), true);
        } else {
            ctx.getSource().sendFailure(Component.translatable("wizardreal.cmd.mana.bonus.cap",
                    String.format(java.util.Locale.ROOT, "%.0f", cap)));
        }
        return granted > 0f ? Command.SINGLE_SUCCESS : 0;
    }

private static int manaReset(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        PlayerMagicState state = PlayerMagicState.get(target.getServer());
        UUID uuid = target.getUUID();
        state.setMana(uuid, state.getMaxMana(uuid));
        MagicSyncHandler.send(target, state);
        ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.mana.reset", target.getName()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int cooldownClear(CommandContext<CommandSourceStack> ctx, String spellId, ServerPlayer target) {
        PlayerMagicState state = PlayerMagicState.get(target.getServer());
        if (spellId == null) {
            state.clearAllCooldowns(target.getUUID());
            ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.cooldown.cleared.all",
                    target.getName()), true);
        } else {
            state.clearCooldown(target.getUUID(), spellId);
            ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.cooldown.cleared",
                    spellId, target.getName()), true);
        }
        MagicSyncHandler.send(target, state);
        return Command.SINGLE_SUCCESS;
    }

    private static int cooldownSet(CommandContext<CommandSourceStack> ctx, String spellId, float seconds,
                                   ServerPlayer target) {
        PlayerMagicState state = PlayerMagicState.get(target.getServer());
        long now = target.level().getGameTime();
        state.setCooldown(target.getUUID(), spellId, now + (long) (seconds * 20.0f));
        MagicSyncHandler.send(target, state);
        ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.cooldown.set",
                String.format(Locale.ROOT, "%.1f", seconds), spellId, target.getName()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int sync(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        PlayerMagicState state = PlayerMagicState.get(target.getServer());
        MagicSyncHandler.send(target, state);
        SpellCatalogService.publish(target);
        ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.sync", target.getName()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int stateSave(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        PlayerMagicState.get(server).save();
        ctx.getSource().sendSuccess(() -> Component.translatable("wizardreal.cmd.state.saved"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int stateDump(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        PlayerMagicState state = PlayerMagicState.get(target.getServer());
        UUID uuid = target.getUUID();
        long now = target.level().getGameTime();
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("wizardreal.cmd.state.dump.player", target.getName()), false);
        source.sendSuccess(() -> Component.translatable("wizardreal.cmd.state.dump.mana",
                formatMana(state.getMana(uuid)), formatMana(state.getMaxMana(uuid))), false);
        // Learning snapshot (D-D2): stored points only; absent spells sit at the 10% baseline.
        List<String> learning = new ArrayList<>();
        for (Map.Entry<String, Float> e : state.learningSnapshot(uuid).entrySet()) {
            learning.add(e.getKey() + "=" + String.format(Locale.ROOT, "%.0f%%",
                    e.getValue() / Math.max(0.01f, difficultyOf(e.getKey()))));
        }
        Collections.sort(learning);
        source.sendSuccess(() -> Component.translatable("wizardreal.cmd.state.dump.known",
                joinOrNone(learning)), false);
        List<String> cooldowns = new ArrayList<>();
        for (Map.Entry<String, Long> entry : state.getCooldowns(uuid).entrySet()) {
            long remaining = entry.getValue() - now;
            if (remaining > 0) {
                cooldowns.add(entry.getKey() + "=" + ((remaining + 19) / 20) + "s");
            }
        }
        Collections.sort(cooldowns);
        source.sendSuccess(() -> Component.translatable("wizardreal.cmd.state.dump.cooldowns", joinOrNone(cooldowns)), false);
        return Command.SINGLE_SUCCESS;
    }
}
