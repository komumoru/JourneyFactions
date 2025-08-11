package io.arona74.journeyfactions.journeymap;

import io.arona74.journeyfactions.JourneyFactions;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.Context;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.ShapeProperties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Debug methods to test different JourneyMap overlay configurations
 */
public class JourneyMapDebugTests {
    
    /**
     * Test 1: Create the absolute simplest possible overlay
     */
    public static void testSimpleRectangle(IClientAPI jmAPI) {
        try {
            // Create a simple 16x16 rectangle at spawn
            List<BlockPos> points = new ArrayList<>();
            points.add(new BlockPos(0, 70, 0));
            points.add(new BlockPos(16, 70, 0));
            points.add(new BlockPos(16, 70, 16));
            points.add(new BlockPos(0, 70, 16));
            points.add(new BlockPos(0, 70, 0)); // Close polygon
            
            MapPolygon polygon = new MapPolygon(points);
            
            ShapeProperties shape = new ShapeProperties()
                .setStrokeColor(Color.RED.getRGB())
                .setFillColor(Color.RED.getRGB())
                .setStrokeWidth(1.0f)
                .setFillOpacity(0.1f)
                .setStrokeOpacity(1.0f);
            
            PolygonOverlay overlay = new PolygonOverlay(
                "journeyfactions",
                "debug_simple",
                World.OVERWORLD,
                shape,
                polygon
            );
            
            overlay.setActiveUIs(EnumSet.of(Context.UI.Any));
            overlay.setActiveMapTypes(EnumSet.of(Context.MapType.Any));
            // NO LABEL - test if triangles still appear
            
            jmAPI.show(overlay);
            JourneyFactions.LOGGER.info("Created simple test rectangle");
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Failed to create simple test", e);
        }
    }
    
    /**
     * Test 2: Same rectangle but with label
     */
    public static void testRectangleWithLabel(IClientAPI jmAPI) {
        try {
            List<BlockPos> points = new ArrayList<>();
            points.add(new BlockPos(32, 70, 0));
            points.add(new BlockPos(48, 70, 0));
            points.add(new BlockPos(48, 70, 16));
            points.add(new BlockPos(32, 70, 16));
            points.add(new BlockPos(32, 70, 0));
            
            MapPolygon polygon = new MapPolygon(points);
            
            ShapeProperties shape = new ShapeProperties()
                .setStrokeColor(Color.BLUE.getRGB())
                .setFillColor(Color.BLUE.getRGB())
                .setStrokeWidth(1.0f)
                .setFillOpacity(0.1f)
                .setStrokeOpacity(1.0f);
            
            PolygonOverlay overlay = new PolygonOverlay(
                "journeyfactions",
                "debug_labeled",
                World.OVERWORLD,
                shape,
                polygon
            );
            
            overlay.setActiveUIs(EnumSet.of(Context.UI.Any));
            overlay.setActiveMapTypes(EnumSet.of(Context.MapType.Any));
            overlay.setLabel("TEST LABEL"); // Add label to see if this causes triangles
            
            jmAPI.show(overlay);
            JourneyFactions.LOGGER.info("Created labeled test rectangle");
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Failed to create labeled test", e);
        }
    }
    
    /**
     * Test 3: Different Y levels
     */
    public static void testDifferentYLevels(IClientAPI jmAPI) {
        int[] yLevels = {64, 70, 80, 100, 200};
        
        for (int i = 0; i < yLevels.length; i++) {
            try {
                List<BlockPos> points = new ArrayList<>();
                int x = i * 32; // Offset each test
                int y = yLevels[i];
                
                points.add(new BlockPos(x, y, 32));
                points.add(new BlockPos(x + 16, y, 32));
                points.add(new BlockPos(x + 16, y, 48));
                points.add(new BlockPos(x, y, 48));
                points.add(new BlockPos(x, y, 32));
                
                MapPolygon polygon = new MapPolygon(points);
                
                ShapeProperties shape = new ShapeProperties()
                    .setStrokeColor(Color.GREEN.getRGB())
                    .setFillColor(Color.GREEN.getRGB())
                    .setStrokeWidth(1.0f)
                    .setFillOpacity(0.1f)
                    .setStrokeOpacity(1.0f);
                
                PolygonOverlay overlay = new PolygonOverlay(
                    "journeyfactions",
                    "debug_y" + y,
                    World.OVERWORLD,
                    shape,
                    polygon
                );
                
                overlay.setActiveUIs(EnumSet.of(Context.UI.Any));
                overlay.setActiveMapTypes(EnumSet.of(Context.MapType.Any));
                overlay.setLabel("Y=" + y);
                
                jmAPI.show(overlay);
                JourneyFactions.LOGGER.info("Created test rectangle at Y={}", y);
                
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Failed to create Y level test", e);
            }
        }
    }
    
    /**
     * Test 4: Different stroke widths
     */
    public static void testStrokeWidths(IClientAPI jmAPI) {
        float[] widths = {0.5f, 1.0f, 1.5f, 2.0f, 3.0f};
        
        for (int i = 0; i < widths.length; i++) {
            try {
                List<BlockPos> points = new ArrayList<>();
                int z = i * 32 + 64; // Offset each test
                float width = widths[i];
                
                points.add(new BlockPos(0, 70, z));
                points.add(new BlockPos(16, 70, z));
                points.add(new BlockPos(16, 70, z + 16));
                points.add(new BlockPos(0, 70, z + 16));
                points.add(new BlockPos(0, 70, z));
                
                MapPolygon polygon = new MapPolygon(points);
                
                ShapeProperties shape = new ShapeProperties()
                    .setStrokeColor(Color.ORANGE.getRGB())
                    .setFillColor(Color.ORANGE.getRGB())
                    .setStrokeWidth(width)
                    .setFillOpacity(0.1f)
                    .setStrokeOpacity(1.0f);
                
                PolygonOverlay overlay = new PolygonOverlay(
                    "journeyfactions",
                    "debug_stroke" + width,
                    World.OVERWORLD,
                    shape,
                    polygon
                );
                
                overlay.setActiveUIs(EnumSet.of(Context.UI.Any));
                overlay.setActiveMapTypes(EnumSet.of(Context.MapType.Any));
                overlay.setLabel("W=" + width);
                
                jmAPI.show(overlay);
                JourneyFactions.LOGGER.info("Created test rectangle with stroke width={}", width);
                
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Failed to create stroke width test", e);
            }
        }
    }
    
    /**
     * Call this method to run all debug tests
     */
    public static void runAllDebugTests(IClientAPI jmAPI) {
        JourneyFactions.LOGGER.info("Running JourneyMap debug tests...");
        
        testSimpleRectangle(jmAPI);
        testRectangleWithLabel(jmAPI);
        testDifferentYLevels(jmAPI);
        testStrokeWidths(jmAPI);
        
        JourneyFactions.LOGGER.info("Debug tests completed - check the map for results");
    }
}