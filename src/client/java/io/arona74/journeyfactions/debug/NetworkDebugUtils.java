package io.arona74.journeyfactions.debug;

import io.arona74.journeyfactions.JourneyFactions;
import io.arona74.journeyfactions.data.ClientFaction;
import io.arona74.journeyfactions.network.ClientNetworkHandler;
import net.minecraft.util.math.ChunkPos;

import java.awt.Color;

/**
 * Debug utilities for testing network functionality
 */
public class NetworkDebugUtils {
    
    /**
     * Test network functionality by requesting data from server
     */
    public static void testNetworkConnection() {
        JourneyFactions.LOGGER.info("=== NETWORK DEBUG TEST ===");
        
        try {
            JourneyFactions.LOGGER.info("Current faction count: {}", 
                JourneyFactions.getFactionManager().getAllFactions().size());
            
            JourneyFactions.LOGGER.info("Requesting faction data from server...");
            ClientNetworkHandler.requestFactionData();
            
            // Schedule a check in 5 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    checkReceivedData();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Network test failed", e);
        }
    }
    
    private static void checkReceivedData() {
        JourneyFactions.LOGGER.info("=== NETWORK TEST RESULTS ===");
        
        int factionCount = JourneyFactions.getFactionManager().getAllFactions().size();
        JourneyFactions.LOGGER.info("Total factions received: {}", factionCount);
        
        for (ClientFaction faction : JourneyFactions.getFactionManager().getAllFactions()) {
            JourneyFactions.LOGGER.info("- {} ({}): {} chunks, type: {}", 
                faction.getName(), faction.getId(), 
                faction.getClaimedChunks().size(), faction.getType());
        }
        
        if (factionCount == 0) {
            JourneyFactions.LOGGER.warn("No factions received - check server integration!");
        } else {
            JourneyFactions.LOGGER.info("Network integration appears to be working!");
        }
    }
    
    /**
     * Print current faction data for debugging
     */
    public static void printFactionData() {
        JourneyFactions.LOGGER.info("=== CURRENT FACTION DATA ===");
        
        var factionManager = JourneyFactions.getFactionManager();
        
        JourneyFactions.LOGGER.info("Total factions: {}", factionManager.getAllFactions().size());
        JourneyFactions.LOGGER.info("Player factions: {}", factionManager.getPlayerFactions().size());
        JourneyFactions.LOGGER.info("Total claimed chunks: {}", factionManager.getTotalClaimedChunks());
        
        for (ClientFaction faction : factionManager.getAllFactions()) {
            JourneyFactions.LOGGER.info("Faction: {} ({})", faction.getName(), faction.getId());
            JourneyFactions.LOGGER.info("  Type: {}", faction.getType());
            JourneyFactions.LOGGER.info("  Display: {}", faction.getDisplayName());
            JourneyFactions.LOGGER.info("  Color: {}", faction.getColor());
            JourneyFactions.LOGGER.info("  Chunks: {} claimed", faction.getClaimedChunks().size());
            
            // Print first few chunks
            int count = 0;
            for (ChunkPos chunk : faction.getClaimedChunks()) {
                if (count < 5) {
                    JourneyFactions.LOGGER.info("    - ({}, {})", chunk.x, chunk.z);
                    count++;
                } else if (count == 5) {
                    JourneyFactions.LOGGER.info("    - ... and {} more", 
                        faction.getClaimedChunks().size() - 5);
                    break;
                }
            }
        }
    }
    
    /**
     * Create some test data for offline testing
     */
    public static void createOfflineTestData() {
        JourneyFactions.LOGGER.info("Creating offline test data...");
        
        var factionManager = JourneyFactions.getFactionManager();
        
        // Clear existing data
        factionManager.clear();
        
        // Create test player faction
        ClientFaction playerFaction = new ClientFaction("player-test-123", "TestPlayerFaction");
        playerFaction.setDisplayName("§6Test Player Faction");
        playerFaction.setColor(new Color(255, 165, 0)); // Orange
        playerFaction.setType(ClientFaction.FactionType.PLAYER);
        
        // Add some chunks around spawn
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) + Math.abs(z) <= 2) { // Diamond shape
                    playerFaction.addClaimedChunk(new ChunkPos(x, z));
                }
            }
        }
        
        factionManager.addOrUpdateFaction(playerFaction);
        
        // Create a safezone
        ClientFaction safezone = new ClientFaction("safezone-spawn", "SafeZone");
        safezone.setDisplayName("§aSafe Zone");
        safezone.setColor(new Color(0, 255, 0)); // Green
        safezone.setType(ClientFaction.FactionType.SAFEZONE);
        safezone.addClaimedChunk(new ChunkPos(10, 10));
        safezone.addClaimedChunk(new ChunkPos(10, 11));
        safezone.addClaimedChunk(new ChunkPos(11, 10));
        safezone.addClaimedChunk(new ChunkPos(11, 11));
        
        factionManager.addOrUpdateFaction(safezone);
        
        // Create a warzone
        ClientFaction warzone = new ClientFaction("warzone-pvp", "WarZone");
        warzone.setDisplayName("§cWar Zone");
        warzone.setColor(new Color(255, 0, 0)); // Red
        warzone.setType(ClientFaction.FactionType.WARZONE);
        warzone.addClaimedChunk(new ChunkPos(-10, -10));
        warzone.addClaimedChunk(new ChunkPos(-10, -9));
        warzone.addClaimedChunk(new ChunkPos(-9, -10));
        warzone.addClaimedChunk(new ChunkPos(-9, -9));
        
        factionManager.addOrUpdateFaction(warzone);
        
        JourneyFactions.LOGGER.info("Created {} test factions with {} total chunks", 
            factionManager.getAllFactions().size(), 
            factionManager.getTotalClaimedChunks());
    }
}