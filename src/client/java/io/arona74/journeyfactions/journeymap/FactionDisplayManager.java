package io.arona74.journeyfactions.journeymap;

import io.arona74.journeyfactions.JourneyFactions;

/**
 * Manages the display state of faction territories
 */
public class FactionDisplayManager {
    
    private static boolean factionDisplayEnabled = true; // Default to enabled
    private static FactionOverlayManager overlayManager;
    
    /**
     * Initialize the display manager with the overlay manager
     */
    public static void initialize(FactionOverlayManager manager) {
        overlayManager = manager;
        // JourneyFactions.LOGGER.info("FactionDisplayManager initialized");
    }
    
    /**
     * Check if faction display is currently enabled
     */
    public static boolean isFactionDisplayEnabled() {
        return factionDisplayEnabled;
    }
    
    /**
     * Toggle faction display on/off
     */
    public static void toggleFactionDisplay() {
        factionDisplayEnabled = !factionDisplayEnabled;
        updateAllOverlayVisibility();
        
        // JourneyFactions.LOGGER.info("Faction display toggled to: {}", factionDisplayEnabled ? "ENABLED" : "DISABLED");
    }
    
    /**
     * Set faction display state
     */
    public static void setFactionDisplayEnabled(boolean enabled) {
        if (factionDisplayEnabled != enabled) {
            factionDisplayEnabled = enabled;
            updateAllOverlayVisibility();
            
            // JourneyFactions.LOGGER.info("Faction display set to: {}", enabled ? "ENABLED" : "DISABLED");
        }
    }
    
    /**
     * Update visibility of all faction overlays
     */
    private static void updateAllOverlayVisibility() {
        if (overlayManager != null) {
            overlayManager.updateAllOverlayVisibility(factionDisplayEnabled);
        }
    }
    
    /**
     * Get display status as string
     */
    public static String getDisplayStatusText() {
        return factionDisplayEnabled ? "Hide Factions" : "Show Factions";
    }
}