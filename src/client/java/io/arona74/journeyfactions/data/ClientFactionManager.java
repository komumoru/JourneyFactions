package io.arona74.journeyfactions.data;

import io.arona74.journeyfactions.JourneyFactions;
import net.minecraft.util.math.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages faction data on the client side
 */
public class ClientFactionManager {
    private final Map<String, ClientFaction> factions = new ConcurrentHashMap<>();
    private final Map<ChunkPos, String> chunkToFaction = new ConcurrentHashMap<>();
    private final Set<FactionUpdateListener> listeners = new HashSet<>();
    
    // Special faction IDs
    public static final String WILDERNESS_ID = "wilderness";
    public static final String SAFEZONE_ID = "safezone";
    public static final String WARZONE_ID = "warzone";

    public ClientFactionManager() {
        // Initialize default factions
        initializeDefaultFactions();
    }

    private void initializeDefaultFactions() {
        // Create wilderness faction
        ClientFaction wilderness = new ClientFaction(WILDERNESS_ID, "Wilderness");
        wilderness.setType(ClientFaction.FactionType.WILDERNESS);
        wilderness.setDisplayName("Wilderness");
        factions.put(WILDERNESS_ID, wilderness);

        // Create safezone faction  
        ClientFaction safezone = new ClientFaction(SAFEZONE_ID, "SafeZone");
        safezone.setType(ClientFaction.FactionType.SAFEZONE);
        safezone.setDisplayName("Safe Zone");
        factions.put(SAFEZONE_ID, safezone);

        // Create warzone faction
        ClientFaction warzone = new ClientFaction(WARZONE_ID, "WarZone");
        warzone.setType(ClientFaction.FactionType.WARZONE);
        warzone.setDisplayName("War Zone");
        factions.put(WARZONE_ID, warzone);
    }

    // Faction management
    public void addOrUpdateFaction(ClientFaction faction) {
        String factionId = faction.getId();
        ClientFaction existing = factions.get(factionId);
        
        if (existing != null) {
            // Update existing faction
            existing.setName(faction.getName());
            existing.setDisplayName(faction.getDisplayName());
            existing.setColor(faction.getColor());
            existing.setType(faction.getType());
            existing.setClaimedChunks(faction.getClaimedChunks());
            
            // Update chunk mapping
            updateChunkMapping(existing);
            
            JourneyFactions.LOGGER.debug("Updated faction: {}", factionId);
        } else {
            // Add new faction
            factions.put(factionId, faction);
            updateChunkMapping(faction);
            
            JourneyFactions.LOGGER.info("Added new faction: {} ({})", faction.getName(), factionId);
        }

        // Notify listeners
        notifyFactionUpdated(faction);
    }

    public void removeFaction(String factionId) {
        ClientFaction faction = factions.remove(factionId);
        if (faction != null) {
            // Remove chunk mappings
            faction.getClaimedChunks().forEach(chunkToFaction::remove);
            
            JourneyFactions.LOGGER.info("Removed faction: {}", factionId);
            notifyFactionRemoved(faction);
        }
    }

    public ClientFaction getFaction(String factionId) {
        return factions.get(factionId);
    }

    public Collection<ClientFaction> getAllFactions() {
        return new ArrayList<>(factions.values());
    }

    public Collection<ClientFaction> getPlayerFactions() {
        return factions.values().stream()
                .filter(f -> f.getType() == ClientFaction.FactionType.PLAYER)
                .toList();
    }

    // Chunk-based queries
    public ClientFaction getFactionAt(ChunkPos chunk) {
        String factionId = chunkToFaction.get(chunk);
        return factionId != null ? factions.get(factionId) : getFaction(WILDERNESS_ID);
    }

    public void setChunkOwner(ChunkPos chunk, String factionId) {
        // Remove from previous owner
        String previousFactionId = chunkToFaction.get(chunk);
        if (previousFactionId != null) {
            ClientFaction previousFaction = factions.get(previousFactionId);
            if (previousFaction != null) {
                previousFaction.removeClaimedChunk(chunk);
                notifyFactionUpdated(previousFaction);
            }
        }

        // Add to new owner
        if (factionId != null && !factionId.equals(WILDERNESS_ID)) {
            chunkToFaction.put(chunk, factionId);
            ClientFaction newFaction = factions.get(factionId);
            if (newFaction != null) {
                newFaction.addClaimedChunk(chunk);
                notifyFactionUpdated(newFaction);
            }
        } else {
            chunkToFaction.remove(chunk);
        }

        // Notify about chunk change
        notifyChunkChanged(chunk, previousFactionId, factionId);
    }

    private void updateChunkMapping(ClientFaction faction) {
        // Remove old mappings for this faction
        chunkToFaction.entrySet().removeIf(entry -> 
            entry.getValue().equals(faction.getId()));
        
        // Add new mappings
        for (ChunkPos chunk : faction.getClaimedChunks()) {
            chunkToFaction.put(chunk, faction.getId());
        }
    }

    // Data management
    public void clear() {
        factions.clear();
        chunkToFaction.clear();
        initializeDefaultFactions();
        
        JourneyFactions.LOGGER.info("Cleared all faction data");
        notifyDataCleared();
    }

    public void cleanup() {
        clear();
        listeners.clear();
    }

    // Statistics
    public int getFactionCount() {
        return (int) factions.values().stream()
                .filter(f -> f.getType() == ClientFaction.FactionType.PLAYER)
                .count();
    }

    public int getTotalClaimedChunks() {
        return factions.values().stream()
                .mapToInt(ClientFaction::getClaimedChunkCount)
                .sum();
    }

    // Event system
    public interface FactionUpdateListener {
        void onFactionUpdated(ClientFaction faction);
        void onFactionRemoved(ClientFaction faction);
        void onChunkChanged(ChunkPos chunk, String oldFactionId, String newFactionId);
        void onDataCleared();
    }

    public void addListener(FactionUpdateListener listener) {
        listeners.add(listener);
    }

    public void removeListener(FactionUpdateListener listener) {
        listeners.remove(listener);
    }

    private void notifyFactionUpdated(ClientFaction faction) {
        listeners.forEach(listener -> {
            try {
                listener.onFactionUpdated(faction);
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Error notifying faction update listener", e);
            }
        });
    }

    private void notifyFactionRemoved(ClientFaction faction) {
        listeners.forEach(listener -> {
            try {
                listener.onFactionRemoved(faction);
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Error notifying faction removal listener", e);
            }
        });
    }

    private void notifyChunkChanged(ChunkPos chunk, String oldFactionId, String newFactionId) {
        listeners.forEach(listener -> {
            try {
                listener.onChunkChanged(chunk, oldFactionId, newFactionId);
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Error notifying chunk change listener", e);
            }
        });
    }

    private void notifyDataCleared() {
        listeners.forEach(listener -> {
            try {
                listener.onDataCleared();
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Error notifying data clear listener", e);
            }
        });
    }
}