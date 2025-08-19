package io.arona74.journeyfactions.journeymap;

import io.arona74.journeyfactions.JourneyFactions;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;

import java.util.EnumSet;

public class JourneyMapPlugin implements IClientPlugin {
    
    private static final String PLUGIN_ID = "journeyfactions";
    private IClientAPI jmAPI;
    private FactionOverlayManager overlayManager;
    
    public JourneyMapPlugin() {
        JourneyFactions.LOGGER.info("JourneyMapPlugin constructor called");
    }
    
    @Override
    public void initialize(IClientAPI jmClientApi) {
        JourneyFactions.LOGGER.info("JourneyMapPlugin.initialize() called");
        
        this.jmAPI = jmClientApi;
        this.overlayManager = new FactionOverlayManager(jmClientApi);
        
        JourneyFactions.LOGGER.info("JourneyMap integration initialized");
        
        try {
            // Subscribe to relevant events
            jmClientApi.subscribe(getModId(), EnumSet.of(
                ClientEvent.Type.MAPPING_STARTED,
                ClientEvent.Type.MAPPING_STOPPED,
                ClientEvent.Type.DISPLAY_UPDATE
            ));
            JourneyFactions.LOGGER.info("Subscribed to JourneyMap events");
            
            // Connect to faction manager for updates
            JourneyFactions.getFactionManager().addListener(overlayManager);
            JourneyFactions.LOGGER.info("Connected to faction manager");
            
            // Initialize the faction toggle button/keybinding
            FactionToggleButton.initialize(jmClientApi);
            
            // Initialize context menu integration
            JourneyMapContextIntegration.initialize(jmClientApi);
            
            JourneyFactions.LOGGER.info("Plugin initialization complete - waiting for mapping events");
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error during JourneyMap plugin initialization", e);
        }
    }
    
    @Override
    public String getModId() {
        return PLUGIN_ID;
    }
    
    @Override
    public void onEvent(ClientEvent event) {
        // JourneyFactions.LOGGER.info("JourneyMap event received: {}", event.type);
        
        try {
            switch (event.type) {
                case MAPPING_STARTED:
                    // JourneyFactions.LOGGER.info("JourneyMap mapping started - creating overlays");
                    overlayManager.onMappingStarted();
                    break;
                case MAPPING_STOPPED:
                    // JourneyFactions.LOGGER.info("JourneyMap mapping stopped");
                    overlayManager.onMappingStopped();
                    break;
                case DISPLAY_UPDATE:
                    // JourneyFactions.LOGGER.debug("JourneyMap display update");
                    overlayManager.updateDisplay();
                    break;
                default:
                    // JourneyFactions.LOGGER.debug("Unhandled JourneyMap event: {}", event.type);
                    break;
            }
        } catch (Exception e) {
            // JourneyFactions.LOGGER.error("Error handling JourneyMap event: " + event.type, e);
        }
    }
    
    /**
     * Get the JourneyMap API instance
     */
    public IClientAPI getAPI() {
        return jmAPI;
    }
    
    /**
     * Get the faction overlay manager
     */
    public FactionOverlayManager getOverlayManager() {
        return overlayManager;
    }
    
    /**
     * Check if the plugin is properly initialized
     */
    public boolean isInitialized() {
        return jmAPI != null && overlayManager != null;
    }
    
    /**
     * Get plugin information
     */
    public String getPluginInfo() {
        return String.format("JourneyFactions Plugin | API: %s | Overlays: %d", 
            jmAPI != null ? "Connected" : "Disconnected",
            overlayManager != null ? overlayManager.getOverlayCount() : 0);
    }
    
    /**
     * Cleanup method for plugin shutdown
     */
    public void cleanup() {
        try {
            if (overlayManager != null) {
                overlayManager.clearAllOverlays();
                JourneyFactions.getFactionManager().removeListener(overlayManager);
            }
            
            FactionToggleButton.cleanup();
            JourneyMapContextIntegration.cleanup();
            
            JourneyFactions.LOGGER.info("JourneyMapPlugin cleanup completed");
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error during plugin cleanup", e);
        }
    }
}