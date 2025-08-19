package io.arona74.journeyfactions.config;

public final class JourneyFactionsConfig {
    private JourneyFactionsConfig() {}

    /** Put the faction name on a separate, label-only overlay. */
    public static boolean separateLabelOverlay = true;

    /** Where to anchor the label inside a region. */
    public static LabelAnchorMode labelAnchorMode = LabelAnchorMode.HULL_CENTROID;

    public enum LabelAnchorMode {
        /** BFS from perimeter to find the chunk farthest from any edge/hole. */
        FARTHEST_INTERIOR_CHUNK,
        /** Area-weighted hull centroid (may fall in a hole for weird shapes). */
        HULL_CENTROID,
        /** Fallback: first claimed chunk center. */
        FIRST_CHUNK_CENTER
    }
}
