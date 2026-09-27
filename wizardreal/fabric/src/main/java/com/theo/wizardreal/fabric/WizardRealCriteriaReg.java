package com.theo.wizardreal.fabric;

import com.theo.wizardreal.advancement.WizardRealCriteria;
import net.minecraft.advancements.CriteriaTriggers;

/**
 * Fabric-side registration of the custom advancement triggers (wizardReal#49).
 * 1.20.1 keeps criterion triggers in vanilla's static {@code CriteriaTriggers}
 * map — no dedicated registry.
 */
public final class WizardRealCriteriaReg {

    private WizardRealCriteriaReg() {}

    public static void register() {
        CriteriaTriggers.register(WizardRealCriteria.VOICE_CAST);
        CriteriaTriggers.register(WizardRealCriteria.MASTERY);
    }
}
