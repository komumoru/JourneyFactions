package io.arona74.journeyfactions.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "journeyfactions")
public class JourneyFactionsConfig implements ConfigData {
    
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 1)
    public boolean separateLabelOverlay = true;
    
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public LabelAnchorMode labelAnchorMode = LabelAnchorMode.HULL_CENTROID;
    
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 1)
    public boolean debugMode = false;
    
    public enum LabelAnchorMode {
        /** BFS from perimeter to find the chunk farthest from any edge/hole. */
        FARTHEST_INTERIOR_CHUNK,
        /** Area-weighted hull centroid (may fall in a hole for weird shapes). */
        HULL_CENTROID,
        /** Fallback: first claimed chunk center. */
        FIRST_CHUNK_CENTER
    }
}