package github.io.ssaspawnerlimiter.service;

import github.io.ssaspawnerlimiter.SSASpawnerLimiter;
import github.io.ssaspawnerlimiter.database.DatabaseManager;
import github.io.ssaspawnerlimiter.util.ChunkKey;
import lombok.Getter;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service for managing chunk spawner limits with caching and thread-safety.
 * Designed to work efficiently with Folia's region-based threading.
 */
public class ChunkLimitService {
    private final SSASpawnerLimiter plugin;
    private final DatabaseManager databaseManager;

    // Thread-safe cache for chunk spawner counts
    private final Map<ChunkKey, CacheEntry> chunkCache;
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

    @Getter
    private final int maxSpawnersPerChunk;
    private final boolean cacheEnabled;
    private final long cacheExpirationMs;

    public ChunkLimitService(SSASpawnerLimiter plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.chunkCache = new ConcurrentHashMap<>();

        // Load config values
        this.maxSpawnersPerChunk = plugin.getConfig().getInt("chunk-limit.max-spawners-per-chunk", 100);
        this.cacheEnabled = plugin.getConfig().getBoolean("performance.cache-enabled", true);
        this.cacheExpirationMs = plugin.getConfig().getInt("performance.cache-expiration", 300) * 1000L;
    }

    /**
     * Check if a player can place spawners in a chunk (SYNC)
     * @param player The player placing the spawner
     * @param location The location where spawner will be placed
     * @param quantity The quantity being placed
     * @return true if allowed, false otherwise
     */
    public boolean canPlaceSpawner(Player player, Location location, int quantity) {
        // Check bypass permission (hardcoded for simplicity)
        if (player.hasPermission("ssaspawnerlimiter.bypass")) {
            return true;
        }

        Chunk chunk = location.getChunk();
        ChunkKey key = new ChunkKey(chunk);

        int currentCount = getSpawnerCount(key);
        int newCount = currentCount + quantity;
        return newCount <= maxSpawnersPerChunk;
    }

    /**
     * Get current spawner count in a chunk (SYNC)
     * @param key The chunk key
     * @return current count
     */
    public int getSpawnerCount(ChunkKey key) {
        if (cacheEnabled) {
            CacheEntry cached = getCachedCount(key);
            if (cached != null && !cached.isExpired()) {
                return cached.count;
            }
        }

        // Cache miss or expired, fetch from database synchronously
        try {
            int count = databaseManager.getSpawnerCount(key.world(), key.x(), key.z()).get();
            if (cacheEnabled) {
                updateCache(key, count);
            }
            return count;
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting spawner count for chunk " + key + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Add spawners to a chunk count (ASYNC - for background database update)
     * @param location The location of the spawner
     * @param quantity The quantity to add
     */
    public void addSpawners(Location location, int quantity) {
        Chunk chunk = location.getChunk();
        ChunkKey key = new ChunkKey(chunk);

        databaseManager.incrementSpawnerCount(key.world(), key.x(), key.z(), quantity)
            .thenAccept(newCount -> {
                if (cacheEnabled) {
                    updateCache(key, newCount);
                }
            });
    }

    /**
     * Remove spawners from a chunk count (ASYNC - for background database update)
     * @param location The location of the spawner
     * @param quantity The quantity to remove
     */
    public void removeSpawners(Location location, int quantity) {
        addSpawners(location, -quantity);
    }

    /**
     * Update spawner count when stacking (ASYNC - for background database update)
     * @param location The location of the spawner
     * @param oldQuantity The old quantity
     * @param newQuantity The new quantity
     */
    public void updateStackCount(Location location, int oldQuantity, int newQuantity) {
        int difference = newQuantity - oldQuantity;
        addSpawners(location, difference);
    }

    /**
     * Set exact spawner count for a chunk
     * @param key The chunk key
     * @param count The count to set
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> setSpawnerCount(ChunkKey key, int count) {
        return databaseManager.setSpawnerCount(key.world(), key.x(), key.z(), count)
            .thenApply(success -> {
                if (success && cacheEnabled) {
                    updateCache(key, count);
                }
                return success;
            });
    }

    /**
     * Reset chunk data
     * @param key The chunk key
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> resetChunk(ChunkKey key) {
        return databaseManager.deleteChunkData(key.world(), key.x(), key.z())
            .thenApply(success -> {
                if (success) {
                    invalidateCache(key);
                }
                return success;
            });
    }

    /**
     * Get cached count for a chunk
     */
    private CacheEntry getCachedCount(ChunkKey key) {
        cacheLock.readLock().lock();
        try {
            return chunkCache.get(key);
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Update cache with new count
     */
    private void updateCache(ChunkKey key, int count) {
        cacheLock.writeLock().lock();
        try {
            chunkCache.put(key, new CacheEntry(count, System.currentTimeMillis()));
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Invalidate cache for a chunk
     */
    private void invalidateCache(ChunkKey key) {
        cacheLock.writeLock().lock();
        try {
            chunkCache.remove(key);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Clear all cache entries
     */
    public void clearCache() {
        cacheLock.writeLock().lock();
        try {
            chunkCache.clear();
            plugin.getLogger().info("Cache cleared");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Clean up expired cache entries
     */
    public void cleanupExpiredCache() {
        cacheLock.writeLock().lock();
        try {
            long now = System.currentTimeMillis();
            chunkCache.entrySet().removeIf(entry ->
                now - entry.getValue().timestamp > cacheExpirationMs
            );
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Get cache size
     */
    public int getCacheSize() {
        cacheLock.readLock().lock();
        try {
            return chunkCache.size();
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Get statistics from database
     */
    public CompletableFuture<Statistics> getStatistics() {
        return databaseManager.getTotalChunks().thenCombine(
            databaseManager.getTotalSpawners(),
            (chunks, spawners) -> new Statistics(chunks, spawners, getCacheSize())
        );
    }

    /**
     * Cache entry with expiration
     */
    private class CacheEntry {
        final int count;
        final long timestamp;

        CacheEntry(int count, long timestamp) {
            this.count = count;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > cacheExpirationMs;
        }
    }

    /**
     * Statistics record
     */
    public record Statistics(int totalChunks, int totalSpawners, int cacheSize) {}
}

