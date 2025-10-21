package io.arona74.journeyfactions.journeymap;

import io.arona74.journeyfactions.JourneyFactions;
import io.arona74.journeyfactions.config.JourneyFactionsConfig;
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

import java.awt.Color;
import java.util.*;

public class FactionOverlayManager implements ClientFactionManager.FactionUpdateListener, ClientFactionManager.ChunkDiscoveryListener {
    
    private final IClientAPI jmAPI;
    private final Map<String, PolygonOverlay> factionOverlays;
    private static final int LABEL_Y = 70;

    private PolygonOverlay createLabelOnlyOverlay(
            String overlayId,
            RegistryKey<World> worldKey,
            BlockPos anchor,
            TextProperties textProps,
            String label
    ) {
        // 2x2 block square around the anchor
        int r = 1;
        List<BlockPos> pts = Arrays.asList(
            new BlockPos(anchor.getX() - r, LABEL_Y, anchor.getZ() - r),
            new BlockPos(anchor.getX() + r, LABEL_Y, anchor.getZ() - r),
            new BlockPos(anchor.getX() + r, LABEL_Y, anchor.getZ() + r),
            new BlockPos(anchor.getX() - r, LABEL_Y, anchor.getZ() + r),
            new BlockPos(anchor.getX() - r, LABEL_Y, anchor.getZ() - r) // close
        );

        MapPolygon tiny = new MapPolygon(pts);

        ShapeProperties invisible = new ShapeProperties()
            .setStrokeWidth(0f)
            .setStrokeOpacity(0f)
            .setFillOpacity(0f)
            .setStrokeColor(0)
            .setFillColor(0);

        PolygonOverlay labelOverlay = new PolygonOverlay(
            JourneyFactions.MOD_ID,
            overlayId,
            worldKey,
            invisible,
            tiny
        );

        labelOverlay.setActiveUIs(EnumSet.of(Context.UI.Any));
        labelOverlay.setActiveMapTypes(EnumSet.of(Context.MapType.Any));
        labelOverlay.setOverlayGroupName("faction_labels");
        labelOverlay.setTitle(label);
        labelOverlay.setTextProperties(textProps);
        labelOverlay.setLabel(label);
        return labelOverlay;
    }

    private BlockPos computeInteriorLabelAnchor(Set<ChunkPos> region) {
        if (region.isEmpty()) return null;

        // Edge detection: any missing 4-neighbor => edge
        Deque<ChunkPos> q = new ArrayDeque<>();
        Map<ChunkPos, Integer> dist = new HashMap<>(region.size() * 2);
        for (ChunkPos c : region) {
            if (isEdgeChunk(c, region)) {
                dist.put(c, 0);
                q.add(c);
            }
        }
        // Single chunk or fully solid region with no detected edge: just use it
        if (dist.isEmpty()) {
            ChunkPos any = region.iterator().next();
            return new BlockPos(any.x * 16 + 8, LABEL_Y, any.z * 16 + 8);
        }

        // BFS into the interior
        while (!q.isEmpty()) {
            ChunkPos cur = q.removeFirst();
            int d = dist.get(cur);
            for (ChunkPos n : neighbors(cur)) {
                if (region.contains(n) && !dist.containsKey(n)) {
                    dist.put(n, d + 1);
                    q.addLast(n);
                }
            }
        }

        // Choose the chunk with max distance from the perimeter (keeps out of holes)
        ChunkPos best = null;
        int bestD = -1;
        for (Map.Entry<ChunkPos, Integer> e : dist.entrySet()) {
            if (e.getValue() > bestD) { bestD = e.getValue(); best = e.getKey(); }
        }
        if (best == null) best = region.iterator().next();

        return new BlockPos(best.x * 16 + 8, LABEL_Y, best.z * 16 + 8);
    }

    private boolean isEdgeChunk(ChunkPos c, Set<ChunkPos> set) {
        return !(set.contains(new ChunkPos(c.x + 1, c.z)) &&
                set.contains(new ChunkPos(c.x - 1, c.z)) &&
                set.contains(new ChunkPos(c.x, c.z + 1)) &&
                set.contains(new ChunkPos(c.x, c.z - 1)));
    }

    private ChunkPos[] neighbors(ChunkPos c) {
        return new ChunkPos[] {
            new ChunkPos(c.x + 1, c.z),
            new ChunkPos(c.x - 1, c.z),
            new ChunkPos(c.x, c.z + 1),
            new ChunkPos(c.x, c.z - 1)
        };
    }
    
    public FactionOverlayManager(IClientAPI jmAPI) {
        this.jmAPI = jmAPI;
        this.factionOverlays = new HashMap<>();
        
        // Initialize the display manager
        FactionDisplayManager.initialize(this);
    }
    
    public void onMappingStarted() {
        JourneyFactions.debugLog("JourneyMap mapping started - loading faction overlays");
        loadAllFactionOverlays();
    }
    
