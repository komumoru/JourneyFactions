package io.arona74.journeyfactions.journeymap;

/**
 * Configuration settings for polygon generation behavior
 */
public class PolygonConfig {
    
    // Enable/disable advanced polygon builder
    public static final boolean USE_ADVANCED_POLYGONS = true;
    
    // Minimum chunks required to use advanced polygon builder
    public static final int MIN_CHUNKS_FOR_ADVANCED = 2;
    
    // Maximum chunks before falling back to bounding rectangle (performance)
    public static final int MAX_CHUNKS_FOR_PRECISE = 50;
    
    // Enable perfect rectangle detection for simple shapes
    public static final boolean DETECT_RECTANGLES = true;
    
    // Maximum chunks for rectangle detection
    public static final int MAX_CHUNKS_FOR_RECTANGLE_DETECTION = 20;
    
    // Polygon simplification settings
    public static final boolean SIMPLIFY_POLYGONS = true;
    public static final double SIMPLIFICATION_TOLERANCE = 8.0; // blocks
    
    // Debug settings
    public static final boolean LOG_POLYGON_DETAILS = false;
    public static final boolean SHOW_ALL_FACTION_TYPES = true; // Show wilderness, safezone, etc.
    
    /**
     * Determine which polygon method to use based on chunk count and configuration
     */
    public static PolygonMethod getPolygonMethod(int chunkCount) {
        if (!USE_ADVANCED_POLYGONS) {
            return PolygonMethod.SIMPLE;
        }
        
        if (chunkCount < MIN_CHUNKS_FOR_ADVANCED) {
            return PolygonMethod.SIMPLE;
        }
        
        if (chunkCount > MAX_CHUNKS_FOR_PRECISE) {
            return PolygonMethod.BOUNDING_RECTANGLE;
        }
        
        if (DETECT_RECTANGLES && chunkCount <= MAX_CHUNKS_FOR_RECTANGLE_DETECTION) {
            return PolygonMethod.RECTANGLE_DETECTION;
        }
        
        return PolygonMethod.ADVANCED;
    }
    
    public enum PolygonMethod {
        SIMPLE,                 // Basic single chunk or bounding rectangle
        RECTANGLE_DETECTION,    // Check for perfect rectangles first
        ADVANCED,              // Use precise boundary tracing
        BOUNDING_RECTANGLE     // Always use bounding rectangle (fast)
    }
}