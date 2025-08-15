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
        // JourneyFactions.LOGGER.debug("JourneyMap mapping started - loading faction overlays");
        loadAllFactionOverlays();
    }
    
    public void onMappingStopped() {
        // JourneyFactions.LOGGER.debug("JourneyMap mapping stopped - clearing overlays");
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
                // JourneyFactions.LOGGER.info("Showed {} faction overlays", factionOverlays.size());
            } else {
                // Hide all overlays by removing them from JourneyMap
                for (PolygonOverlay overlay : factionOverlays.values()) {
                    jmAPI.remove(overlay);
                }
                // JourneyFactions.LOGGER.info("Hid {} faction overlays", factionOverlays.size());
            }
        } catch (Exception e) {
            // JourneyFactions.LOGGER.error("Error updating overlay visibility", e);
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
            // JourneyFactions.LOGGER.info("Loading faction overlays...");
            Collection<ClientFaction> factions = JourneyFactions.getFactionManager().getAllFactions();
            // JourneyFactions.LOGGER.info("Found {} factions to process", factions.size());
            
            for (ClientFaction faction : factions) {
                // JourneyFactions.LOGGER.info("Processing faction: {} (type: {}, chunks: {})", 
                //    faction.getName(), faction.getType(), faction.getClaimedChunks().size());
                
                // Only display factions that have claimed territory
                if (!faction.getClaimedChunks().isEmpty()) {
                    createOrUpdateFactionOverlay(faction);
                } else {
                    // JourneyFactions.LOGGER.info("Skipping faction {} - no claimed chunks", faction.getName());
                }
            }
            
        } catch (Exception e) {
            // JourneyFactions.LOGGER.error("Error loading faction overlays", e);
        }
    }
    
    private void createOrUpdateFactionOverlay(ClientFaction faction) {
        String factionId = faction.getId();

        // Remove any old overlays for this faction
        removeOverlay(factionId);

        Set<ChunkPos> claimedChunks = faction.getClaimedChunks();
        if (claimedChunks.isEmpty()) {
            return;
        }

        try {
            // Build polygons with holes preserved
            List<MapPolygonWithHoles> polygons = buildPolygonsUsingJourneyMapHelper(claimedChunks);
            if (polygons.isEmpty()) {
                return;
            }

            RegistryKey<World> worldKey = World.OVERWORLD;

            for (int i = 0; i < polygons.size(); i++) {
                String overlayId = polygons.size() > 1 ? factionId + "_region_" + i : factionId;

                PolygonOverlay overlay = new PolygonOverlay(
                    JourneyFactions.MOD_ID,
                    overlayId,
                    worldKey,
                    createShapeProperties(faction),
                    polygons.get(i) // ✅ Pass with holes
                );

                overlay.setActiveUIs(EnumSet.of(Context.UI.Any));
                overlay.setActiveMapTypes(EnumSet.of(Context.MapType.Any));
                overlay.setTextProperties(createTextProperties(faction));

                // Label numbering for multi-region factions
                overlay.setLabel(polygons.size() > 1
                    ? faction.getDisplayName() + " #" + (i + 1)
                    : faction.getDisplayName());

                overlay.setOverlayGroupName("faction_territories");
                overlay.setTitle(faction.getDisplayName() + " Territory");

                if (FactionDisplayManager.isFactionDisplayEnabled()) {
                    jmAPI.show(overlay);
                }

                factionOverlays.put(overlayId, overlay);
            }

        } catch (Exception e) {
            // Log or handle error
        }
    }

    
    /**
     * Build polygons using JourneyMap's official PolygonHelper for proper rendering,
     * preserving holes when present.
     */
    private List<MapPolygonWithHoles> buildPolygonsUsingJourneyMapHelper(Set<ChunkPos> chunks) {
        List<MapPolygonWithHoles> polygons = new ArrayList<>();

        try {
            // Split into connected regions first
            List<Set<ChunkPos>> regions = findConnectedRegions(chunks);
            regions.sort((a, b) -> Integer.compare(b.size(), a.size())); // largest first

            for (Set<ChunkPos> region : regions) {
                try {
                    // Let JourneyMap do the heavy lifting
                    List<MapPolygonWithHoles> polysWithHoles = PolygonHelper.createChunksPolygon(region, 70);

                    if (polysWithHoles != null && !polysWithHoles.isEmpty()) {
                        polygons.addAll(polysWithHoles); // ✅ Keep holes
                    } else {
                        // Fallback: create simple bounding or chunk polygon
                        MapPolygon fallback = createFallbackPolygon(region);
                        if (fallback != null) {
                            polygons.add(new MapPolygonWithHoles(fallback, Collections.emptyList()));
                        }
                    }
                } catch (Exception e) {
                    // If helper fails, still make something visible
                    MapPolygon fallback = createFallbackPolygon(region);
                    if (fallback != null) {
                        polygons.add(new MapPolygonWithHoles(fallback, Collections.emptyList()));
                    }
                }
            }
        } catch (Exception e) {
            // No polygons at all if something fatal happens
            return Collections.emptyList();
        }

        return polygons;
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
        
        // JourneyFactions.LOGGER.info("Created bounding rectangle: ({},{}) to ({},{}) covering {}x{} chunks", 
        //    worldMinX, worldMinZ, worldMaxX, worldMaxZ, maxX - minX + 1, maxZ - minZ + 1);
        
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
                    // JourneyFactions.LOGGER.debug("Removed overlay: {}", overlayId);
                } catch (Exception e) {
                    // JourneyFactions.LOGGER.warn("Failed to remove overlay: {}", overlayId, e);
                }
            }
        }
        
        if (!overlaysToRemove.isEmpty()) {
            // JourneyFactions.LOGGER.info("Removed {} overlays for faction: {}", overlaysToRemove.size(), factionId);
        }
    }
    
    private ShapeProperties createShapeProperties(ClientFaction faction) {
        Color factionColor = faction.getEffectiveColor();
        
        return new ShapeProperties()
            .setStrokeColor(factionColor.getRGB())
            .setFillColor(new Color(factionColor.getRed(), factionColor.getGreen(), factionColor.getBlue(), 50).getRGB())
            .setStrokeWidth(1.5f)
            .setFillOpacity(0.1f)
            .setStrokeOpacity(0.9f);
    }
    
    private TextProperties createTextProperties(ClientFaction faction) {
        Color factionColor = faction.getEffectiveColor();
        
        Color backgroundColor;
        Color textColor;

        // Faction color is black → use lighter background
        if (factionColor.getRed() == 0 && factionColor.getGreen() == 0 && factionColor.getBlue() == 0) {
            // Default text
            textColor = new Color(factionColor.getRGB());
            // Lighter background
            backgroundColor = new Color(170, 170, 170, 128);
            // JourneyFactions.LOGGER.info("Faction color is BLACK");
        } else {
            // Faction color is dark_gray → use lighter text
            if (factionColor.getRed() == 85 && factionColor.getGreen() == 85 && factionColor.getBlue() == 85) {
                // Lighter text
                textColor = new Color(170, 170, 170, 128);
                // Default background
                backgroundColor = new Color(200, 200, 200, 128);
                // JourneyFactions.LOGGER.info("Faction color is DARK_GRAY");
            } else {
                // Default text
                textColor = new Color(factionColor.getRGB());
                // Default background
                backgroundColor = new Color(0, 0, 0, 128);
                // JourneyFactions.LOGGER.info("Faction color is ELSE");
            }
        }

        return new TextProperties()
            .setColor(textColor.brighter().getRGB())
            .setOpacity(0f)
            .setBackgroundColor(backgroundColor.getRGB())
            .setBackgroundOpacity(1f)
            .setScale(1.0f)
            .setFontShadow(false);
    }
    
    public void clearAllOverlays() {
        for (Map.Entry<String, PolygonOverlay> entry : factionOverlays.entrySet()) {
            try {
                jmAPI.remove(entry.getValue());
            } catch (Exception e) {
                // JourneyFactions.LOGGER.warn("Failed to remove faction overlay: " + entry.getKey(), e);
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