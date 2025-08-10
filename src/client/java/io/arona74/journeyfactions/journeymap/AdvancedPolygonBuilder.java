package io.arona74.journeyfactions.journeymap;

import io.arona74.journeyfactions.JourneyFactions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

/**
 * Advanced polygon builder that creates precise boundaries for faction territories
 * by tracing the actual edges of claimed chunks rather than using bounding rectangles.
 */
public class AdvancedPolygonBuilder {
    
    /**
     * Build precise polygons for all connected regions in a chunk set
     */
    public static List<List<BlockPos>> buildPrecisePolygons(Set<ChunkPos> chunks) {
        List<List<BlockPos>> polygons = new ArrayList<>();
        
        // Find connected regions
        List<Set<ChunkPos>> regions = findConnectedRegions(chunks);
        
        for (Set<ChunkPos> region : regions) {
            List<BlockPos> boundary = traceBoundary(region);
            if (!boundary.isEmpty()) {
                polygons.add(boundary);
            }
        }
        
        return polygons;
    }
    
    /**
     * Find connected regions of chunks using flood fill
     */
    private static List<Set<ChunkPos>> findConnectedRegions(Set<ChunkPos> chunks) {
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
    
    private static void floodFill(ChunkPos start, Set<ChunkPos> allChunks, Set<ChunkPos> visited, Set<ChunkPos> region) {
        if (visited.contains(start) || !allChunks.contains(start)) {
            return;
        }
        
        visited.add(start);
        region.add(start);
        
        // Check 4 adjacent chunks
        ChunkPos[] neighbors = {
            new ChunkPos(start.x + 1, start.z),
            new ChunkPos(start.x - 1, start.z),
            new ChunkPos(start.x, start.z + 1),
            new ChunkPos(start.x, start.z - 1)
        };
        
        for (ChunkPos neighbor : neighbors) {
            floodFill(neighbor, allChunks, visited, region);
        }
    }
    
    /**
     * Trace the precise boundary of a connected region using edge following
     */
    private static List<BlockPos> traceBoundary(Set<ChunkPos> region) {
        if (region.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            JourneyFactions.LOGGER.info("Tracing boundary for region with {} chunks", region.size());
            
            // Create a grid representation for easier boundary tracing
            Map<Point, Boolean> grid = createGrid(region);
            
            // Find the leftmost, topmost cell as starting point
            Point start = findStartPoint(grid);
            if (start == null) {
                JourneyFactions.LOGGER.warn("Could not find start point for boundary tracing");
                return createFallbackBoundary(region);
            }
            
            // Trace the boundary using Moore neighborhood tracing
            List<Point> boundaryPoints = mooreBoundaryTrace(grid, start);
            
            // Convert grid points to world coordinates
            List<BlockPos> worldBoundary = convertToWorldCoordinates(boundaryPoints);
            
            JourneyFactions.LOGGER.info("Traced boundary with {} points", worldBoundary.size());
            return worldBoundary;
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error tracing boundary, falling back to simple method", e);
            return createFallbackBoundary(region);
        }
    }
    
    /**
     * Create a grid representation where each chunk becomes multiple grid cells
     * This allows for more precise boundary tracing
     */
    private static Map<Point, Boolean> createGrid(Set<ChunkPos> region) {
        Map<Point, Boolean> grid = new HashMap<>();
        
        // Each chunk becomes a 4x4 grid for smoother boundaries
        for (ChunkPos chunk : region) {
            for (int dx = 0; dx < 4; dx++) {
                for (int dz = 0; dz < 4; dz++) {
                    Point gridPoint = new Point(chunk.x * 4 + dx, chunk.z * 4 + dz);
                    grid.put(gridPoint, true);
                }
            }
        }
        
        return grid;
    }
    
    private static Point findStartPoint(Map<Point, Boolean> grid) {
        return grid.keySet().stream()
            .min(Comparator.comparingInt((Point p) -> p.x).thenComparing(p -> p.z))
            .orElse(null);
    }
    
    /**
     * Moore boundary tracing algorithm
     */
    private static List<Point> mooreBoundaryTrace(Map<Point, Boolean> grid, Point start) {
        List<Point> boundary = new ArrayList<>();
        
        // Directions: N, NE, E, SE, S, SW, W, NW
        int[] dx = {0, 1, 1, 1, 0, -1, -1, -1};
        int[] dz = {-1, -1, 0, 1, 1, 1, 0, -1};
        
        Point current = start;
        int direction = 0; // Start facing north
        
        do {
            boundary.add(new Point(current.x, current.z));
            
            // Find next boundary point
            boolean found = false;
            int searchDir = (direction + 6) % 8; // Start searching 2 steps counter-clockwise
            
            for (int i = 0; i < 8; i++) {
                int checkDir = (searchDir + i) % 8;
                Point next = new Point(current.x + dx[checkDir], current.z + dz[checkDir]);
                
                if (grid.containsKey(next)) {
                    current = next;
                    direction = checkDir;
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                break;
            }
            
            // Prevent infinite loops
            if (boundary.size() > 10000) {
                JourneyFactions.LOGGER.warn("Boundary trace exceeded maximum points, stopping");
                break;
            }
            
        } while (!current.equals(start) || boundary.size() < 2);
        
        return boundary;
    }
    
    /**
     * Convert grid coordinates back to world block coordinates
     */
    private static List<BlockPos> convertToWorldCoordinates(List<Point> gridPoints) {
        List<BlockPos> worldPoints = new ArrayList<>();
        
        for (Point gridPoint : gridPoints) {
            // Convert grid coordinates back to world coordinates
            // Each grid cell represents 4x4 blocks within a chunk
            int worldX = (gridPoint.x / 4) * 16 + (gridPoint.x % 4) * 4;
            int worldZ = (gridPoint.z / 4) * 16 + (gridPoint.z % 4) * 4;
            
            worldPoints.add(new BlockPos(worldX, 64, worldZ));
        }
        
        // Simplify the polygon to reduce point count
        return simplifyPolygon(worldPoints);
    }
    
    /**
     * Simplify polygon using Douglas-Peucker algorithm
     */
    private static List<BlockPos> simplifyPolygon(List<BlockPos> points) {
        if (points.size() <= 3) {
            return points;
        }
        
        // Simple simplification: remove points that are very close to their neighbors
        List<BlockPos> simplified = new ArrayList<>();
        simplified.add(points.get(0));
        
        for (int i = 1; i < points.size() - 1; i++) {
            BlockPos prev = points.get(i - 1);
            BlockPos curr = points.get(i);
            BlockPos next = points.get(i + 1);
            
            // Calculate distances
            double dist1 = distance(prev, curr);
            double dist2 = distance(curr, next);
            
            // Keep point if it's not too close to neighbors or if it creates a significant angle
            if (dist1 > 8 || dist2 > 8 || !isCollinear(prev, curr, next)) {
                simplified.add(curr);
            }
        }
        
        simplified.add(points.get(points.size() - 1));
        return simplified;
    }
    
    private static double distance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    private static boolean isCollinear(BlockPos a, BlockPos b, BlockPos c) {
        // Check if three points are roughly collinear
        double area = Math.abs((a.getX() * (b.getZ() - c.getZ()) + 
                               b.getX() * (c.getZ() - a.getZ()) + 
                               c.getX() * (a.getZ() - b.getZ())) / 2.0);
        return area < 1.0; // Tolerance for "roughly" collinear
    }
    
    /**
     * Fallback method that creates a simple bounding rectangle
     */
    private static List<BlockPos> createFallbackBoundary(Set<ChunkPos> region) {
        List<BlockPos> points = new ArrayList<>();
        
        if (region.isEmpty()) {
            return points;
        }
        
        // Find bounds
        int minX = region.stream().mapToInt(c -> c.x).min().orElse(0);
        int maxX = region.stream().mapToInt(c -> c.x).max().orElse(0);
        int minZ = region.stream().mapToInt(c -> c.z).min().orElse(0);
        int maxZ = region.stream().mapToInt(c -> c.z).max().orElse(0);
        
        // Convert to world coordinates
        int worldMinX = minX * 16;
        int worldMaxX = (maxX + 1) * 16;
        int worldMinZ = minZ * 16;
        int worldMaxZ = (maxZ + 1) * 16;
        
        // Create bounding rectangle
        points.add(new BlockPos(worldMinX, 64, worldMinZ));
        points.add(new BlockPos(worldMaxX, 64, worldMinZ));
        points.add(new BlockPos(worldMaxX, 64, worldMaxZ));
        points.add(new BlockPos(worldMinX, 64, worldMaxZ));
        points.add(new BlockPos(worldMinX, 64, worldMinZ)); // Close polygon
        
        JourneyFactions.LOGGER.info("Created fallback bounding rectangle for {} chunks", region.size());
        return points;
    }
    
    /**
     * Simple point class for grid coordinates
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
    }
}