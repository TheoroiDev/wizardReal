package com.theo.wizardreal.compat;

import java.util.Locale;
import java.util.function.Predicate;

/** Loader-agnostic soft-dependency detection. */
public final class ModDetection {
    private static Predicate<String> CHECKER = id -> false;

    private ModDetection() {}

    public static void init(Predicate<String> checker) {
        CHECKER = checker == null ? id -> false : checker;
    }

    public static boolean isLoaded(String modId) {
        try { return CHECKER.test(modId.toLowerCase(Locale.ROOT)); }
        catch (Throwable t) { return false; }
    }

    public static boolean curios() { return isLoaded("curios"); }
    public static boolean trinkets() { return isLoaded("trinkets"); }
    public static boolean patchouli() { return isLoaded("patchouli"); }
    public static boolean voiceChat() { return isLoaded("voicechat"); }
}
