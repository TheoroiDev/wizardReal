package com.theo.wizardreal.api;

import com.theo.voicecast.api.Pronunciation;

/**
 * A single line of a long incantation: the localized text shown on the client
 * HUD ({@code displayKey}) and the pronunciation templates used to match the
 * spoken line server-side.
 */
public record ChantLine(String displayKey, Pronunciation pronunciation) {
}
