package io.arona74.journeyfactions;

import io.arona74.journeyfactions.data.ClientFactionManager;
import io.arona74.journeyfactions.network.ClientNetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JourneyFactions implements ClientModInitializer {
	public static final String MOD_ID = "journeyfactions";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ClientFactionManager factionManager;
	private static boolean journeyMapLoaded = false;

	@Override
	public void onInitializeClient() {
		LOGGER.info("JourneyFactions client mod initializing...");

		// Initialize client-side faction data manager
		factionManager = new ClientFactionManager();

		// Initialize network handling
		ClientNetworkHandler.initialize();

		// Check if JourneyMap is loaded
		journeyMapLoaded = FabricLoader.getInstance().isModLoaded("journeymap");
		if (journeyMapLoaded) {
			LOGGER.info("JourneyMap detected - map integration will be available");
		} else {
			LOGGER.warn("JourneyMap not detected - mod will function in minimal mode");
		}

		// Handle client lifecycle events
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			// Clean up resources when client shuts down
			if (factionManager != null) {
				factionManager.cleanup();
			}
		});

		LOGGER.info("JourneyFactions client mod initialized successfully");
	}

	public static ClientFactionManager getFactionManager() {
		return factionManager;
	}

	public static boolean isJourneyMapLoaded() {
		return journeyMapLoaded;
	}
}