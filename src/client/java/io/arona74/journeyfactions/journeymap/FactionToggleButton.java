package io.arona74.journeyfactions.journeymap;

import io.arona74.journeyfactions.JourneyFactions;
import journeymap.client.api.IClientAPI;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Creates JourneyMap GUI integration for faction controls
 */
public class FactionToggleButton {
    
    private static KeyBinding toggleKeyBinding;
    private static boolean isInitialized = false;
    
    /**
     * Initialize the faction controls
     */
    public static void initialize(IClientAPI jmAPI) {
        try {
            setupKeybinding();
            isInitialized = true;
            JourneyFactions.LOGGER.info("Faction controls initialized successfully - Keybinding: 'G' key");
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Failed to initialize faction controls: {}", e.getMessage());
            isInitialized = false;
        }
    }
    
    /**
     * Set up keybinding with improved error handling
     */
    private static void setupKeybinding() {
        try {
            JourneyFactions.LOGGER.info("Setting up faction toggle keybinding...");
            
            // Create a keybinding for toggling faction display
            toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.journeyfactions.toggle", // Translation key
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G, // Default to 'G' key
                "category.journeyfactions" // Category
            ));
            
            JourneyFactions.LOGGER.info("Keybinding registered: {}", toggleKeyBinding != null ? "SUCCESS" : "FAILED");
            
            if (toggleKeyBinding != null) {
                JourneyFactions.LOGGER.info("Keybinding details: {}", toggleKeyBinding.getBoundKeyLocalizedText().getString());
            }
            
            // Register tick event to check for key presses
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                try {
                    if (toggleKeyBinding != null && toggleKeyBinding.wasPressed()) {
                        JourneyFactions.LOGGER.info("'G' key pressed - toggling faction display");
                        toggleFactionDisplay();
                    }
                } catch (Exception e) {
                    JourneyFactions.LOGGER.error("Error in keybinding tick event: {}", e.getMessage());
                }
            });
            
            JourneyFactions.LOGGER.info("Client tick event registered for keybinding monitoring");
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Failed to setup keybinding: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Toggle faction display with comprehensive feedback
     */
    private static void toggleFactionDisplay() {
        try {
            JourneyFactions.LOGGER.info("=== FACTION TOGGLE TRIGGERED ===");
            
            boolean wasEnabled = FactionDisplayManager.isFactionDisplayEnabled();
            JourneyFactions.LOGGER.info("Previous state: {}", wasEnabled ? "VISIBLE" : "HIDDEN");
            
            // Perform the toggle
            FactionDisplayManager.toggleFactionDisplay();
            
            boolean isNowEnabled = FactionDisplayManager.isFactionDisplayEnabled();
            JourneyFactions.LOGGER.info("New state: {}", isNowEnabled ? "VISIBLE" : "HIDDEN");
            
            // Show message to player
            try {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    String message = String.format("§eFaction territories: %s", 
                        isNowEnabled ? "§aShown" : "§cHidden");
                    
                    // Send both action bar and chat message for better visibility
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal(message), 
                        true // Action bar
                    );
                    
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("[JourneyFactions] " + message), 
                        false // Chat
                    );
                    
                    JourneyFactions.LOGGER.info("Sent toggle message to player");
                } else {
                    JourneyFactions.LOGGER.warn("No player available for toggle message");
                }
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Could not send player message: {}", e.getMessage());
            }
            
            JourneyFactions.LOGGER.info("=== FACTION TOGGLE COMPLETE ===");
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error during faction toggle: {}", e.getMessage());
        }
    }
    
    /**
     * Check if keybinding is working
     */
    public static void testKeybinding() {
        JourneyFactions.LOGGER.info("=== KEYBINDING TEST ===");
        JourneyFactions.LOGGER.info("Initialized: {}", isInitialized);
        JourneyFactions.LOGGER.info("Keybinding object: {}", toggleKeyBinding != null ? "EXISTS" : "NULL");
        
        if (toggleKeyBinding != null) {
            JourneyFactions.LOGGER.info("Bound key: {}", toggleKeyBinding.getBoundKeyLocalizedText().getString());
            JourneyFactions.LOGGER.info("Translation key: {}", toggleKeyBinding.getTranslationKey());
            JourneyFactions.LOGGER.info("Category: {}", toggleKeyBinding.getCategory());
        }
        
        JourneyFactions.LOGGER.info("Try pressing the key now and watch the logs");
        JourneyFactions.LOGGER.info("=== END KEYBINDING TEST ===");
    }
    
    /**
     * Force trigger toggle (for testing)
     */
    public static void forceTrigger() {
        JourneyFactions.LOGGER.info("=== FORCE TRIGGERING TOGGLE ===");
        toggleFactionDisplay();
    }
    
    /**
     * Manual toggle method for debug commands
     */
    public static void manualToggle() {
        JourneyFactions.LOGGER.info("=== MANUAL TOGGLE CALLED ===");
        toggleFactionDisplay();
    }
    
    /**
     * Get status information
     */
    public static String getStatus() {
        String keybind = toggleKeyBinding != null 
            ? toggleKeyBinding.getBoundKeyLocalizedText().getString() 
            : "Not available";
        
        return String.format("Faction Toggle: [%s] | Initialized: %s | Status: %s", 
            keybind,
            isInitialized,
            FactionDisplayManager.isFactionDisplayEnabled() ? "VISIBLE" : "HIDDEN");
    }
    
    /**
     * Show comprehensive help information
     */
    public static void showHelp() {
        JourneyFactions.LOGGER.info("=== FACTION CONTROLS HELP ===");
        JourneyFactions.LOGGER.info("Status: {}", getStatus());
        
        if (toggleKeyBinding != null) {
            JourneyFactions.LOGGER.info("Primary method: Press '{}' key to toggle faction territories", 
                toggleKeyBinding.getBoundKeyLocalizedText().getString());
        } else {
            JourneyFactions.LOGGER.info("Keybinding not available!");
        }
        
        JourneyFactions.LOGGER.info("Alternative methods:");
        JourneyFactions.LOGGER.info("  FactionToggleButton.manualToggle() - Manual toggle");
        JourneyFactions.LOGGER.info("  FactionToggleButton.forceTrigger() - Force trigger");
        JourneyFactions.LOGGER.info("  FactionToggleButton.testKeybinding() - Test keybinding setup");
        JourneyFactions.LOGGER.info("  FactionDebugCommands.toggleDisplay() - Alternative toggle");
        JourneyFactions.LOGGER.info("  FactionDebugCommands.openControlsGUI() - GUI controls");
        JourneyFactions.LOGGER.info("==============================");
    }
    
    /**
     * Cleanup method
     */
    public static void cleanup() {
        JourneyFactions.LOGGER.info("FactionToggleButton cleanup completed");
        isInitialized = false;
    }
}