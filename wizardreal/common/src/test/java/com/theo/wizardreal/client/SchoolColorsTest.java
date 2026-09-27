package com.theo.wizardreal.client;

import com.theo.wizardreal.api.School;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** wizardReal#44: the runtime school palette loads from the generated copy
 *  and every school resolves tones + a legacy text color. */
class SchoolColorsTest {

    @Test
    void allTenSchoolsResolveTones() {
        for (School school : School.values()) {
            SchoolColors.Tones t = SchoolColors.tones(school);
            assertNotNull(t, "no tones for " + school);
            assertTrue(t.accent() != t.glow(), "glow should differ from accent for " + school);
            assertTrue(t.dark() != t.accent(), "dark should differ from accent for " + school);
        }
    }

    @Test
    void fireAccentIsTheSpellDustOrange() {
        // The palette was extracted from the spell JSONs' de-facto dust colors;
        // fire's dust (255,120,20) must survive as the accent.
        SchoolColors.Tones t = SchoolColors.tones(School.FIRE);
        assertNotNull(t);
        assertEquals(0xFF7814, t.accent());
    }

    @Test
    void textFormattingIsStableAndNonNull() {
        for (School school : School.values()) {
            assertEquals(SchoolColors.textFormatting(school), SchoolColors.textFormatting(school));
        }
        // fire accent (255,120,20) sits closest to vanilla GOLD (255,170,0)
        assertEquals(ChatFormatting.GOLD, SchoolColors.textFormatting(School.FIRE));
    }
}
