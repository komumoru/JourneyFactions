package io.arona74.journeyfactions.debug;

import io.arona74.journeyfactions.JourneyFactions;
import io.arona74.journeyfactions.data.ClientFaction;
import io.arona74.journeyfactions.journeymap.FactionDisplayManager;
import io.arona74.journeyfactions.journeymap.FactionToggleButton;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Debug commands for managing faction data
 */
public class FactionDebugCommands {
    
    /**
     * Clear all test/debug faction data
     */
    public static void clearTestData() {
        try {
            JourneyFactions.LOGGER.info("=== CLEARING TEST DATA ===");
            
            Collection<ClientFaction> allFactions = JourneyFactions.getFactionManager().getAllFactions();
            Collection<ClientFaction> testFactionsToRemove = new ArrayList<>();
            
            // Find test factions (those with test IDs or names)
            for (ClientFaction faction : allFactions) {
                String id = faction.getId().toLowerCase();
                String name = faction.getName().toLowerCase();
                
                if (id.contains("test") || id.contains("debug") || 
                    name.contains("test") || name.contains("debug") ||
                    id.startsWith("player-test") || name.contains("redfaction") || 
                    name.contains("bluefaction") || name.equals("debug_")) {
                    
                    testFactionsToRemove.add(faction);
                }
            }
            
            // Remove test factions
            for (ClientFaction testFaction : testFactionsToRemove) {
                JourneyFactions.getFactionManager().removeFaction(testFaction.getId());
                JourneyFactions.LOGGER.info("Removed test faction: {} ({})", testFaction.getName(), testFaction.getId());
            }
            
            JourneyFactions.LOGGER.info("Cleared {} test factions", testFactionsToRemove.size());
            
            // Also clear any system factions that shouldn't have claimed chunks
            clearSystemFactionChunks();
            
            JourneyFactions.LOGGER.info("=== TEST DATA CLEARED ===");
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error clearing test data", e);
        }
    }
    
    /**
     * Clear chunks from system factions (wilderness, safezone, warzone) if they have any
     */
    private static void clearSystemFactionChunks() {
        String[] systemFactionIds = {"wilderness", "safezone", "warzone"};
        
        for (String systemId : systemFactionIds) {
            ClientFaction systemFaction = JourneyFactions.getFactionManager().getFaction(systemId);
            if (systemFaction != null && !systemFaction.getClaimedChunks().isEmpty()) {
                systemFaction.setClaimedChunks(new java.util.HashSet<>());
                JourneyFactions.LOGGER.info("Cleared chunks from system faction: {}", systemFaction.getName());
            }
        }
    }
    
    /**
     * Print current faction status
     */
    public static void printFactionStatus() {
        JourneyFactions.LOGGER.info("=== FACTION STATUS ===");
        
        Collection<ClientFaction> factions = JourneyFactions.getFactionManager().getAllFactions();
        JourneyFactions.LOGGER.info("Total factions: {}", factions.size());
        
        int playerFactions = 0;
        int totalChunks = 0;
        
        for (ClientFaction faction : factions) {
            if (faction.getType() == ClientFaction.FactionType.PLAYER) {
                playerFactions++;
            }
            totalChunks += faction.getClaimedChunks().size();
            
            if (!faction.getClaimedChunks().isEmpty()) {
                JourneyFactions.LOGGER.info("- {} ({}): {} chunks", 
                    faction.getName(), faction.getType(), faction.getClaimedChunks().size());
            }
        }
        
        JourneyFactions.LOGGER.info("Player factions: {}", playerFactions);
        JourneyFactions.LOGGER.info("Total claimed chunks: {}", totalChunks);
        JourneyFactions.LOGGER.info("Display enabled: {}", FactionDisplayManager.isFactionDisplayEnabled());
        JourneyFactions.LOGGER.info("=== END STATUS ===");
    }
    
    /**
     * Toggle faction display
     */
    public static void toggleDisplay() {
        FactionDisplayManager.toggleFactionDisplay();
        JourneyFactions.LOGGER.info("Faction display toggled to: {}", 
            FactionDisplayManager.isFactionDisplayEnabled() ? "ENABLED" : "DISABLED");
    }
    
    /**
     * Show all available debug commands
     */
    public static void showHelp() {
        JourneyFactions.LOGGER.info("=== JOURNEYFACTIONS DEBUG COMMANDS ===");
        JourneyFactions.LOGGER.info("FactionDebugCommands.clearTestData() - Remove all test factions");
        JourneyFactions.LOGGER.info("FactionDebugCommands.printFactionStatus() - Show current faction data");
        JourneyFactions.LOGGER.info("FactionDebugCommands.toggleDisplay() - Toggle faction display on/off");
        JourneyFactions.LOGGER.info("FactionDebugCommands.openControlsGUI() - Open faction controls screen");
        JourneyFactions.LOGGER.info("FactionDebugCommands.showHelp() - Show this help");
        JourneyFactions.LOGGER.info("FactionToggleButton.manualToggle() - Manual toggle via button system");
        JourneyFactions.LOGGER.info("=== GUI Controls ===");
        JourneyFactions.LOGGER.info("Press 'G' key - Quick toggle faction display");
        JourneyFactions.LOGGER.info("Right-click JourneyMap - Access faction controls (if integrated)");
        JourneyFactions.LOGGER.info("=== Commands can be called from debug console or mod code ===");
    }
    
    /**
     * Open the faction controls GUI screen
     */
    public static void openControlsGUI() {
        try {
            io.arona74.journeyfactions.journeymap.JourneyMapContextIntegration.openFactionControlsScreen();
            JourneyFactions.LOGGER.info("Opened faction controls GUI");
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error opening controls GUI: {}", e.getMessage());
        }
    }
    
    /**
     * Run a complete debug session
     */
    public static void runFullDebug() {
        JourneyFactions.LOGGER.info("=== RUNNING FULL DEBUG SESSION ===");
        
        printFactionStatus();
        
        JourneyFactions.LOGGER.info("--- Testing manual toggle ---");
        toggleDisplay();
        
        JourneyFactions.LOGGER.info("=== DEBUG SESSION COMPLETE ===");
        showHelp();
    }
}