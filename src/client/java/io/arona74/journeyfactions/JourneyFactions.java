package io.arona74.journeyfactions;

import io.arona74.journeyfactions.data.ClientFaction;
import io.arona74.journeyfactions.data.ClientFactionManager;
import io.arona74.journeyfactions.network.ClientNetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

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
			LOGGER.info("JourneyMap detected - integration will be available");
		} else {
			LOGGER.info("JourneyMap not detected - mod will function in data collection mode");
		}

		LOGGER.info("JourneyFactions client mod initialized successfully");
	}

	// Keep test faction creation as a separate method for debugging
	public static void createTestFactions() {
		LOGGER.info("Creating test factions for debugging...");
		
		try {
			// Test Faction 1 - Red at spawn
			ClientFaction testFaction1 = new ClientFaction("test1", "RedFaction");
			testFaction1.setDisplayName("Red Test Faction");
			testFaction1.setColor(new Color(255, 0, 0)); // Red
			testFaction1.setType(ClientFaction.FactionType.PLAYER);
			testFaction1.addClaimedChunk(new ChunkPos(0, 0)); // Spawn chunk
			testFaction1.addClaimedChunk(new ChunkPos(1, 0)); // Adjacent chunk
			factionManager.addOrUpdateFaction(testFaction1);
			LOGGER.info("Created Red Test Faction with chunks at (0,0) and (1,0)");

			// Test Faction 2 - Blue nearby
			ClientFaction testFaction2 = new ClientFaction("test2", "BlueFaction");
			testFaction2.setDisplayName("Blue Test Faction");
			testFaction2.setColor(new Color(0, 0, 255)); // Blue
			testFaction2.setType(ClientFaction.FactionType.PLAYER);
			testFaction2.addClaimedChunk(new ChunkPos(0, 1)); // Near spawn
			testFaction2.addClaimedChunk(new ChunkPos(1, 1)); // Adjacent
			factionManager.addOrUpdateFaction(testFaction2);
			LOGGER.info("Created Blue Test Faction with chunks at (0,1) and (1,1)");

		} catch (Exception e) {
			LOGGER.error("Failed to create test factions", e);
		}
	}

	public static ClientFactionManager getFactionManager() {
		return factionManager;
	}

	public static boolean isJourneyMapLoaded() {
		return journeyMapLoaded;
	}
}