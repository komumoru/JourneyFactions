package io.arona74.journeyfactions.data;

import io.arona74.journeyfactions.JourneyFactions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;

/**
 * Tracks which chunks have been discovered by the local client.
 * This allows us to only display faction territory that the player has actually seen.
 */
public final class ClientDiscoveryTracker {

    private static boolean initialized = false;
    private static ChunkPos lastChunk;
    private static int lastRecordedRadius = -1;

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

            int radius = Math.max(0, getEffectiveViewDistance(client));
            ChunkPos currentChunk = new ChunkPos(client.player.getBlockPos());
            if (!currentChunk.equals(lastChunk) || radius != lastRecordedRadius) {
                lastChunk = currentChunk;
                lastRecordedRadius = radius;
                markLoadedAreaDiscovered(client, currentChunk, radius);
            }
        });

        // Reset discovered chunks whenever we change servers/worlds
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            lastChunk = null;
            lastRecordedRadius = -1;
            JourneyFactions.getFactionManager().resetDiscoveredChunks();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            lastChunk = null;
            lastRecordedRadius = -1;
            JourneyFactions.getFactionManager().resetDiscoveredChunks();
        });
    }

    private static void markLoadedAreaDiscovered(MinecraftClient client, ChunkPos centerChunk, int radius) {
        // Always ensure we at least mark the center chunk
        JourneyFactions.getFactionManager().markChunkDiscovered(centerChunk);

        if (radius <= 0) {
            return;
        }

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue; // already marked
                }
                ChunkPos nearby = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                JourneyFactions.getFactionManager().markChunkDiscovered(nearby);
            }
        }
    }

    private static int getEffectiveViewDistance(MinecraftClient client) {
        if (client == null || client.options == null) {
            return 0;
        }

        // JourneyMap renders chunks based on the game's view distance setting.
        // Use the configured view distance (in chunks) to approximate the loaded map range.
        return client.options.getViewDistance().getValue();
    }
}
