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
            // Build merged polygon from chunks
            List<MapPolygon> mapPolygons = buildAllPolygons(claimedChunks);
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
                
                // Add label only to the first/largest region to avoid clutter
                if (i == 0) {
                    overlay.setTextProperties(createTextProperties(faction));
                    overlay.setLabel(faction.getDisplayName());
                }
                
                // Add to JourneyMap
                try {
                    jmAPI.show(overlay);
                    factionOverlays.put(overlayId, overlay);
                    
                    JourneyFactions.LOGGER.info("Successfully added overlay {} to JourneyMap for faction: {}", 
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
     * Build all polygons for a faction's chunks, including disconnected regions
     */
    private List<MapPolygon> buildAllPolygons(Set<ChunkPos> chunks) {
        try {
            int chunkCount = chunks.size();
            JourneyFactions.LOGGER.info("Building all polygons from {} chunks", chunkCount);
            
            List<MapPolygon> polygons = new ArrayList<>();
            
            if (chunkCount == 1) {
                // Single chunk - create simple rectangle
                ChunkPos chunk = chunks.iterator().next();
                List<BlockPos> boundary = createChunkRectangle(chunk);
                polygons.add(new MapPolygon(boundary));
                JourneyFactions.LOGGER.info("Created single chunk rectangle");
            } else if (isRectangularRegion(chunks)) {
                // Perfect rectangular region - create precise rectangle
                List<BlockPos> boundary = createPreciseRectangle(chunks);
                polygons.add(new MapPolygon(boundary));
                JourneyFactions.LOGGER.info("Created precise rectangle for {} chunks", chunkCount);
            } else {
                // Complex shape - create chunk-aligned boundary for each region
                List<List<BlockPos>> boundaries = createAllChunkAlignedBoundaries(chunks);
                for (List<BlockPos> boundary : boundaries) {
                    if (!boundary.isEmpty()) {
                        polygons.add(new MapPolygon(boundary));
                    }
                }
                JourneyFactions.LOGGER.info("Created {} chunk-aligned boundaries for {} chunks", 
                    boundaries.size(), chunkCount);
            }
            
            JourneyFactions.LOGGER.info("Successfully created {} polygons", polygons.size());
            return polygons;
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error building all polygons", e);
            return new ArrayList<>();
        }
    }

    /**
     * Build a merged polygon from multiple chunks using chunk-aligned boundaries
     * @deprecated Use buildAllPolygons for better support of disconnected regions
     */
    private MapPolygon buildMergedPolygon(Set<ChunkPos> chunks) {
        List<MapPolygon> polygons = buildAllPolygons(chunks);
        return polygons.isEmpty() ? null : polygons.get(0);
    }
    
    /**
     * Create chunk-aligned boundaries for all disconnected regions
     */
    private List<List<BlockPos>> createAllChunkAlignedBoundaries(Set<ChunkPos> chunks) {
        JourneyFactions.LOGGER.info("Creating chunk-aligned boundaries for all regions in {} chunks", chunks.size());
        
        // Find all disconnected regions
        List<Set<ChunkPos>> regions = findConnectedRegions(chunks);
        JourneyFactions.LOGGER.info("Found {} disconnected regions", regions.size());
        
        List<List<BlockPos>> boundaries = new ArrayList<>();
        
        // Sort regions by size (largest first) so the main region gets the label
        regions.sort((a, b) -> Integer.compare(b.size(), a.size()));
        
        for (int i = 0; i < regions.size(); i++) {
            Set<ChunkPos> region = regions.get(i);
            JourneyFactions.LOGGER.info("Processing region {} with {} chunks", i + 1, region.size());
            
            List<BlockPos> boundary = traceChunkBoundary(region);
            if (!boundary.isEmpty()) {
                boundaries.add(boundary);
                JourneyFactions.LOGGER.info("Created boundary for region {} with {} points", i + 1, boundary.size());
            } else {
                JourneyFactions.LOGGER.warn("Failed to create boundary for region {} with {} chunks", i + 1, region.size());
            }
        }
        
        return boundaries;
    }

    /**
     * Create a chunk-aligned boundary that follows actual chunk edges (legacy method for single region)
     */
    private List<BlockPos> createChunkAlignedBoundary(Set<ChunkPos> chunks) {
        List<List<BlockPos>> allBoundaries = createAllChunkAlignedBoundaries(chunks);
        return allBoundaries.isEmpty() ? new ArrayList<>() : allBoundaries.get(0);
    }
    
    /**
     * Trace the boundary of a connected region by following chunk edges
     */
    private List<BlockPos> traceChunkBoundary(Set<ChunkPos> region) {
        if (region.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (region.size() == 1) {
            return createChunkRectangle(region.iterator().next());
        }
        
        JourneyFactions.LOGGER.info("Tracing boundary for {} chunks", region.size());
        
        // Use a more reliable boundary tracing method
        return createBoundaryFromChunkGrid(region);
    }
    
    /**
     * Create boundary by building a grid and walking around the perimeter
     */
    private List<BlockPos> createBoundaryFromChunkGrid(Set<ChunkPos> region) {
        // Find the bounds of the region
        int minChunkX = region.stream().mapToInt(c -> c.x).min().orElse(0);
        int maxChunkX = region.stream().mapToInt(c -> c.x).max().orElse(0);
        int minChunkZ = region.stream().mapToInt(c -> c.z).min().orElse(0);
        int maxChunkZ = region.stream().mapToInt(c -> c.z).max().orElse(0);
        
        // Create a grid to mark which chunks exist
        boolean[][] grid = new boolean[maxChunkX - minChunkX + 3][maxChunkZ - minChunkZ + 3];
        
        // Fill the grid (with 1-block border of false)
        for (ChunkPos chunk : region) {
            int gridX = chunk.x - minChunkX + 1;
            int gridZ = chunk.z - minChunkZ + 1;
            grid[gridX][gridZ] = true;
        }
        
        // Find boundary points by checking each chunk's corners
        Set<Point> boundaryPoints = new HashSet<>();
        
        for (ChunkPos chunk : region) {
            int chunkX = chunk.x;
            int chunkZ = chunk.z;
            
            // Check each corner of this chunk
            addCornerIfBoundary(boundaryPoints, chunkX * 16, chunkZ * 16, region);           // Top-left
            addCornerIfBoundary(boundaryPoints, (chunkX + 1) * 16, chunkZ * 16, region);     // Top-right  
            addCornerIfBoundary(boundaryPoints, (chunkX + 1) * 16, (chunkZ + 1) * 16, region); // Bottom-right
            addCornerIfBoundary(boundaryPoints, chunkX * 16, (chunkZ + 1) * 16, region);     // Bottom-left
        }
        
        // Convert boundary points to ordered polygon
        return orderBoundaryPoints(boundaryPoints);
    }
    
    /**
     * Add a corner point if it's on the boundary (has at least one non-claimed neighbor)
     */
    private void addCornerIfBoundary(Set<Point> boundaryPoints, int worldX, int worldZ, Set<ChunkPos> region) {
        // Convert world coordinates back to chunk coordinates
        int chunkX = worldX / 16;
        int chunkZ = worldZ / 16;
        
        // Check the 4 chunks that share this corner
        ChunkPos[] cornerChunks = {
            new ChunkPos(chunkX - 1, chunkZ - 1),  // Top-left chunk
            new ChunkPos(chunkX, chunkZ - 1),      // Top-right chunk
            new ChunkPos(chunkX, chunkZ),          // Bottom-right chunk
            new ChunkPos(chunkX - 1, chunkZ)       // Bottom-left chunk
        };
        
        // Count how many of these chunks are claimed
        int claimedCount = 0;
        for (ChunkPos chunk : cornerChunks) {
            if (region.contains(chunk)) {
                claimedCount++;
            }
        }
        
        // Add corner if it's on the boundary (not all 4 chunks claimed, but at least 1 claimed)
        if (claimedCount > 0 && claimedCount < 4) {
            boundaryPoints.add(new Point(worldX, worldZ));
        }
    }
    
    /**
     * Order boundary points into a proper polygon
     */
    private List<BlockPos> orderBoundaryPoints(Set<Point> points) {
        if (points.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Point> pointList = new ArrayList<>(points);
        
        // Find the centroid
        double centerX = pointList.stream().mapToInt(p -> p.x).average().orElse(0);
        double centerZ = pointList.stream().mapToInt(p -> p.z).average().orElse(0);
        
        // Sort points by angle from centroid (creates clockwise ordering)
        pointList.sort((a, b) -> {
            double angleA = Math.atan2(a.z - centerZ, a.x - centerX);
            double angleB = Math.atan2(b.z - centerZ, b.x - centerX);
            return Double.compare(angleA, angleB);
        });
        
        // Convert to BlockPos and close the polygon
        List<BlockPos> polygon = new ArrayList<>();
        for (Point p : pointList) {
            polygon.add(new BlockPos(p.x, 64, p.z));
        }
        
        // Close the polygon
        if (!polygon.isEmpty()) {
            polygon.add(polygon.get(0));
        }
        
        JourneyFactions.LOGGER.info("Created ordered polygon with {} points", polygon.size());
        return polygon;
    }
    
    /**
     * Simple point class for boundary tracing
     */
    private static class Point {
        final int x, z;
        
        Point(int x, int z) {
            this.x = x;
            this.z = z;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Point point = (Point) obj;
            return x == point.x && z == point.z;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
        
        @Override
        public String toString() {
            return String.format("Point(%d,%d)", x, z);
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
     * Build the boundary polygon for a connected region of chunks
     */
    private List<BlockPos> buildRegionBoundary(Set<ChunkPos> region) {
        if (region.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (region.size() == 1) {
            // Single chunk - create rectangle
            ChunkPos chunk = region.iterator().next();
            return createChunkRectangle(chunk);
        } else if (region.size() <= 4 && isRectangularRegion(region)) {
            // Small rectangular regions - create precise rectangle
            return createPreciseRectangle(region);
        } else {
            // Complex regions - create bounding rectangle
            return createBoundingRectangle(region);
        }
    }
    
    /**
     * Check if a region forms a perfect rectangle
     */
    private boolean isRectangularRegion(Set<ChunkPos> region) {
        if (region.size() <= 1) return true;
        
        int minX = region.stream().mapToInt(c -> c.x).min().orElse(0);
        int maxX = region.stream().mapToInt(c -> c.x).max().orElse(0);
        int minZ = region.stream().mapToInt(c -> c.z).min().orElse(0);
        int maxZ = region.stream().mapToInt(c -> c.z).max().orElse(0);
        
        int expectedSize = (maxX - minX + 1) * (maxZ - minZ + 1);
        
        // Check if all expected chunks are present
        if (region.size() != expectedSize) {
            return false;
        }
        
        // Verify all chunks in the rectangle exist
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!region.contains(new ChunkPos(x, z))) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Create a precise rectangle for a perfectly rectangular region
     */
    private List<BlockPos> createPreciseRectangle(Set<ChunkPos> region) {
        int minX = region.stream().mapToInt(c -> c.x).min().orElse(0);
        int maxX = region.stream().mapToInt(c -> c.x).max().orElse(0);
        int minZ = region.stream().mapToInt(c -> c.z).min().orElse(0);
        int maxZ = region.stream().mapToInt(c -> c.z).max().orElse(0);
        
        return createBoundingRectangleFromBounds(minX, maxX, minZ, maxZ);
    }
    
    /**
     * Create a rectangle for a single chunk
     */
    private List<BlockPos> createChunkRectangle(ChunkPos chunk) {
        List<BlockPos> points = new ArrayList<>();
        int worldX = chunk.x * 16;
        int worldZ = chunk.z * 16;
        
        // Create rectangle (clockwise)
        points.add(new BlockPos(worldX, 64, worldZ));           // Top-left
        points.add(new BlockPos(worldX + 16, 64, worldZ));      // Top-right
        points.add(new BlockPos(worldX + 16, 64, worldZ + 16)); // Bottom-right
        points.add(new BlockPos(worldX, 64, worldZ + 16));      // Bottom-left
        points.add(new BlockPos(worldX, 64, worldZ));           // Close polygon
        
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
        
        return createBoundingRectangleFromBounds(minX, maxX, minZ, maxZ);
    }
    
    /**
     * Helper method to create rectangle from chunk bounds
     */
    private List<BlockPos> createBoundingRectangleFromBounds(int minX, int maxX, int minZ, int maxZ) {
        List<BlockPos> points = new ArrayList<>();
        
        // Convert to world coordinates
        int worldMinX = minX * 16;
        int worldMaxX = (maxX + 1) * 16;
        int worldMinZ = minZ * 16;
        int worldMaxZ = (maxZ + 1) * 16;
        
        // Create bounding rectangle (clockwise)
        points.add(new BlockPos(worldMinX, 64, worldMinZ));     // Top-left
        points.add(new BlockPos(worldMaxX, 64, worldMinZ));     // Top-right
        points.add(new BlockPos(worldMaxX, 64, worldMaxZ));     // Bottom-right
        points.add(new BlockPos(worldMinX, 64, worldMaxZ));     // Bottom-left
        points.add(new BlockPos(worldMinX, 64, worldMinZ));     // Close polygon
        
        JourneyFactions.LOGGER.info("Created rectangle: ({},{}) to ({},{}) covering {}x{} chunks", 
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