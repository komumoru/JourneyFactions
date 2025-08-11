package io.arona74.journeyfactions.journeymap;

import io.arona74.journeyfactions.JourneyFactions;
import io.arona74.journeyfactions.data.ClientFaction;
import io.arona74.journeyfactions.data.ClientFactionManager;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.Context;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.MapPolygonWithHoles;
import journeymap.client.api.model.ShapeProperties;
import journeymap.client.api.model.TextProperties;
import journeymap.client.api.util.PolygonHelper;
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
        
        // Initialize the display manager
        FactionDisplayManager.initialize(this);
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
    
    /**
     * Update visibility of all faction overlays
     */
    public void updateAllOverlayVisibility(boolean visible) {
        try {
            if (visible) {
                // Show all overlays by adding them to JourneyMap
                for (PolygonOverlay overlay : factionOverlays.values()) {
                    jmAPI.show(overlay);
                }
                JourneyFactions.LOGGER.info("Showed {} faction overlays", factionOverlays.size());
            } else {
                // Hide all overlays by removing them from JourneyMap
                for (PolygonOverlay overlay : factionOverlays.values()) {
                    jmAPI.remove(overlay);
                }
                JourneyFactions.LOGGER.info("Hid {} faction overlays", factionOverlays.size());
            }
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error updating overlay visibility", e);
        }
    }
    
    /**
     * Get the current number of active overlays
     */
    public int getOverlayCount() {
        return factionOverlays.size();
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
        
        // Skip wilderness and other system factions unless configured to show all
        if (faction.getType() != ClientFaction.FactionType.PLAYER) {
            JourneyFactions.LOGGER.info("Processing non-player faction: {} (showing all types)", faction.getName());
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
            // Build polygons using JourneyMap's PolygonHelper for all disconnected regions
            List<MapPolygon> mapPolygons = buildPolygonsUsingJourneyMapHelper(claimedChunks);
            if (mapPolygons.isEmpty()) {
                JourneyFactions.LOGGER.warn("Failed to build polygons for faction: {}", faction.getName());
                return;
            }
            
            JourneyFactions.LOGGER.info("Built {} polygons for faction: {}", mapPolygons.size(), faction.getName());
            
            // Get current world - we'll use overworld as default
            RegistryKey<World> worldKey = World.OVERWORLD;
            
            // Create overlays for each disconnected region
            for (int i = 0; i < mapPolygons.size(); i++) {
                String overlayId = mapPolygons.size() > 1 ? factionId + "_region_" + i : factionId;
                
                // Create the overlay
                PolygonOverlay overlay = new PolygonOverlay(
                    JourneyFactions.MOD_ID, 
                    overlayId,
                    worldKey,
                    createShapeProperties(faction),
                    mapPolygons.get(i)
                );
                
                // Set display properties
                overlay.setActiveUIs(EnumSet.of(Context.UI.Any));
                overlay.setActiveMapTypes(EnumSet.of(Context.MapType.Any));
                
                // Add label to ALL regions (not just the first one)
                overlay.setTextProperties(createTextProperties(faction));
                
                // For multiple regions, add region number to distinguish them
                if (mapPolygons.size() > 1) {
                    overlay.setLabel(faction.getDisplayName() + " #" + (i + 1));
                } else {
                    overlay.setLabel(faction.getDisplayName());
                }
                
                // Set additional properties
                overlay.setOverlayGroupName("faction_territories");
                overlay.setTitle(faction.getDisplayName() + " Territory");
                
                // Add to JourneyMap (visibility is controlled by showing/removing overlays)
                try {
                    // Only show if faction display is enabled
                    if (FactionDisplayManager.isFactionDisplayEnabled()) {
                        jmAPI.show(overlay);
                    }
                    factionOverlays.put(overlayId, overlay);
                    
                    JourneyFactions.LOGGER.info("Successfully created overlay {} for faction: {}", 
                        overlayId, faction.getName());
                        
                } catch (Exception e) {
                    JourneyFactions.LOGGER.error("Failed to add overlay {} to JourneyMap for faction: {} - {}", 
                        overlayId, faction.getName(), e.getMessage());
                }
            }
            
            JourneyFactions.LOGGER.info("Created {} overlays for faction: {} with {} total chunks", 
                mapPolygons.size(), faction.getName(), claimedChunks.size());
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Failed to create overlay for faction: " + factionId, e);
        }
    }
    
    /**
     * Build polygons using JourneyMap's official PolygonHelper for proper rendering
     */
    private List<MapPolygon> buildPolygonsUsingJourneyMapHelper(Set<ChunkPos> chunks) {
        try {
            JourneyFactions.LOGGER.info("Building polygons using JourneyMap PolygonHelper for {} chunks", chunks.size());
            
            List<MapPolygon> polygons = new ArrayList<>();
            
            // Find all disconnected regions
            List<Set<ChunkPos>> regions = findConnectedRegions(chunks);
            JourneyFactions.LOGGER.info("Found {} disconnected regions", regions.size());
            
            // Sort regions by size (largest first) so the main region gets the label
            regions.sort((a, b) -> Integer.compare(b.size(), a.size()));
            
            for (int i = 0; i < regions.size(); i++) {
                Set<ChunkPos> region = regions.get(i);
                JourneyFactions.LOGGER.info("Processing region {} with {} chunks", i + 1, region.size());
                
                try {
                    // Use JourneyMap's PolygonHelper.createChunksPolygon()
                    // This creates optimal polygons with holes support
                    List<MapPolygonWithHoles> polygonsWithHoles = PolygonHelper.createChunksPolygon(region, 70);
                    
                    if (polygonsWithHoles != null && !polygonsWithHoles.isEmpty()) {
                        // Extract the hull (outer boundary) from each MapPolygonWithHoles
                        for (MapPolygonWithHoles polyWithHoles : polygonsWithHoles) {
                            // Access the public hull field directly
                            MapPolygon hull = polyWithHoles.hull;
                            if (hull != null) {
                                polygons.add(hull);
                                JourneyFactions.LOGGER.info("Successfully created polygon using PolygonHelper for region {}", i + 1);
                            } else {
                                JourneyFactions.LOGGER.warn("Hull is null for MapPolygonWithHoles in region {}", i + 1);
                            }
                        }
                    } else {
                        JourneyFactions.LOGGER.warn("PolygonHelper returned empty result for region {}, using fallback", i + 1);
                        
                        // Fallback: create simple polygon manually
                        MapPolygon fallbackPolygon = createFallbackPolygon(region);
                        if (fallbackPolygon != null) {
                            polygons.add(fallbackPolygon);
                            JourneyFactions.LOGGER.info("Created fallback polygon for region {}", i + 1);
                        }
                    }
                    
                } catch (Exception e) {
                    JourneyFactions.LOGGER.error("Error creating polygon for region {} with PolygonHelper: {}", i + 1, e.getMessage());
                    
                    // Fallback: create simple polygon manually
                    try {
                        MapPolygon fallbackPolygon = createFallbackPolygon(region);
                        if (fallbackPolygon != null) {
                            polygons.add(fallbackPolygon);
                            JourneyFactions.LOGGER.info("Created fallback polygon for region {}", i + 1);
                        }
                    } catch (Exception fallbackError) {
                        JourneyFactions.LOGGER.error("Fallback polygon creation also failed for region {}: {}", i + 1, fallbackError.getMessage());
                    }
                }
            }
            
            JourneyFactions.LOGGER.info("Successfully created {} polygons using PolygonHelper", polygons.size());
            return polygons;
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error building polygons with PolygonHelper", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Create a fallback polygon when PolygonHelper fails
     */
    private MapPolygon createFallbackPolygon(Set<ChunkPos> region) {
        if (region.isEmpty()) {
            return null;
        }
        
        if (region.size() == 1) {
            // Single chunk - create simple rectangle
            ChunkPos chunk = region.iterator().next();
            List<BlockPos> boundary = createChunkRectangle(chunk);
            return new MapPolygon(boundary);
        } else {
            // Multiple chunks - create bounding rectangle
            List<BlockPos> boundary = createBoundingRectangle(region);
            return new MapPolygon(boundary);
        }
    }
    
    /**
     * Find connected regions of chunks using flood fill
     */
    private List<Set<ChunkPos>> findConnectedRegions(Set<ChunkPos> chunks) {
        List<Set<ChunkPos>> regions = new ArrayList<>();
        Set<ChunkPos> visited = new HashSet<>();
        
        for (ChunkPos chunk : chunks) {
            if (!visited.contains(chunk)) {
                Set<ChunkPos> region = new HashSet<>();
                floodFill(chunk, chunks, visited, region);
                if (!region.isEmpty()) {
                    regions.add(region);
                }
            }
        }
        
        return regions;
    }
    
    /**
     * Flood fill to find connected chunks
     */
    private void floodFill(ChunkPos start, Set<ChunkPos> allChunks, Set<ChunkPos> visited, Set<ChunkPos> region) {
        if (visited.contains(start) || !allChunks.contains(start)) {
            return;
        }
        
        visited.add(start);
        region.add(start);
        
        // Check 4 adjacent chunks
        ChunkPos[] neighbors = {
            new ChunkPos(start.x + 1, start.z),     // East
            new ChunkPos(start.x - 1, start.z),     // West
            new ChunkPos(start.x, start.z + 1),     // South
            new ChunkPos(start.x, start.z - 1)      // North
        };
        
        for (ChunkPos neighbor : neighbors) {
            floodFill(neighbor, allChunks, visited, region);
        }
    }
    
    /**
     * Create a rectangle for a single chunk
     */
    private List<BlockPos> createChunkRectangle(ChunkPos chunk) {
        List<BlockPos> points = new ArrayList<>();
        int worldX = chunk.x * 16;
        int worldZ = chunk.z * 16;
        
        // Create rectangle (clockwise)
        points.add(new BlockPos(worldX, 70, worldZ));           // Top-left
        points.add(new BlockPos(worldX + 16, 70, worldZ));      // Top-right
        points.add(new BlockPos(worldX + 16, 70, worldZ + 16)); // Bottom-right
        points.add(new BlockPos(worldX, 70, worldZ + 16));      // Bottom-left
        points.add(new BlockPos(worldX, 70, worldZ));           // Close polygon
        
        return points;
    }
    
    /**
     * Create a bounding rectangle for multiple chunks
     */
    private List<BlockPos> createBoundingRectangle(Set<ChunkPos> chunks) {
        // Find bounds
        int minX = chunks.stream().mapToInt(c -> c.x).min().orElse(0);
        int maxX = chunks.stream().mapToInt(c -> c.x).max().orElse(0);
        int minZ = chunks.stream().mapToInt(c -> c.z).min().orElse(0);
        int maxZ = chunks.stream().mapToInt(c -> c.z).max().orElse(0);
        
        // Convert to world coordinates
        int worldMinX = minX * 16;
        int worldMaxX = (maxX + 1) * 16;
        int worldMinZ = minZ * 16;
        int worldMaxZ = (maxZ + 1) * 16;
        
        List<BlockPos> points = new ArrayList<>();
        points.add(new BlockPos(worldMinX, 70, worldMinZ));     // Top-left
        points.add(new BlockPos(worldMaxX, 70, worldMinZ));     // Top-right
        points.add(new BlockPos(worldMaxX, 70, worldMaxZ));     // Bottom-right
        points.add(new BlockPos(worldMinX, 70, worldMaxZ));     // Bottom-left
        points.add(new BlockPos(worldMinX, 70, worldMinZ));     // Close polygon
        
        JourneyFactions.LOGGER.info("Created bounding rectangle: ({},{}) to ({},{}) covering {}x{} chunks", 
            worldMinX, worldMinZ, worldMaxX, worldMaxZ, maxX - minX + 1, maxZ - minZ + 1);
        
        return points;
    }
    
    private void removeOverlay(String factionId) {
        // Remove main overlay and any region overlays
        Set<String> overlaysToRemove = new HashSet<>();
        
        for (String overlayId : factionOverlays.keySet()) {
            if (overlayId.equals(factionId) || overlayId.startsWith(factionId + "_region_")) {
                overlaysToRemove.add(overlayId);
            }
        }
        
        for (String overlayId : overlaysToRemove) {
            PolygonOverlay overlay = factionOverlays.remove(overlayId);
            if (overlay != null) {
                try {
                    jmAPI.remove(overlay);
                    JourneyFactions.LOGGER.debug("Removed overlay: {}", overlayId);
                } catch (Exception e) {
                    JourneyFactions.LOGGER.warn("Failed to remove overlay: {}", overlayId, e);
                }
            }
        }
        
        if (!overlaysToRemove.isEmpty()) {
            JourneyFactions.LOGGER.info("Removed {} overlays for faction: {}", overlaysToRemove.size(), factionId);
        }
    }
    
    private ShapeProperties createShapeProperties(ClientFaction faction) {
        Color factionColor = faction.getEffectiveColor();
        
        return new ShapeProperties()
            .setStrokeColor(factionColor.getRGB())
            .setFillColor(new Color(factionColor.getRed(), factionColor.getGreen(), 
                                  factionColor.getBlue(), 50).getRGB())
            .setStrokeWidth(1.5f)
            .setFillOpacity(0.3f)
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
    
    public void clearAllOverlays() {
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