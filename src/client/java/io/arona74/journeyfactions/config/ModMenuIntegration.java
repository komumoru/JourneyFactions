package io.arona74.journeyfactions.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.arona74.journeyfactions.JourneyFactions;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            try {
                return AutoConfig.getConfigScreen(JourneyFactionsConfig.class, parent).get();
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Failed to open config screen", e);
                return parent; // Fallback to parent screen if config screen fails
            }
        };
    }
}