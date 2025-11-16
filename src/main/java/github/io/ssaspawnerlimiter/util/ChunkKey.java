package github.io.ssaspawnerlimiter.util;

import org.bukkit.Chunk;
import org.bukkit.Location;

/**
 * Immutable key for identifying chunks across worlds.
 * Thread-safe and suitable for use as HashMap keys.
 */
public record ChunkKey(String world, int x, int z) {

    public ChunkKey(Chunk chunk) {
        this(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public ChunkKey(Location location) {
        this(location.getChunk());
    }

    @Override
    public String toString() {
        return world + ":" + x + "," + z;
    }

    /**
     * Parse ChunkKey from string format "world:x,z"
     */
    public static ChunkKey fromString(String str) {
        String[] parts = str.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid ChunkKey format: " + str);
        }

        String world = parts[0];
        String[] coords = parts[1].split(",");
        if (coords.length != 2) {
            throw new IllegalArgumentException("Invalid ChunkKey coordinates: " + str);
        }

        int x = Integer.parseInt(coords[0]);
        int z = Integer.parseInt(coords[1]);

        return new ChunkKey(world, x, z);
    }
}

