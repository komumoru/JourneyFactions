package io.arona74.journeyfactions.network;

import io.arona74.journeyfactions.JourneyFactions;
import io.arona74.journeyfactions.data.ClientFaction;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles network communication from server-side factions mod
 */
public class ClientNetworkHandler {
    
    // Packet identifiers - these should match your server-side factions mod
    public static final Identifier FACTION_DATA_SYNC = new Identifier("factions", "faction_data_sync");
    public static final Identifier FACTION_UPDATE = new Identifier("factions", "faction_update");
    public static final Identifier CHUNK_CLAIM = new Identifier("factions", "chunk_claim");
    public static final Identifier CHUNK_UNCLAIM = new Identifier("factions", "chunk_unclaim");
    public static final Identifier FACTION_DELETE = new Identifier("factions", "faction_delete");
    public static final Identifier CLIENT_REQUEST_DATA = new Identifier("factions", "client_request_data");

    public static void initialize() {
        JourneyFactions.debugLog("Initializing client network handlers...");
        
        // Register packet receivers
        registerPacketHandlers();
        
        // Register connection events
        registerConnectionEvents();
        
        JourneyFactions.debugLog("Client network handler initialized successfully");
    }

    private static void registerPacketHandlers() {
        // Handle full faction data sync (sent on join or request)
        ClientPlayNetworking.registerGlobalReceiver(FACTION_DATA_SYNC, (client, handler, buf, responseSender) -> {
            try {
                // Read faction count
                int factionCount = buf.readVarInt();
                JourneyFactions.debugLog("Receiving full faction data sync: {} factions", factionCount);
                
                // Read all factions
                Set<ClientFaction> factions = new HashSet<>();
                for (int i = 0; i < factionCount; i++) {
                    ClientFaction faction = readFactionFromBuffer(buf);
                    if (faction != null) {
                        factions.add(faction);
                        JourneyFactions.debugLog("Received faction: {} with {} chunks",faction.getName(), faction.getClaimedChunks().size());
                    }
                }
                
                // Process on main thread
                client.execute(() -> {
                    try {
                        // Clear existing data
                        JourneyFactions.getFactionManager().clear();
                        
                        // Add all received factions
                        for (ClientFaction faction : factions) {
                            JourneyFactions.getFactionManager().addOrUpdateFaction(faction);
                        }
                        
                        JourneyFactions.debugLog("Successfully processed {} factions from server", factions.size());
                        
                    } catch (Exception e) {
                        JourneyFactions.LOGGER.error("Error processing faction data sync", e);
                    }
                });
                
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Error reading faction data sync packet", e);
            }
        });

        // Handle individual faction updates
        ClientPlayNetworking.registerGlobalReceiver(FACTION_UPDATE, (client, handler, buf, responseSender) -> {
            try {
                ClientFaction faction = readFactionFromBuffer(buf);
                if (faction != null) {
                    JourneyFactions.debugLog("Received faction update: {}", faction.getName());
                    
                    client.execute(() -> {
                        JourneyFactions.getFactionManager().addOrUpdateFaction(faction);
                    });
                }
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Error processing faction update", e);
            }
        });

        // Handle chunk claims
        ClientPlayNetworking.registerGlobalReceiver(CHUNK_CLAIM, (client, handler, buf, responseSender) -> {
            try {
                String factionId = buf.readString();
                int chunkX = buf.readInt();
                int chunkZ = buf.readInt();
                
                ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                JourneyFactions.debugLog("Received chunk claim: {} by faction {}", chunk, factionId);
                
                client.execute(() -> {
                    JourneyFactions.getFactionManager().setChunkOwner(chunk, factionId);
                });
                
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Error processing chunk claim", e);
            }
        });

        // Handle chunk unclaims
        ClientPlayNetworking.registerGlobalReceiver(CHUNK_UNCLAIM, (client, handler, buf, responseSender) -> {
            try {
                int chunkX = buf.readInt();
                int chunkZ = buf.readInt();
                
                ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                JourneyFactions.debugLog("Received chunk unclaim: {}", chunk);
                
                client.execute(() -> {
                    // Set to wilderness (null means wilderness)
                    JourneyFactions.getFactionManager().setChunkOwner(chunk, null);
                });
                
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Error processing chunk unclaim", e);
            }
        });

        // Handle faction deletions
        ClientPlayNetworking.registerGlobalReceiver(FACTION_DELETE, (client, handler, buf, responseSender) -> {
            try {
                String factionId = buf.readString();
                JourneyFactions.debugLog("Received faction deletion: {}", factionId);
                
                client.execute(() -> {
                    JourneyFactions.getFactionManager().removeFaction(factionId);
                });
                
            } catch (Exception e) {
                JourneyFactions.LOGGER.error("Error processing faction deletion", e);
            }
        });
        
        JourneyFactions.debugLog("Registered all packet handlers");
    }

