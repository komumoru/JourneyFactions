package io.arona74.journeyfactions.journeymap;

import io.arona74.journeyfactions.JourneyFactions;
import io.arona74.journeyfactions.data.ClientFaction;
import io.arona74.journeyfactions.data.ClientFactionManager;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.Context;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.ShapeProperties;
import journeymap.client.api.model.TextProperties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.awt.*;
import java.util.*;
import java.util.List;

public class FactionOverlayManager implements ClientFactionManager.FactionUpdateListener {
    
    private final IClientAPI jmAPI;
    private final Map<String, PolygonOverlay> factionOverlays;
    
    public FactionOverlayManager(IClientAPI jmAPI) {
        this.jmAPI = jmAPI;
        this.factionOverlays = new HashMap<>();
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
        // Refresh overlays periodically
        loadAllFactionOverlays();
    }
    
    private void loadAllFactionOverlays() {
        try {
            JourneyFactions.LOGGER.info("Loading faction overlays...");
            Collection<ClientFaction> factions = JourneyFactions.getFactionManager().getAllFactions();
            JourneyFactions.LOGGER.info("Found {} factions to process", factions.size());
            
            for (ClientFaction faction : factions) {
                JourneyFactions.LOGGER.info("Processing faction: {} (type: {}, chunks: {})", 
                    faction.getName(), faction.getType(), faction.getClaimedChunks().size());
                
                // Only display factions that have claimed territory
                if (!faction.getClaimedChunks().isEmpty()) {
                    createOrUpdateFactionOverlay(faction);
                } else {
                    JourneyFactions.LOGGER.info("Skipping faction {} - no claimed chunks", faction.getName());
                }
            }
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error loading faction overlays", e);
        }
    }
    
    private void createOrUpdateFactionOverlay(ClientFaction faction) {
        String factionId = faction.getId();
        
        JourneyFactions.LOGGER.info("Creating overlay for faction: {} (type: {})", faction.getName(), faction.getType());
        
        // Skip wilderness and other system factions for now
        if (faction.getType() != ClientFaction.FactionType.PLAYER) {
            JourneyFactions.LOGGER.info("Skipping non-player faction: {} (but showing for testing)", faction.getName());
            // Temporarily allow all factions for testing
            // return;
        }
        
        // Remove existing overlay if it exists
        removeOverlay(factionId);
        
        // Get faction's claimed chunks
        Set<ChunkPos> claimedChunks = faction.getClaimedChunks();
        if (claimedChunks.isEmpty()) {
            JourneyFactions.LOGGER.info("No claimed chunks for faction: {}", faction.getName());
            return;
        }
        
        JourneyFactions.LOGGER.info("Faction {} has {} claimed chunks", faction.getName(), claimedChunks.size());
        
        try {
            // Build polygon from chunks
            MapPolygon mapPolygon = buildMapPolygon(claimedChunks);
            if (mapPolygon == null) {
                JourneyFactions.LOGGER.warn("Failed to build polygon for faction: {}", faction.getName());
                return;
            }
            
            JourneyFactions.LOGGER.info("Built polygon for faction: {}", faction.getName());
            
            // Get current world - we'll use overworld as default
            RegistryKey<World> worldKey = World.OVERWORLD;
            
            // Create the overlay with correct constructor
            PolygonOverlay overlay = new PolygonOverlay(
                JourneyFactions.MOD_ID, 
                factionId,
                worldKey,
                createShapeProperties(faction),
                mapPolygon
            );
            
            JourneyFactions.LOGGER.info("Created PolygonOverlay for faction: {}", faction.getName());
            
            // Set display properties
            overlay.setActiveUIs(EnumSet.of(Context.UI.Any));
            overlay.setActiveMapTypes(EnumSet.of(Context.MapType.Any));
            
            // Add label
            overlay.setTextProperties(createTextProperties(faction));
            overlay.setLabel(faction.getDisplayName());
            
            JourneyFactions.LOGGER.info("Configured overlay properties for faction: {}", faction.getName());
            
            // Add to JourneyMap
            try {
                jmAPI.show(overlay);
                factionOverlays.put(factionId, overlay);
                
                JourneyFactions.LOGGER.info("Successfully added overlay to JourneyMap for faction: {} with {} chunks", 
                    faction.getName(), claimedChunks.size());
                    
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Failed to add overlay to JourneyMap for faction: {} - {}", 
                    faction.getName(), e.getMessage());
                JourneyFactions.LOGGER.info("This might be because JourneyMap isn't fully initialized yet. Will retry when mapping starts.");
                // Don't throw - just log and continue
            }
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Failed to create overlay for faction: " + factionId, e);
        }
    }
    
    private MapPolygon buildMapPolygon(Set<ChunkPos> chunks) {
        try {
            JourneyFactions.LOGGER.info("Building polygon from {} chunks", chunks.size());
            
            // Create a simple polygon from chunk boundaries
            List<BlockPos> points = new ArrayList<>();
            
            // For now, create individual rectangles for each chunk
            // TODO: Implement proper polygon merging for connected chunks
            for (ChunkPos chunk : chunks) {
                int worldX = chunk.x * 16;
                int worldZ = chunk.z * 16;
                
                JourneyFactions.LOGGER.info("Processing chunk at ({}, {}) -> world coords ({}, {})", 
                    chunk.x, chunk.z, worldX, worldZ);
                
                // Create rectangle for this chunk (16x16 blocks)
                points.add(new BlockPos(worldX, 64, worldZ));
                points.add(new BlockPos(worldX + 16, 64, worldZ));
                points.add(new BlockPos(worldX + 16, 64, worldZ + 16));
                points.add(new BlockPos(worldX, 64, worldZ + 16));
                points.add(new BlockPos(worldX, 64, worldZ)); // Close the polygon
                break; // For now, just do the first chunk to test
            }
            
            JourneyFactions.LOGGER.info("Created polygon with {} points", points.size());
            
            if (points.isEmpty()) {
                JourneyFactions.LOGGER.warn("No points generated for polygon");
                return null;
            }
            
            // Create MapPolygon from BlockPos list
            MapPolygon polygon = new MapPolygon(points);
            JourneyFactions.LOGGER.info("Successfully created MapPolygon");
            return polygon;
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error building map polygon", e);
            return null;
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
        createOrUpdateFactionOverlay(faction);
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
}