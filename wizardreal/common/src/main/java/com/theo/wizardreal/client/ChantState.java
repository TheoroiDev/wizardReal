package com.theo.wizardreal.client;

import java.util.List;

/**
 * Client-side mirror of the current ritual chant, populated from the
 * {@code wizardreal:chant} S2C packets and read by {@link ChantHud}.
 */
public final class ChantState {
    public String spellId;
    public List<List<String>> variantLines; // display keys per chant variant
    public int variant = -1;
    public int lineIndex;
    public boolean error;
    public long startedMs;
    public long endedMs;
    public boolean active;

    /** Practice engines (wizardReal#43) flag on START: zero stakes, strict
     *  bands, per-word HUD highlighting. */
    public boolean practice;

    /** wr#43 逐词对齐: the last practice utterance, aligned client-side
     *  against the localized current line by the HUD. */
    public String practiceHeard;

    // Idle-recognition notice (issue #41): kind + optional spell name lang
    // key, transient — the HUD fades it after NOTICE_TTL_MS.
    public static final long NOTICE_TTL_MS = 3500;
    public String noticeKind;
    public String noticeNameKey;
    public long noticeMs;

    void onStart(String spellId, List<List<String>> variantLines, boolean practice) {
        this.spellId = spellId;
        this.variantLines = variantLines;
        // Display variant 0's incantation right away so the player sees the lines
        // to chant (the variant is locked server-side after the first matched line,
        // which may switch the displayed variant).
        this.variant = variantLines.isEmpty() ? -1 : 0;
        this.lineIndex = 0;
        this.error = false;
        this.startedMs = System.currentTimeMillis();
        this.endedMs = 0;
        this.active = true;
        this.practice = practice;
        this.practiceHeard = null;
    }

    void onProgress(int variant, int lineIndex, boolean error) {
        this.variant = variant;
        this.lineIndex = lineIndex;
        this.error = error;
        if (!error) this.practiceHeard = null;
        if (variant >= 0 && this.startedMs == 0) this.startedMs = System.currentTimeMillis();
    }

    void onEnd(boolean success) {
        this.active = false;
        this.endedMs = System.currentTimeMillis();
        this.practiceHeard = null;
    }

    void onNotice(String kind, String nameKey) {
        this.noticeKind = kind;
        this.noticeNameKey = nameKey;
        this.noticeMs = System.currentTimeMillis();
    }

    void onPracticeWords(String heard) {
        this.practiceHeard = heard;
    }
}
