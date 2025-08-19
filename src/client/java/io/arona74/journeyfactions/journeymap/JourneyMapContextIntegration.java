package io.arona74.journeyfactions.journeymap;

import io.arona74.journeyfactions.JourneyFactions;
import io.arona74.journeyfactions.client.gui.FactionControlsScreen;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.ModPopupMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * Integrates faction controls with JourneyMap's context menu system
 * Following MapFrontiers approach for context menu integration
 */
public class JourneyMapContextIntegration {
    
    /**
     * Initialize the context integration
     */
    public static void initialize(IClientAPI jmAPI) {
        JourneyFactions.LOGGER.info("JourneyMap context integration initialized");
    }
    
    /**
     * Create faction-related menu items for JourneyMap context menus
     * This would be called by JourneyMap when building context menus
     */
    public static void addFactionMenuItems(ModPopupMenu contextMenu) {
        try {
            if (contextMenu == null) {
                return;
            }
            
            // Add faction controls submenu
            ModPopupMenu factionSubmenu = contextMenu.createSubItemList("Faction Controls");
            
            // Add toggle action
            factionSubmenu.addMenuItem(getToggleText(), new ModPopupMenu.Action() {
                @Override
                public void doAction(BlockPos blockPos) {
                    FactionDisplayManager.toggleFactionDisplay();
                    showToggleFeedback();
                }
            });
            
            // Add open controls screen action
            factionSubmenu.addMenuItemScreen("Faction Settings", new FactionControlsScreen());
            
            // JourneyFactions.LOGGER.debug("Added faction controls to JourneyMap context menu");
            
        } catch (Exception e) {
            // JourneyFactions.LOGGER.warn("Error adding faction menu items: {}", e.getMessage());
        }
    }
    
    /**
     * Create a standalone popup menu for faction controls
     */
    public static ModPopupMenu createFactionPopupMenu() {
        // Since ModPopupMenu is an interface, we need to implement it
        // This is a simplified implementation for demonstration
        return new ModPopupMenu() {
            @Override
            public ModPopupMenu addMenuItem(String label, Action action) {
                // JourneyFactions.LOGGER.debug("Would add menu item: {}", label);
                return this;
            }
            
            @Override
            public ModPopupMenu addMenuItemScreen(String label, net.minecraft.client.gui.screen.Screen screen) {
                // JourneyFactions.LOGGER.debug("Would add screen menu item: {}", label);
                return this;
            }
            
            @Override
            public ModPopupMenu createSubItemList(String label) {
                // JourneyFactions.LOGGER.debug("Would create submenu: {}", label);
                return this;
            }
        };
    }
    
    /**
     * Open the faction controls screen directly
     */
    public static void openFactionControlsScreen() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.setScreen(new FactionControlsScreen());
                // JourneyFactions.LOGGER.info("Opened faction controls screen");
            }
        } catch (Exception e) {
            // JourneyFactions.LOGGER.error("Error opening faction controls screen: {}", e.getMessage());
        }
    }
    
    /**
     * Get dynamic toggle text
     */
    private static String getToggleText() {
        return FactionDisplayManager.isFactionDisplayEnabled() 
            ? "Hide Factions" 
            : "Show Factions";
    }
    
    /**
     * Show feedback for toggle action
     */
    private static void showToggleFeedback() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                String message = FactionDisplayManager.isFactionDisplayEnabled() 
                    ? "§eFaction territories: §aShown" 
                    : "§eFaction territories: §cHidden";
                
                client.player.sendMessage(
                    net.minecraft.text.Text.literal(message), 
                    true // Action bar
                );
            }
        } catch (Exception e) {
            // JourneyFactions.LOGGER.debug("Could not send toggle feedback: {}", e.getMessage());
        }
        
        // JourneyFactions.LOGGER.info("Faction display toggled via context menu");
    }
    
    /**
     * Cleanup method
     */
    public static void cleanup() {
        JourneyFactions.LOGGER.info("JourneyMap context integration cleanup completed");
    }
}