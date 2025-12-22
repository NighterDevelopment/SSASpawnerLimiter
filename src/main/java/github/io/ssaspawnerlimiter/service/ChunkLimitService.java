package github.io.ssaspawnerlimiter.service;

import github.io.ssaspawnerlimiter.SSASpawnerLimiter;
import github.io.ssaspawnerlimiter.database.DatabaseManager;
import github.io.ssaspawnerlimiter.util.ChunkKey;
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import lombok.Getter;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
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
    private final SmartSpawnerAPI api;

    // Thread-safe cache for chunk spawner counts
    private final Map<ChunkKey, CacheEntry> chunkCache;
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

    @Getter
    private boolean enabled;
    @Getter
    private int maxSpawnersPerChunk;
    @Getter
    private boolean verifyChunkCountOnCheck;
    private final long cacheExpirationMs = 300 * 1000L; // 5 minutes

    public ChunkLimitService(SSASpawnerLimiter plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.api = plugin.getApi();
        this.chunkCache = new ConcurrentHashMap<>();
        loadSpawnerLimit();
    }

    public void loadSpawnerLimit() {
        this.enabled = plugin.getConfig().getBoolean("enable_chunk_limit", true);
        this.maxSpawnersPerChunk = plugin.getConfig().getInt("max_spawners_per_chunk", 1000);
        this.verifyChunkCountOnCheck = plugin.getConfig().getBoolean("verify_chunk_count_on_check", true);
        if (verifyChunkCountOnCheck) {
            plugin.getLogger().info("Chunk spawner count verification is ENABLED. You can disable it after all chunks are verified to improve performance.");
        }

    }

    /**
     * Check if a player can place spawners in a chunk (SYNC)
     * @param player The player placing the spawner
     * @param location The location where spawner will be placed
     * @param quantity The quantity being placed
     * @return true if allowed, false otherwise
     */
    public boolean canPlaceSpawner(Player player, Location location, int quantity) {
        if (!enabled) {
            return true;
        }

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
        // If verification is enabled, verify actual count from SmartSpawner API
        if (verifyChunkCountOnCheck) {
            int actualCount = verifyAndUpdateChunkCount(key);
            if (actualCount >= 0) {
                return actualCount;
            }
        }

        CacheEntry cached = getCachedCount(key);
        if (cached != null && !cached.isExpired()) {
            return cached.count;
        }

        // Cache miss or expired, fetch from database synchronously
        try {
            int count = databaseManager.getSpawnerCount(key.world(), key.x(), key.z()).get();
            updateCache(key, count);
            return count;
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting spawner count for chunk " + key + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Verify and update chunk count by checking actual spawners in the chunk
     * Uses SmartSpawner API to get all spawners and filter by chunk
     * This is thread-safe and optimized for performance
     *
     * @param key The chunk key to verify
     * @return actual count, or -1 if verification failed
     */
    private int verifyAndUpdateChunkCount(ChunkKey key) {
        try {
            // Get all spawners from API (cached by SmartSpawner)
            List<SpawnerDataDTO> allSpawners = api.getAllSpawners();

            if (allSpawners == null) {
                return -1;
            }

            // Filter spawners in this chunk and sum their stack sizes
            int actualCount = allSpawners.stream()
                .filter(spawner -> {
                    Location loc = spawner.getLocation();
                    return loc != null
                        && loc.getWorld() != null
                        && loc.getWorld().getName().equals(key.world())
                        && (loc.getBlockX() >> 4) == key.x()
                        && (loc.getBlockZ() >> 4) == key.z();
                })
                .mapToInt(SpawnerDataDTO::getStackSize)
                .sum();

            // Update database and cache with actual count
            databaseManager.setSpawnerCount(key.world(), key.x(), key.z(), actualCount)
                .thenAccept(success -> {
                    if (success) {
                        updateCache(key, actualCount);
                        if (plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().info(String.format(
                                "[VERIFY] Chunk %s actual count: %d", key, actualCount
                            ));
                        }
                    }
                });

            return actualCount;

        } catch (Exception e) {
            plugin.getLogger().warning("Error verifying chunk count for " + key + ": " + e.getMessage());
            return -1;
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
                updateCache(key, newCount);
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
                if (success) {
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

