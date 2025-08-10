package io.arona74.journeyfactions.journeymap;

import io.arona74.journeyfactions.JourneyFactions;
import io.arona74.journeyfactions.data.ClientFaction;
import io.arona74.journeyfactions.data.ClientFactionManager;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.Context;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.model.ShapeProperties;
import journeymap.client.api.model.TextProperties;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.awt.*;
import java.util.*;
import java.util.List;

public class FactionOverlayManager implements ClientFactionManager.FactionUpdateListener {
    
    private final IClientAPI jmAPI;
    private final Map<String, PolygonOverlay> factionOverlays;
    private final MinecraftClient mc;
    private final FactionPolygonBuilder polygonBuilder;
    
    public FactionOverlayManager(IClientAPI jmAPI) {
        this.jmAPI = jmAPI;
        this.factionOverlays = new HashMap<>();
        this.mc = MinecraftClient.getInstance();
        this.polygonBuilder = new FactionPolygonBuilder();
    }
    
    public void onMappingStarted() {
        JourneyFactions.LOGGER.debug("JourneyMap mapping started - loading faction overlays");
        loadAllFactionOverlays();
    }
    
    public void onMappingStopped() {
        JourneyFactions.LOGGER.debug("JourneyMap mapping stopped - clearing overlays");
        clearAllOverlays();
    }
    
    public void updateDisplay() {
        if (mc.world == null || mc.player == null) return;
        
        // Refresh overlays periodically
        loadAllFactionOverlays();
    }
    
    private void loadAllFactionOverlays() {
        if (mc.world == null) return;
        
        try {
            Collection<ClientFaction> factions = JourneyFactions.getFactionManager().getAllFactions();
            
            for (ClientFaction faction : factions) {
                // Only display factions that have claimed territory
                if (!faction.getClaimedChunks().isEmpty()) {
                    createOrUpdateFactionOverlay(faction);
                }
            }
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error loading faction overlays", e);
        }
    }
    
    private void createOrUpdateFactionOverlay(ClientFaction faction) {
        String factionId = faction.getId();
        
        // Skip wilderness and other system factions for now
        if (faction.getType() != ClientFaction.FactionType.PLAYER) {
            return;
        }
        
        // Remove existing overlay if it exists
        removeOverlay(factionId);
        
        // Get faction's claimed chunks
        Set<ChunkPos> claimedChunks = faction.getClaimedChunks();
        if (claimedChunks.isEmpty()) return;
        
        try {
            // Build polygon from chunks
            List<BlockPos> polygonPoints = polygonBuilder.buildPolygonFromChunks(claimedChunks);
            if (polygonPoints.isEmpty()) return;
            
            // Create the overlay
            PolygonOverlay overlay = new PolygonOverlay(
                JourneyFactions.MOD_ID, 
                factionId,
                mc.world.getRegistryKey(),
                createShapeProperties(faction),
                polygonPoints
            );
            
            // Set display properties
            overlay.setActiveUIs(EnumSet.of(Context.UI.Any));
            overlay.setActiveMapTypes(EnumSet.of(Context.MapType.Any));
            
            // Add label
            overlay.setTextProperties(createTextProperties(faction));
            overlay.setLabel(faction.getDisplayName());
            
            // Add to JourneyMap
            jmAPI.show(overlay);
            factionOverlays.put(factionId, overlay);
            
            JourneyFactions.LOGGER.debug("Created overlay for faction: {} with {} chunks", 
                faction.getName(), claimedChunks.size());
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Failed to create overlay for faction: " + factionId, e);
        }
    }
    
    private void removeOverlay(String factionId) {
        PolygonOverlay existingOverlay = factionOverlays.remove(factionId);
        if (existingOverlay != null) {
            try {
                jmAPI.remove(existingOverlay);
            } catch (Exception e) {
                JourneyFactions.LOGGER.warn("Failed to remove existing overlay for faction: " + factionId, e);
            }
        }
    }
    
    private ShapeProperties createShapeProperties(ClientFaction faction) {
        Color factionColor = faction.getEffectiveColor();
        
        return new ShapeProperties()
            .setStrokeColor(factionColor.getRGB())
            .setFillColor(new Color(factionColor.getRed(), factionColor.getGreen(), 
                                  factionColor.getBlue(), 30).getRGB()) // Very transparent fill
            .setStrokeWidth(2.0f)
            .setFillOpacity(0.2f)
            .setStrokeOpacity(0.9f);
    }
    
    private TextProperties createTextProperties(ClientFaction faction) {
        Color factionColor = faction.getEffectiveColor();
        
        return new TextProperties()
            .setColor(factionColor.brighter().getRGB())
            .setBackgroundColor(new Color(0, 0, 0, 128).getRGB())
            .setBackgroundOpacity(0.7f)
            .setScale(1.2f);
    }
    
    private void clearAllOverlays() {
        for (Map.Entry<String, PolygonOverlay> entry : factionOverlays.entrySet()) {
            try {
                jmAPI.remove(entry.getValue());
            } catch (Exception e) {
                JourneyFactions.LOGGER.warn("Failed to remove faction overlay: " + entry.getKey(), e);
            }
        }
        factionOverlays.clear();
    }
    
    // FactionUpdateListener implementation
    @Override
    public void onFactionUpdated(ClientFaction faction) {
        if (mc.world != null) {
            createOrUpdateFactionOverlay(faction);
        }
    }
    
    @Override
    public void onFactionRemoved(ClientFaction faction) {
        removeOverlay(faction.getId());
    }
    
    @Override
    public void onChunkChanged(ChunkPos chunk, String oldFactionId, String newFactionId) {
        // Update overlays for affected factions
        if (oldFactionId != null) {
            ClientFaction oldFaction = JourneyFactions.getFactionManager().getFaction(oldFactionId);
            if (oldFaction != null) {
                createOrUpdateFactionOverlay(oldFaction);
            }
        }
        
        if (newFactionId != null) {
            ClientFaction newFaction = JourneyFactions.getFactionManager().getFaction(newFactionId);
            if (newFaction != null) {
                createOrUpdateFactionOverlay(newFaction);
            }
        }
    }
    
    @Override
    public void onDataCleared() {
        clearAllOverlays();
    }
    
    /**
     * Helper class to build proper polygon boundaries from chunk sets
     */
    private static class FactionPolygonBuilder {
        
        public List<BlockPos> buildPolygonFromChunks(Set<ChunkPos> chunks) {
            if (chunks.isEmpty()) return new ArrayList<>();
            
            // For now, create simple rectangles for each chunk
            // TODO: Implement proper polygon merging for better visuals
            List<BlockPos> allPoints = new ArrayList<>();
            
            for (ChunkPos chunk : chunks) {
                allPoints.addAll(getChunkBoundary(chunk));
            }
            
            return allPoints;
        }
        
        private List<BlockPos> getChunkBoundary(ChunkPos chunk) {
            int worldX = chunk.x * 16;
            int worldZ = chunk.z * 16;
            int y = 64; // Use a fixed Y level for the overlay
            
            // Create rectangle for this chunk (16x16 blocks)
            return Arrays.asList(
                new BlockPos(worldX, y, worldZ),
                new BlockPos(worldX + 16, y, worldZ),
                new BlockPos(worldX + 16, y, worldZ + 16),
                new BlockPos(worldX, y, worldZ + 16),
                new BlockPos(worldX, y, worldZ) // Close the polygon
            );
        }
    }
}