package io.arona74.journeyfactions;

import io.arona74.journeyfactions.config.JourneyFactionsConfig;
import io.arona74.journeyfactions.data.ClientFactionManager;
import io.arona74.journeyfactions.network.ClientNetworkHandler;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JourneyFactions implements ClientModInitializer {
    public static final String MOD_ID = "journeyfactions";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ClientFactionManager factionManager;
    private static boolean journeyMapLoaded = false;
    public static JourneyFactionsConfig CONFIG;

    @Override
    public void onInitializeClient() {
        LOGGER.info("JourneyFactions client mod initializing...");
        
        // Register and load config
        AutoConfig.register(JourneyFactionsConfig.class, JanksonConfigSerializer::new);
        CONFIG = AutoConfig.getConfigHolder(JourneyFactionsConfig.class).getConfig();
        
        debugLog("Config loaded - Debug mode: {}", CONFIG.debugMode);
        
        // Initialize client-side faction data manager
        factionManager = new ClientFactionManager();
        debugLog("ClientFactionManager initialized");
        
        // Initialize network handling
        ClientNetworkHandler.initialize();
        debugLog("ClientNetworkHandler initialized");
        
        // Check if JourneyMap is loaded
        journeyMapLoaded = FabricLoader.getInstance().isModLoaded("journeymap");
        if (journeyMapLoaded) {
            LOGGER.info("JourneyMap detected - integration will be available");
        } else {
            LOGGER.info("JourneyMap not detected - mod will function in data collection mode");
        }
        
        LOGGER.info("JourneyFactions client mod initialized successfully");
        debugLog("Initialization complete - all systems ready");
    }
    
    /**
     * Helper method for debug logging - only logs if debug mode is enabled
     */
    public static void debugLog(String message, Object... args) {
        if (CONFIG != null && CONFIG.debugMode) {
            LOGGER.info("[DEBUG] " + message, args);
        }
    }

    public static ClientFactionManager getFactionManager() {
        return factionManager;
    }

    public static boolean isJourneyMapLoaded() {
        return journeyMapLoaded;
    }
}