    private static void registerConnectionEvents() {
        // Request faction data when joining a server
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            JourneyFactions.debugLog("Connected to server - requesting faction data");
            
            // Small delay to ensure everything is initialized
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // 1 second delay
                    requestFactionData();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    JourneyFactions.debugLog("Interrupted while waiting to request faction data");
                }
            }).start();
        });

        // Clear data when disconnecting
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            JourneyFactions.debugLog("Disconnected from server - clearing faction data");
            JourneyFactions.getFactionManager().clear();
        });
        
        JourneyFactions.debugLog("Registered connection event handlers");
    }

    /**
     * Request faction data from server
     */
    public static void requestFactionData() {
        try {
            JourneyFactions.debugLog("Requesting faction data from server");
            
            PacketByteBuf buf = PacketByteBufs.create();
            // Empty buffer - just a request signal
            
            ClientPlayNetworking.send(CLIENT_REQUEST_DATA, buf);
            JourneyFactions.debugLog("Faction data request sent");
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Failed to request faction data", e);
        }
    }

    /**
     * Read faction data from packet buffer (matches server-side writeFactionToBuffer)
     */
    private static ClientFaction readFactionFromBuffer(PacketByteBuf buf) {
        try {
            String factionId = buf.readString();           // Faction ID (UUID as string)
            String factionName = buf.readString();         // Faction name
            String displayName = buf.readString();         // Display name with color
            
            // Create faction
            ClientFaction faction = new ClientFaction(factionId, factionName);
            faction.setDisplayName(displayName);
            
            // Read faction type
            int typeOrdinal = buf.readVarInt();
            ClientFaction.FactionType type = getFactionTypeFromOrdinal(typeOrdinal);
            faction.setType(type);
            
            // Read color if present
            boolean hasColor = buf.readBoolean();
            if (hasColor) {
                int colorRGB = buf.readInt();
                faction.setColor(new Color(colorRGB));
            }
            
            // Read claimed chunks
            int chunkCount = buf.readVarInt();
            Set<ChunkPos> chunks = new HashSet<>();
            for (int i = 0; i < chunkCount; i++) {
                int chunkX = buf.readInt();
                int chunkZ = buf.readInt();
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
            faction.setClaimedChunks(chunks);
            
            JourneyFactions.debugLog("Read faction from buffer: {} ({}) with {} chunks",factionName, type, chunkCount);
            
            return faction;
            
        } catch (Exception e) {
            JourneyFactions.LOGGER.error("Error reading faction from buffer", e);
            return null;
        }
    }

    /**
     * Convert ordinal back to faction type (matches server-side getFactionTypeOrdinal)
     */
    private static ClientFaction.FactionType getFactionTypeFromOrdinal(int ordinal) {
        switch (ordinal) {
            case 1: return ClientFaction.FactionType.WILDERNESS;
            case 2: return ClientFaction.FactionType.SAFEZONE;
            case 3: return ClientFaction.FactionType.WARZONE;
            default: return ClientFaction.FactionType.PLAYER;
        }
    }
}