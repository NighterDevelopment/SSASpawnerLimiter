package github.io.ssaspawnerlimiter.listener;

import github.io.ssaspawnerlimiter.SSASpawnerLimiter;
import github.io.ssaspawnerlimiter.Scheduler;
import github.io.ssaspawnerlimiter.service.ChunkLimitService;
import github.io.ssaspawnerlimiter.service.PlayerLimitService;
import github.io.ssaspawnerlimiter.util.ChunkKey;
import github.nighter.smartspawner.api.events.SpawnerPlayerBreakEvent;
import github.nighter.smartspawner.api.events.SpawnerPlaceEvent;
import github.nighter.smartspawner.api.events.SpawnerRemoveEvent;
import github.nighter.smartspawner.api.events.SpawnerStackEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event listener for SmartSpawner events to enforce chunk and player limits.
 * Designed to be thread-safe and work with Folia's region-based threading.
 */
public class SpawnerLimitListener implements Listener {
    private final SSASpawnerLimiter plugin;
    private final ChunkLimitService chunkLimitService;
    private final PlayerLimitService playerLimitService;

    public SpawnerLimitListener(SSASpawnerLimiter plugin, ChunkLimitService chunkLimitService, PlayerLimitService playerLimitService) {
        this.plugin = plugin;
        this.chunkLimitService = chunkLimitService;
        this.playerLimitService = playerLimitService;
    }

    /**
     * Handle spawner placement - check if limit is reached
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerPlace(SpawnerPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        int quantity = event.getQuantity();
        UUID playerUUID = player.getUniqueId();

        // Check chunk limit synchronously for immediate cancellation
        boolean canPlaceChunk = chunkLimitService.canPlaceSpawner(player, location, quantity);

        if (!canPlaceChunk) {
            event.setCancelled(true);

            // Send chunk limit message to player on their region thread
            Scheduler.runAtLocation(player.getLocation(), () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("limit", String.valueOf(chunkLimitService.getMaxSpawnersPerChunk()));
                placeholders.put("current", String.valueOf(chunkLimitService.getSpawnerCount(new ChunkKey(location))));
                plugin.getMessageService().sendMessage(player, "chunk_limit_reached", placeholders);
            });
            return;
        }

        // Check player limit synchronously for immediate cancellation
        boolean canPlacePlayer = playerLimitService.canPlaceSpawner(player, quantity);

        if (!canPlacePlayer) {
            event.setCancelled(true);

            // Send player limit message to player on their region thread
            Scheduler.runAtLocation(player.getLocation(), () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("limit", String.valueOf(playerLimitService.getPlayerLimit(player)));
                placeholders.put("current", String.valueOf(playerLimitService.getPlayerSpawnerCount(playerUUID)));
                plugin.getMessageService().sendMessage(player, "player_limit_reached", placeholders);
            });
            return;
        }

        // Both limits passed - Update counts in database asynchronously (in background)
        chunkLimitService.addSpawners(location, quantity);
        playerLimitService.addSpawners(playerUUID, quantity);
    }

    /**
     * Handle spawner break - decrease count
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerBreak(SpawnerPlayerBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        int quantity = event.getQuantity();
        UUID playerUUID = player.getUniqueId();

        if (plugin.getConfig().getBoolean("debug", false)) {
            String entityName = event.getEntity() != null ? event.getEntity().getName() : "Unknown";
            plugin.getLogger().info(String.format(
                "[DEBUG] SpawnerBreakEvent - Player: %s, Entity: %s, Quantity: %d, Chunk: %s",
                player.getName(), entityName, quantity, new ChunkKey(location)
            ));
        }

        // Update counts asynchronously in background
        chunkLimitService.removeSpawners(location, quantity);
        playerLimitService.removeSpawners(playerUUID, quantity);
    }

    /**
     * Handle spawner stacking - check limit and update count
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerStack(SpawnerStackEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        int oldQuantity = event.getOldStackSize();
        int newQuantity = event.getNewStackSize();
        int difference = newQuantity - oldQuantity;
        UUID playerUUID = player.getUniqueId();

        // Only check if we're adding to the stack
        if (difference <= 0) {
            return;
        }

        // Check if adding would exceed chunk limit (SYNC for immediate cancel)
        boolean canStackChunk = chunkLimitService.canPlaceSpawner(player, location, difference);

        if (!canStackChunk) {
            event.setCancelled(true);

            // Send chunk limit message to player on their region thread
            Scheduler.runAtLocation(player.getLocation(), () -> {
                int currentCount = chunkLimitService.getSpawnerCount(new ChunkKey(location));
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("limit", String.valueOf(chunkLimitService.getMaxSpawnersPerChunk()));
                placeholders.put("current", String.valueOf(currentCount));
                plugin.getMessageService().sendMessage(player, "chunk_limit_reached", placeholders);
            });
            return;
        }

        // Check if adding would exceed player limit (SYNC for immediate cancel)
        boolean canStackPlayer = playerLimitService.canPlaceSpawner(player, difference);

        if (!canStackPlayer) {
            event.setCancelled(true);

            // Send player limit message to player on their region thread
            Scheduler.runAtLocation(player.getLocation(), () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("limit", String.valueOf(playerLimitService.getPlayerLimit(player)));
                placeholders.put("current", String.valueOf(playerLimitService.getPlayerSpawnerCount(playerUUID)));
                plugin.getMessageService().sendMessage(player, "player_limit_reached", placeholders);
            });
        }
    }

    /**
     * Handle spawner stack completion - update count
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerStackComplete(SpawnerStackEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        int oldQuantity = event.getOldStackSize();
        int newQuantity = event.getNewStackSize();
        UUID playerUUID = player.getUniqueId();

        // Update counts asynchronously
        chunkLimitService.updateStackCount(location, oldQuantity, newQuantity);
        playerLimitService.updateStackCount(playerUUID, oldQuantity, newQuantity);
    }

    /**
     * Handle spawner removal (from GUI or other means) - decrease count
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerRemove(SpawnerRemoveEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        int changeAmount = event.getChangeAmount();
        UUID playerUUID = player.getUniqueId();

        // changeAmount is the difference (can be negative when removing)
        chunkLimitService.removeSpawners(location, Math.abs(changeAmount));
        playerLimitService.removeSpawners(playerUUID, Math.abs(changeAmount));
    }
}

