package com.theo.wizardreal;

import com.theo.voicecast.api.RecognizerRegistry;
import com.theo.wizardreal.effect.BuiltinEffects;
import com.theo.wizardreal.item.WizardRealItems;
import com.theo.wizardreal.net.ChantNetwork;
import com.theo.wizardreal.server.LootTableModifier;
import com.theo.wizardreal.server.MagicSyncHandler;
import com.theo.wizardreal.server.ManaManager;
import com.theo.wizardreal.server.PlayerMagicState;
import com.theo.wizardreal.server.ServerVoiceCast;
import com.theo.wizardreal.server.SpellCatalogService;
import com.theo.wizardreal.server.SpellDataLoader;
import com.theo.wizardreal.server.SpellKillDrops;
import com.theo.wizardreal.server.WizardRealCommands;
import com.theo.wizardreal.spell.Spells;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Be a Real Wizard - voice-driven magic mod.
 *
 * <p>Gameplay content lives in common; the loader modules bootstrap platform
 * bindings (registries, networking, events) and call {@link #init()}.
 */
public final class WizardReal {
    public static final String MOD_ID = "wizardreal";
    public static final Logger LOGGER = LoggerFactory.getLogger("WizardReal");

    private static boolean initialized;

    private WizardReal() {}

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        LOGGER.info("Be a Real Wizard initializing (voice engines: {})", RecognizerRegistry.ids());
        BuiltinEffects.register();
        Spells.register();
        ServerVoiceCast.init();
        ChantNetwork.registerServerReceiver();
        SpellCatalogService.register();
        PlayerMagicState.registerHooks();
        ManaManager.get().register();
        LootTableModifier.init();
        SpellKillDrops.init();
        MagicSyncHandler.register();
        WizardRealCommands.register();
        // Datapack spell load runs between SERVER_STARTING and SERVER_STARTED:
        // the server must be captured before the reload listeners fire.
        LifecycleEvent.SERVER_STARTING.register(SpellDataLoader::setServer);
        LifecycleEvent.SERVER_STARTED.register(server -> {
            SpellDataLoader.setServer(server);
            ServerVoiceCast.pushVocabulary();
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> SpellDataLoader.setServer(null));
    }
}
