package github.io.ssaspawnerlimiter.service;

import github.io.ssaspawnerlimiter.SSASpawnerLimiter;
import github.io.ssaspawnerlimiter.database.DatabaseManager;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for managing per-player spawner limits with permission-based tiers.
 * Thread-safe and designed to work with Folia's region-based threading.
 */
public class PlayerLimitService {
    private final SSASpawnerLimiter plugin;
    private final DatabaseManager databaseManager;

    // Thread-safe cache for player spawner counts
    private final Map<UUID, CacheEntry> playerCache;
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

    @Getter
    private boolean enabled;
    @Getter
    private int defaultMaxSpawnersPerPlayer;
    private final long cacheExpirationMs = 300 * 1000L; // 5 minutes

    // Pattern to match permission nodes like ssaspawnerlimiter.perplayer.1500
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("ssaspawnerlimiter\\.perplayer\\.(\\d+)");
    private static final String BYPASS_PERMISSION = "ssaspawnerlimiter.perplayer.bypass";

    public PlayerLimitService(SSASpawnerLimiter plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.playerCache = new ConcurrentHashMap<>();
        loadConfiguration();
    }

    /**
     * Load configuration from config.yml
     */
    public void loadConfiguration() {
        this.enabled = plugin.getConfig().getBoolean("enable_player_limit", true);
        this.defaultMaxSpawnersPerPlayer = plugin.getConfig().getInt("max_spawners_per_player", 500);
    }

    /**
     * Check if a player can place spawners (SYNC)
     * @param player The player placing the spawner
     * @param quantity The quantity being placed
     * @return true if allowed, false otherwise
     */
    public boolean canPlaceSpawner(Player player, int quantity) {
        if (!enabled) {
            return true;
        }

        // Check bypass permission
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        int currentCount = getPlayerSpawnerCount(uuid);
        int limit = getPlayerLimit(player);
        int newCount = currentCount + quantity;

        return newCount <= limit;
    }

    /**
     * Get player's spawner limit based on permissions
     * Checks for permission nodes like ssaspawnerlimiter.perplayer.1500
     * Returns highest limit found, or default if none found
     * @param player The player
     * @return The limit for this player
     */
    public int getPlayerLimit(Player player) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return Integer.MAX_VALUE;
        }

        int highestLimit = defaultMaxSpawnersPerPlayer;

        // Check all effective permissions for player
        for (var permAttachment : player.getEffectivePermissions()) {
            if (!permAttachment.getValue()) {
                continue; // Skip negated permissions
            }

            String permission = permAttachment.getPermission();
            Matcher matcher = PERMISSION_PATTERN.matcher(permission);

            if (matcher.matches()) {
                try {
                    int limit = Integer.parseInt(matcher.group(1));
                    if (limit > highestLimit) {
                        highestLimit = limit;
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid permission limit: " + permission);
                }
            }
        }

        return highestLimit;
    }

    /**
     * Get current spawner count for a player (SYNC)
     * @param uuid Player UUID
     * @return current count
     */
    public int getPlayerSpawnerCount(UUID uuid) {
        if (!enabled) {
            return 0;
        }

        CacheEntry cached = getCachedCount(uuid);
        if (cached != null && !cached.isExpired()) {
            return cached.count;
        }

        // Cache miss or expired, fetch from database synchronously
        try {
            int count = databaseManager.getPlayerSpawnerCount(uuid.toString()).get();
            updateCache(uuid, count);
            return count;
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting player spawner count for " + uuid + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Add spawners to a player's count (ASYNC - for background database update)
     * @param uuid Player UUID
     * @param quantity The quantity to add
     */
    public void addSpawners(UUID uuid, int quantity) {
        if (!enabled) {
            return;
        }

        databaseManager.incrementPlayerSpawnerCount(uuid.toString(), quantity)
            .thenAccept(newCount -> {
                updateCache(uuid, newCount);

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info(String.format(
                        "[DEBUG] Player %s spawner count: %d (+%d)", uuid, newCount, quantity
                    ));
                }
            });
    }

    /**
     * Remove spawners from a player's count (ASYNC - for background database update)
     * @param uuid Player UUID
     * @param quantity The quantity to remove
     */
    public void removeSpawners(UUID uuid, int quantity) {
        if (!enabled) {
            return;
        }

        addSpawners(uuid, -quantity);
    }

    /**
     * Update spawner count when stacking (ASYNC - for background database update)
     * @param uuid Player UUID
     * @param oldQuantity The old quantity
     * @param newQuantity The new quantity
     */
    public void updateStackCount(UUID uuid, int oldQuantity, int newQuantity) {
        if (!enabled) {
            return;
        }

        int difference = newQuantity - oldQuantity;
        addSpawners(uuid, difference);
    }

    /**
     * Set exact spawner count for a player
     * @param uuid Player UUID
     * @param count The count to set
     * @return CompletableFuture indicating success
     */
    public CompletableFuture<Boolean> setPlayerSpawnerCount(UUID uuid, int count) {
        return databaseManager.setPlayerSpawnerCount(uuid.toString(), count)
            .thenApply(success -> {
                if (success) {
                    updateCache(uuid, count);
                }
                return success;
            });
    }

    /**
     * Get cached count for a player
     */
    private CacheEntry getCachedCount(UUID uuid) {
        cacheLock.readLock().lock();
        try {
            return playerCache.get(uuid);
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Update cache with new count
     */
    private void updateCache(UUID uuid, int count) {
        cacheLock.writeLock().lock();
        try {
            playerCache.put(uuid, new CacheEntry(count, System.currentTimeMillis()));
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Invalidate cache for a player
     */
    private void invalidateCache(UUID uuid) {
        cacheLock.writeLock().lock();
        try {
            playerCache.remove(uuid);
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
            playerCache.clear();
            plugin.getLogger().info("Player cache cleared");
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
            playerCache.entrySet().removeIf(entry ->
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
            return playerCache.size();
        } finally {
            cacheLock.readLock().unlock();
        }
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
}

