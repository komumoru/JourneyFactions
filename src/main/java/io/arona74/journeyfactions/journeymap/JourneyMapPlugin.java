package io.arona74.journeyfactions.journeymap;

import io.arona74.journeyfactions.JourneyFactions;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.EnumSet;

@ParametersAreNonnullByDefault
public class JourneyMapPlugin implements IClientPlugin {
    
    private static final String PLUGIN_ID = "journeyfactions";
    private IClientAPI jmAPI;
    private FactionOverlayManager overlayManager;
    
    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.jmAPI = jmClientApi;
        this.overlayManager = new FactionOverlayManager(jmClientApi);
        
        JourneyFactions.LOGGER.info("JourneyMap integration initialized");
        
        // Subscribe to relevant events
        jmClientApi.subscribe(getModId(), EnumSet.of(
            ClientEvent.Type.MAPPING_STARTED,
            ClientEvent.Type.MAPPING_STOPPED,
            ClientEvent.Type.DISPLAY_UPDATE
        ));
        
        // Connect to faction manager for updates
        JourneyFactions.getFactionManager().addListener(overlayManager);
    }
    
    @Override
    public String getModId() {
        return PLUGIN_ID;
    }
    
    @Override
    public void onEvent(ClientEvent event) {
        switch (event.type) {
            case MAPPING_STARTED:
                overlayManager.onMappingStarted();
                break;
            case MAPPING_STOPPED:
                overlayManager.onMappingStopped();
                break;
            case DISPLAY_UPDATE:
                overlayManager.updateDisplay();
                break;
        }
    }
    
    public IClientAPI getAPI() {
        return jmAPI;
    }
    
    public FactionOverlayManager getOverlayManager() {
        return overlayManager;
    }
}