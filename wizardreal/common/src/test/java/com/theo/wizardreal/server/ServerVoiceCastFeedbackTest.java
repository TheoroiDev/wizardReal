package com.theo.wizardreal.server;

import com.theo.voicecast.api.Alternative;
import com.theo.voicecast.api.Decision;
import com.theo.wizardreal.net.ChantNetwork;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Issue #41 失败路径三修 — the pure seams: idle-rejection notice kind
 *  selection and the 空转咏唱 stage walk. */
class ServerVoiceCastFeedbackTest {

    private static final Alternative TOP = new Alternative("wizardreal:aegis", "wizardreal:aegis", 0.7f);

    @Test
    void rejectedAlwaysDissipates() {
        assertEquals(ChantNetwork.NOTICE_DISSIPATE,
                ServerVoiceCast.noticeKind(Decision.REJECTED, List.of(TOP)));
        assertEquals(ChantNetwork.NOTICE_DISSIPATE,
                ServerVoiceCast.noticeKind(Decision.REJECTED, List.of()));
    }

    @Test
    void runnerUpCandidatesWhisper() {
        // AMBIGUOUS / NONE with candidates: the top runner-up names the spell.
        assertEquals(ChantNetwork.NOTICE_WHISPER,
                ServerVoiceCast.noticeKind(Decision.AMBIGUOUS, List.of(TOP)));
        assertEquals(ChantNetwork.NOTICE_WHISPER,
                ServerVoiceCast.noticeKind(null, List.of(TOP)));
    }

    @Test
    void pureNoiseStaysSilent() {
        // Decisions that would never reach the idle path, and a decision-less
        // result with no candidates: no notice either way.
        assertNull(ServerVoiceCast.noticeKind(Decision.EXACT, List.of()));
        assertNull(ServerVoiceCast.noticeKind(Decision.NEAR, List.of()));
        assertNull(ServerVoiceCast.noticeKind(null, null));
    }

    @Test
    void stageWalkPrefersTheHighestAffordableTier() {
        float[] stages = {10f, 25f, 60f};
        assertEquals(2, SpellCastHandler.cheapestAffordableStage(60f, stages));
        assertEquals(1, SpellCastHandler.cheapestAffordableStage(59f, stages));
        assertEquals(1, SpellCastHandler.cheapestAffordableStage(25f, stages));
        assertEquals(0, SpellCastHandler.cheapestAffordableStage(24.9f, stages));
    }

    @Test
    void emptyBarFizzlesInsteadOfCastingFree() {
        assertEquals(-1, SpellCastHandler.cheapestAffordableStage(9.9f, new float[] {10f}));
        assertEquals(-1, SpellCastHandler.cheapestAffordableStage(0f, new float[] {10f, 25f}));
    }
}
