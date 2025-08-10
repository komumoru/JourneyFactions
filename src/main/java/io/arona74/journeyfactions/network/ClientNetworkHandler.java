package io.arona74.journeyfactions.network;

import io.arona74.journeyfactions.JourneyFactions;
import io.arona74.journeyfactions.data.ClientFaction;
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
        JourneyFactions.LOGGER.info("Initializing client network handlers...");

        // Handle full faction data sync (sent when player joins)
        ClientPlayNetworking.registerGlobalReceiver(FACTION_DATA_SYNC, (client, handler, buf, responseSender) -> {
            // Read faction data from buffer
            int factionCount = buf.readVarInt();
            Set<ClientFaction> factions = new HashSet<>();
            
            for (int i = 0; i < factionCount; i++) {
                ClientFaction faction = readFactionFromBuffer(buf);
                factions.add(faction);
            }
            
            // Apply on main thread
            client.execute(() -> {
                JourneyFactions.LOGGER.info("Received faction data sync: {} factions", factionCount);
                
                // Clear existing data and add new factions
                JourneyFactions.getFactionManager().clear();
                for (ClientFaction faction : factions) {
                    JourneyFactions.getFactionManager().addOrUpdateFaction(faction);
                }
            });
        });

        // Handle individual faction updates
        ClientPlayNetworking.registerGlobalReceiver(FACTION_UPDATE, (client, handler, buf, responseSender) -> {
            ClientFaction faction = readFactionFromBuffer(buf);
            
            client.execute(() -> {
                JourneyFactions.LOGGER.debug("Received faction update: {}", faction.getName());
                JourneyFactions.getFactionManager().addOrUpdateFaction(faction);
            });
        });

        // Handle chunk claims
        ClientPlayNetworking.registerGlobalReceiver(CHUNK_CLAIM, (client, handler, buf, responseSender) -> {
            String factionId = buf.readString();
            int x = buf.readInt();
            int z = buf.readInt();
            ChunkPos chunk = new ChunkPos(x, z);
            
            client.execute(() -> {
                JourneyFactions.LOGGER.debug("Chunk claimed: {} by {}", chunk, factionId);
                JourneyFactions.getFactionManager().setChunkOwner(chunk, factionId);
            });
        });

        // Handle chunk unclaims
        ClientPlayNetworking.registerGlobalReceiver(CHUNK_UNCLAIM, (client, handler, buf, responseSender) -> {
            int x = buf.readInt();
            int z = buf.readInt();
            ChunkPos chunk = new ChunkPos(x, z);
            
            client.execute(() -> {
                JourneyFactions.LOGGER.debug("Chunk unclaimed: {}", chunk);
                JourneyFactions.getFactionManager().setChunkOwner(chunk, null);
            });
        });

        // Handle faction deletion
        ClientPlayNetworking.registerGlobalReceiver(FACTION_DELETE, (client, handler, buf, responseSender) -> {
            String factionId = buf.readString();
            
            client.execute(() -> {
                JourneyFactions.LOGGER.info("Faction deleted: {}", factionId);
                JourneyFactions.getFactionManager().removeFaction(factionId);
            });
        });

        JourneyFactions.LOGGER.info("Client network handlers registered successfully");
    }

    /**
     * Reads faction data from a packet buffer
     */
    private static ClientFaction readFactionFromBuffer(PacketByteBuf buf) {
        String id = buf.readString();
        String name = buf.readString();
        String displayName = buf.readString();
        
        ClientFaction faction = new ClientFaction(id, name);
        faction.setDisplayName(displayName);
        
        // Read faction type
        int typeOrdinal = buf.readVarInt();
        if (typeOrdinal >= 0 && typeOrdinal < ClientFaction.FactionType.values().length) {
            faction.setType(ClientFaction.FactionType.values()[typeOrdinal]);
        }
        
        // Read color (server should always provide this)
        boolean hasColor = buf.readBoolean();
        if (hasColor) {
            int colorRGB = buf.readInt();
            faction.setColor(new Color(colorRGB));
            JourneyFactions.LOGGER.debug("Faction {} received color: #{}", 
                name, Integer.toHexString(colorRGB).toUpperCase());
        } else {
            JourneyFactions.LOGGER.warn("Faction {} did not receive color from server, using fallback", name);
        }
        
        // Read claimed chunks
        int chunkCount = buf.readVarInt();
        Set<ChunkPos> chunks = new HashSet<>();
        for (int i = 0; i < chunkCount; i++) {
            int x = buf.readInt();
            int z = buf.readInt();
            chunks.add(new ChunkPos(x, z));
        }
        faction.setClaimedChunks(chunks);
        
        return faction;
    }

    /**
     * Request faction data from server (called when client connects)
     */
    public static void requestFactionData() {
        if (ClientPlayNetworking.canSend(CLIENT_REQUEST_DATA)) {
            PacketByteBuf buf = PacketByteBufs.create();
            ClientPlayNetworking.send(CLIENT_REQUEST_DATA, buf);
            JourneyFactions.LOGGER.info("Requested faction data from server");
        } else {
            JourneyFactions.LOGGER.warn("Cannot send faction data request - server may not have factions mod");
        }
    }
}