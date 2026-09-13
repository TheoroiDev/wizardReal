package com.theo.wizardreal.server;

import com.theo.wizardreal.api.SpellStage;

import java.util.List;

/**
 * Chant-stage ladder resolution (magic_eco 03): maps a cast's completed chant
 * lines and the caster's mastery percent onto a stage index. Pure JVM logic —
 * unit-testable; consumed by {@link ChantManager} before handing the cast to
 * {@link SpellCastHandler}.
 *
 * <p>Dual gate per stage (2026-09-06 user ruling: 可念行数与熟练度绑定): the stage
 * unlocks when the cast has completed at least {@code afterLines} lines (the L1
 * entry line counts as line 1) AND the caster's mastery {@code t} (learning
 * percent 0-100) meets the stage's {@code masteryThreshold}. Under-mastery
 * casts resolve DOWN to the highest unlocked stage — the lines are still
 * spoken (and teach), they just cannot exceed the caster's growth.
 */
public final class StageResolver {

    private StageResolver() {}

    /**
     * @param stages   the spell's {@code chant_stages}, ascending by after_lines
     * @param completedLines lines completed by this cast (0 for skip-cast)
     * @param masteryT caster mastery percent (0-100)
     * @return 0 = base tier, N = stages[N-1]
     */
    public static int resolve(List<SpellStage> stages, int completedLines, float masteryT) {
        int best = 0;
        for (int i = 0; i < stages.size(); i++) {
            SpellStage stage = stages.get(i);
            if (completedLines >= stage.afterLines() && masteryT >= stage.masteryThreshold()) {
                best = i + 1;
            }
        }
        return best;
    }

    /** Mana cost for the resolved tier (stage override or the spell's base). */
    public static int manaCost(List<SpellStage> stages, int stageIndex, int baseCost) {
        if (stageIndex >= 1 && stageIndex <= stages.size()) {
            Integer override = stages.get(stageIndex - 1).manaCost();
            if (override != null) return override;
        }
        return baseCost;
    }

    /** Cooldown for the resolved tier (stage override or the spell's base). */
    public static int cooldownTicks(List<SpellStage> stages, int stageIndex, int baseTicks) {
        if (stageIndex >= 1 && stageIndex <= stages.size()) {
            Integer override = stages.get(stageIndex - 1).cooldownTicks();
            if (override != null) return override;
        }
        return baseTicks;
    }
}