    public void onMappingStopped() {
        JourneyFactions.debugLog("JourneyMap mapping stopped - clearing overlays");
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
                JourneyFactions.debugLog("Showed {} faction overlays", factionOverlays.size());
            } else {
                // Hide all overlays by removing them from JourneyMap
                for (PolygonOverlay overlay : factionOverlays.values()) {
                    jmAPI.remove(overlay);
                }
                JourneyFactions.debugLog("Hid {} faction overlays", factionOverlays.size());
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
            JourneyFactions.debugLog("Loading faction overlays...");
            Collection<ClientFaction> factions = JourneyFactions.getFactionManager().getAllFactions();
            JourneyFactions.debugLog("Found {} factions to process", factions.size());

            for (ClientFaction faction : factions) {
                JourneyFactions.debugLog("Processing faction: {} (type: {}, chunks: {})",faction.getName(), faction.getType(), faction.getClaimedChunks().size());

                // Only display factions that have claimed territory and that the player has discovered
                if (!faction.getClaimedChunks().isEmpty()) {
                    createOrUpdateFactionOverlay(faction, faction.getClaimedChunks());
                } else {
                    JourneyFactions.debugLog("Skipping faction {} - no claimed chunks", faction.getName());
                }
            }
            
        } catch (Exception e) {
            // JourneyFactions.LOGGER.error("Error loading faction overlays", e);
        }
    }
    
    private BlockPos computeHullCentroid(Set<ChunkPos> region) {
        if (region.isEmpty()) {
            return new BlockPos(0, LABEL_Y, 0);
        }

        int minChunkX = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;

        for (ChunkPos c : region) {
            if (c.x < minChunkX) minChunkX = c.x;
            if (c.x > maxChunkX) maxChunkX = c.x;
            if (c.z < minChunkZ) minChunkZ = c.z;
            if (c.z > maxChunkZ) maxChunkZ = c.z;
        }

        // Convert chunks to block coords for edges
        int minBlockX = minChunkX * 16;
        int maxBlockX = (maxChunkX * 16) + 15; // last block in chunk
        int minBlockZ = minChunkZ * 16;
        int maxBlockZ = (maxChunkZ * 16) + 15;

        // Perfect geometric center
        int centerX = (minBlockX + maxBlockX) / 2;
        int centerZ = (minBlockZ + maxBlockZ) / 2;

        return new BlockPos(centerX, LABEL_Y, centerZ);
    }



    private void createOrUpdateFactionOverlay(ClientFaction faction, Set<ChunkPos> claimedChunks) {
        if (faction == null || claimedChunks.isEmpty()) {
            return;
        }

        Set<ChunkPos> visibleChunks = JourneyFactions.getFactionManager().getDiscoveredClaims(claimedChunks);
        if (visibleChunks.isEmpty()) {
            JourneyFactions.debugLog("Skipping overlay for faction {} - no discovered chunks yet", faction.getDisplayName());
            return;
        }

        String factionId = faction.getId();
        RegistryKey<World> worldKey = World.OVERWORLD;

        JourneyFactions.debugLog("Creating overlay for faction: {} with {} discovered chunks ({} total)",
            faction.getDisplayName(), visibleChunks.size(), claimedChunks.size());

        // Sort connected regions by size so overlays match
        List<Set<ChunkPos>> regions = findConnectedRegions(visibleChunks);
        regions.sort((a, b) -> Integer.compare(b.size(), a.size()));

        JourneyFactions.debugLog("Found {} connected regions for faction {}", regions.size(), faction.getDisplayName());

        try {
            // Build polygons with holes preserved
            List<MapPolygonWithHoles> polygons = buildPolygonsUsingJourneyMapHelper(visibleChunks);
            if (polygons.isEmpty()) {
                JourneyFactions.debugLog("No polygons generated for faction {}", faction.getDisplayName());
                return;
            }
            
            JourneyFactions.debugLog("Generated {} polygons for faction {}", polygons.size(), faction.getDisplayName());
            
            for (int i = 0; i < polygons.size(); i++) {
                String overlayId = polygons.size() > 1 ? factionId + "_region_" + i : factionId;
                
                JourneyFactions.debugLog("Creating polygon overlay {} for faction {}", overlayId, faction.getDisplayName());
                
                // --- 1) Main polygon overlay ---
                PolygonOverlay overlay = new PolygonOverlay(
                    JourneyFactions.MOD_ID,
                    overlayId,
                    worldKey,
                    createShapeProperties(faction),
                    polygons.get(i)
                );
                overlay.setActiveUIs(EnumSet.of(Context.UI.Any));
                overlay.setActiveMapTypes(EnumSet.of(Context.MapType.Any));
                overlay.setTextProperties(createTextProperties(faction));
                
                if (JourneyFactions.CONFIG.separateLabelOverlay) {
                    overlay.setLabel(null); // no built-in label
                    JourneyFactions.debugLog("Using separate label overlay for {}", overlayId);
                } else {
                    overlay.setLabel(polygons.size() > 1
                        ? faction.getDisplayName() + " #" + (i + 1)
                        : faction.getDisplayName());
                    JourneyFactions.debugLog("Using built-in label for {}: {}", overlayId, overlay.getLabel());
                }
                
                overlay.setOverlayGroupName("faction_territories");
                overlay.setTitle(faction.getDisplayName() + " Territory");
                
                if (FactionDisplayManager.isFactionDisplayEnabled()) {
                    jmAPI.show(overlay);
                    JourneyFactions.debugLog("Displayed polygon overlay: {}", overlayId);
                } else {
                    JourneyFactions.debugLog("Faction display disabled - overlay {} created but not shown", overlayId);
                }
                
                factionOverlays.put(overlayId, overlay);
                
                // --- 2) Optional: separate label-only overlay ---
                if (JourneyFactions.CONFIG.separateLabelOverlay) {
                    Set<ChunkPos> region = (i < regions.size()) ? regions.get(i) : visibleChunks;
                    BlockPos anchor;
                    
                                        JourneyFactions.debugLog("Computing label anchor using mode: {}", JourneyFactions.CONFIG.labelAnchorMode);
                    
                    switch (JourneyFactions.CONFIG.labelAnchorMode) {
                        case HULL_CENTROID:
                            anchor = computeHullCentroid(region);
                            JourneyFactions.debugLog("Hull centroid anchor: {}", anchor);
                            break;
                        case FIRST_CHUNK_CENTER:
                            ChunkPos first = region.iterator().next();
                            anchor = new BlockPos(first.x * 16 + 8, LABEL_Y, first.z * 16 + 8);
                            JourneyFactions.debugLog("First chunk center anchor: {}", anchor);
                            break;
                        case FARTHEST_INTERIOR_CHUNK:
                        default:
                            anchor = computeInteriorLabelAnchor(region);
                            JourneyFactions.debugLog("Farthest interior anchor: {}", anchor);
                    }
                    
                    String labelId = overlayId + "_label";
                    PolygonOverlay labelOverlay = createLabelOnlyOverlay(
                        labelId,
                        worldKey,
                        anchor,
                        createTextProperties(faction),
                        polygons.size() > 1
                            ? faction.getDisplayName() + " #" + (i + 1)
                            : faction.getDisplayName()
                    );
                    
                    if (FactionDisplayManager.isFactionDisplayEnabled()) {
                        jmAPI.show(labelOverlay);
                        JourneyFactions.debugLog("Displayed label overlay: {}", labelId);
                    } else {
                        JourneyFactions.debugLog("Faction display disabled - label overlay {} created but not shown", labelId);
                    }
                    
                    factionOverlays.put(labelId, labelOverlay);
                }
            }
            
            JourneyFactions.debugLog("=== OVERLAY CREATION COMPLETE FOR FACTION: {} ===", faction.getDisplayName());
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error creating overlay for faction {}: {}", faction.getDisplayName(), e.getMessage(), e);
            JourneyFactions.debugLog("Exception details: {}", e.toString());
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
        
        JourneyFactions.debugLog("Created bounding rectangle: ({},{}) to ({},{}) covering {}x{} chunks",worldMinX, worldMinZ, worldMaxX, worldMaxZ, maxX - minX + 1, maxZ - minZ + 1);
        
        return points;
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
            JourneyFactions.debugLog("Faction color is BLACK");
        } else {
            // Faction color is dark_gray → use lighter text
            if (factionColor.getRed() == 85 && factionColor.getGreen() == 85 && factionColor.getBlue() == 85) {
                // Lighter text
                textColor = new Color(170, 170, 170, 128);
                // Default background
                backgroundColor = new Color(200, 200, 200, 128);
                JourneyFactions.debugLog("Faction color is DARK_GRAY");
            } else {
                // Default text
                textColor = new Color(factionColor.getRGB());
                // Default background
                backgroundColor = new Color(0, 0, 0, 128);
                JourneyFactions.debugLog("Faction color is ELSE");
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
                JourneyFactions.LOGGER.error("Failed to remove faction overlay: " + entry.getKey(), e);
            }
        }
        factionOverlays.clear();
    }
    
    // FactionUpdateListener implementation
    @Override
    public void onFactionUpdated(ClientFaction faction) {
        // Check if faction is being disbanded (has no chunks but still exists)
        if (faction.getClaimedChunks().isEmpty()) {
            JourneyFactions.debugLog("Faction {} appears to be disbanded - just cleaning overlays", faction.getName());
            completelyRemoveFactionOverlays(faction.getId());
            return;
        }
        
        // Normal update for factions with chunks
        JourneyFactions.debugLog("Faction updated: {} - doing complete refresh", faction.getName());
        completelyRefreshFaction(faction);
    }
    
    @Override
    public void onFactionRemoved(ClientFaction faction) {
        JourneyFactions.debugLog("Faction removed: {} - cleaning up all overlays", faction.getName());
        completelyRemoveFactionOverlays(faction.getId());
    }
    
    @Override
    public void onChunkChanged(ChunkPos chunk, String oldFactionId, String newFactionId) {
        // Only log chunk changes, don't automatically refresh
        // The onFactionUpdated events will handle the refreshing
        JourneyFactions.LOGGER.debug("Chunk changed: {} from {} to {} (will be handled by faction updates)",
            chunk, oldFactionId, newFactionId);
    }

    @Override
    public void onChunkDiscovered(ChunkPos chunk, ClientFaction owningFaction) {
        if (owningFaction == null) {
            return;
        }

        JourneyFactions.debugLog("Chunk {} discovered for faction {} - refreshing overlays", chunk, owningFaction.getName());
        completelyRefreshFaction(owningFaction, true);
    }

    private void completelyRemoveFactionOverlays(String factionId) {
        JourneyFactions.debugLog("=== COMPLETELY REMOVING ALL OVERLAYS FOR FACTION: {} ===", factionId);
        
        // Find ALL overlay IDs that could possibly belong to this faction
        Set<String> overlaysToRemove = new HashSet<>();
        
        for (String overlayId : factionOverlays.keySet()) {
            // Match exact faction ID or anything starting with faction ID + underscore
            if (overlayId.equals(factionId) || 
                overlayId.startsWith(factionId + "_") ||
                overlayId.contains(factionId)) {
                overlaysToRemove.add(overlayId);
            }
        }
        
        JourneyFactions.debugLog("Found {} overlays to remove: {}", overlaysToRemove.size(), overlaysToRemove);
        
        // Remove each overlay from both JourneyMap and our tracking
        for (String overlayId : overlaysToRemove) {
            PolygonOverlay overlay = factionOverlays.get(overlayId);
            if (overlay != null) {
                try {
                    // Remove from JourneyMap
                    jmAPI.remove(overlay);
                    JourneyFactions.debugLog("Removed from JourneyMap: {}", overlayId);
                } catch (Exception e) {
                    JourneyFactions.LOGGER.error("Failed to remove overlay from JourneyMap: {} - {}", overlayId, e.getMessage());
                }
                
                // Remove from our tracking
                factionOverlays.remove(overlayId);
                JourneyFactions.debugLog("Removed from tracking: {}", overlayId);
            }
        }
        
        // Double-check: force remove any remaining overlays that might have been missed
        Iterator<Map.Entry<String, PolygonOverlay>> iterator = factionOverlays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PolygonOverlay> entry = iterator.next();
            if (entry.getKey().contains(factionId)) {
                try {
                    jmAPI.remove(entry.getValue());
                    JourneyFactions.LOGGER.warn("Force removed missed overlay: {}", entry.getKey());
                } catch (Exception e) {
                    JourneyFactions.LOGGER.error("Failed to force remove overlay: {}", entry.getKey());
                }
                iterator.remove();
            }
        }
        
        JourneyFactions.debugLog("=== COMPLETE REMOVAL FINISHED FOR FACTION: {} ===", factionId);
    }

    /**
     * Complete clean and redraw for a faction
     */
    private void completelyRefreshFaction(ClientFaction faction) {
        completelyRefreshFaction(faction, false);
    }

    private void completelyRefreshFaction(ClientFaction faction, boolean skipDelay) {
        String factionId = faction.getId();
        JourneyFactions.debugLog("=== COMPLETE REFRESH STARTING FOR FACTION: {} ===", faction.getName());

        // Step 1: Nuclear removal of all overlays
        completelyRemoveFactionOverlays(factionId);

        // Step 2: Short delay to ensure cleanup is processed
        if (!skipDelay) {
            try {
                Thread.sleep(100); // 100ms delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Step 3: Only recreate if faction has chunks
        if (!faction.getClaimedChunks().isEmpty()) {
            JourneyFactions.debugLog("Recreating overlays for faction: {} with {} chunks",
                faction.getName(), faction.getClaimedChunks().size());
            createOrUpdateFactionOverlay(faction, faction.getClaimedChunks());
        } else {
            JourneyFactions.debugLog("Faction {} has no chunks, not recreating overlays", faction.getName());
        }
        
        JourneyFactions.debugLog("=== COMPLETE REFRESH FINISHED FOR FACTION: {} ===", faction.getName());
    }
    
    @Override
    public void onDataCleared() {
        JourneyFactions.debugLog("Data cleared - removing all faction overlays");
        clearAllOverlays();
    }
}