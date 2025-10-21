package io.arona74.journeyfactions.data;

import io.arona74.journeyfactions.JourneyFactions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.util.math.ChunkPos;

/**
 * Tracks which chunks have been discovered by the local client.
 * This allows us to only display faction territory that the player has actually seen.
 */
public final class ClientDiscoveryTracker {

    private static boolean initialized = false;
    private static ChunkPos lastChunk;

    private ClientDiscoveryTracker() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        // Whenever the client finishes a tick, record the chunk the player currently occupies
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.player == null || client.world == null) {
                return;
            }

            ChunkPos currentChunk = new ChunkPos(client.player.getBlockPos());
            if (!currentChunk.equals(lastChunk)) {
                lastChunk = currentChunk;
                JourneyFactions.getFactionManager().markChunkDiscovered(currentChunk);
            }
        });

        // Reset discovered chunks whenever we change servers/worlds
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            lastChunk = null;
            JourneyFactions.getFactionManager().resetDiscoveredChunks();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            lastChunk = null;
            JourneyFactions.getFactionManager().resetDiscoveredChunks();
        });
    }
}
