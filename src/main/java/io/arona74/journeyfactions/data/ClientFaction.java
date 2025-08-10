package io.arona74.journeyfactions.data;

import net.minecraft.util.math.ChunkPos;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side representation of a faction with minimal data needed for map display
 */
public class ClientFaction {
    private final String id;
    private String name;
    private String displayName;
    private final Set<ChunkPos> claimedChunks;
    private Color color;
    private FactionType type;
    private long lastUpdated;

    public enum FactionType {
        PLAYER,      // Regular player faction
        WILDERNESS,  // Unclaimed territory
        SAFEZONE,    // Safe zone
        WARZONE      // War zone
    }

    public ClientFaction(String id, String name) {
        this.id = id;
        this.name = name;
        this.displayName = name;
        this.claimedChunks = new HashSet<>();
        this.color = generateColorFromName(name);
        this.type = FactionType.PLAYER;
        this.lastUpdated = System.currentTimeMillis();
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public Set<ChunkPos> getClaimedChunks() { return new HashSet<>(claimedChunks); }
    public Color getColor() { return color; }
    public FactionType getType() { return type; }
    public long getLastUpdated() { return lastUpdated; }

    // Setters
    public void setName(String name) {
        this.name = name;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void setColor(Color color) {
        this.color = color;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void setType(FactionType type) {
        this.type = type;
        this.lastUpdated = System.currentTimeMillis();
    }

    // Chunk management
    public void addClaimedChunk(ChunkPos chunk) {
        claimedChunks.add(chunk);
        this.lastUpdated = System.currentTimeMillis();
    }

    public void removeClaimedChunk(ChunkPos chunk) {
        claimedChunks.remove(chunk);
        this.lastUpdated = System.currentTimeMillis();
    }

    public void setClaimedChunks(Set<ChunkPos> chunks) {
        this.claimedChunks.clear();
        this.claimedChunks.addAll(chunks);
        this.lastUpdated = System.currentTimeMillis();
    }

    public boolean hasChunk(ChunkPos chunk) {
        return claimedChunks.contains(chunk);
    }

    public int getClaimedChunkCount() {
        return claimedChunks.size();
    }

    // Utility methods
    private Color generateColorFromName(String name) {
        // Generate a consistent color based on faction name
        int hash = name.hashCode();
        float hue = Math.abs(hash % 360) / 360.0f;
        float saturation = 0.7f + (Math.abs(hash >> 8) % 30) / 100.0f; // 0.7-1.0
        float brightness = 0.8f + (Math.abs(hash >> 16) % 20) / 100.0f; // 0.8-1.0
        
        return Color.getHSBColor(hue, saturation, brightness);
    }

    public boolean isEmpty() {
        return claimedChunks.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ClientFaction that = (ClientFaction) obj;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ClientFaction{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", chunks=" + claimedChunks.size() +
                ", type=" + type +
                '}';
    }
}