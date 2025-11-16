package github.io.ssaspawnerlimiter.listener;

import github.io.ssaspawnerlimiter.SSASpawnerLimiter;
import github.io.ssaspawnerlimiter.Scheduler;
import github.io.ssaspawnerlimiter.service.ChunkLimitService;
import github.io.ssaspawnerlimiter.util.ChunkKey;
import github.nighter.smartspawner.api.events.SpawnerBreakEvent;
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

/**
 * Event listener for SmartSpawner events to enforce chunk limits.
 * Designed to be thread-safe and work with Folia's region-based threading.
 */
public class SpawnerLimitListener implements Listener {
    private final SSASpawnerLimiter plugin;
    private final ChunkLimitService limitService;

    public SpawnerLimitListener(SSASpawnerLimiter plugin, ChunkLimitService limitService) {
        this.plugin = plugin;
        this.limitService = limitService;
    }

    /**
     * Handle spawner placement - check if limit is reached
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerPlace(SpawnerPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        int quantity = event.getQuantity();

        // Check limit asynchronously, then sync back to cancel if needed
        limitService.canPlaceSpawner(player, location, quantity).thenAccept(canPlace -> {
            if (!canPlace) {
                // Must cancel on the region thread for Folia
                Scheduler.runAtLocation(location, () -> {
                    if (!event.isCancelled()) {
                        event.setCancelled(true);

                        // Send message to player
                        limitService.getSpawnerCount(new ChunkKey(location)).thenAccept(currentCount -> {
                            Scheduler.runAtLocation(player.getLocation(), () -> {
                                Map<String, String> placeholders = new HashMap<>();
                                placeholders.put("limit", String.valueOf(limitService.getMaxSpawnersPerChunk()));
                                placeholders.put("current", String.valueOf(currentCount));
                                plugin.getMessageService().sendMessage(player, "limit_reached", placeholders);
                            });
                        });
                    }
                });
            } else {
                // Update count in database asynchronously
                limitService.addSpawners(location, quantity);
            }
        });
    }

    /**
     * Handle spawner break - decrease count
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerBreak(SpawnerBreakEvent event) {
        if (!plugin.getConfig().getBoolean("chunk-limit.enabled", true)) {
            return;
        }

        Location location = event.getLocation();
        int quantity = event.getQuantity();

        if (plugin.getConfig().getBoolean("debug", false)) {
            String entityName = event.getEntity() != null ? event.getEntity().getName() : "Unknown";
            plugin.getLogger().info(String.format(
                "[DEBUG] SpawnerBreakEvent - Entity: %s, Quantity: %d, Chunk: %s",
                entityName, quantity, new ChunkKey(location)
            ));
        }

        // Update count asynchronously
        limitService.removeSpawners(location, quantity).thenAccept(newCount -> {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info(String.format(
                    "[DEBUG] Spawners removed - New count: %d, Chunk: %s",
                    newCount, new ChunkKey(location)
                ));
            }
        });
    }

    /**
     * Handle spawner stacking - check limit and update count
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerStack(SpawnerStackEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        int oldQuantity = event.getOldQuantity();
        int newQuantity = event.getNewQuantity();
        int difference = newQuantity - oldQuantity;

        // Only check if we're adding to the stack
        if (difference <= 0) {
            // If removing from stack, update count in MONITOR phase
            return;
        }

        // Check if adding would exceed limit
        limitService.canPlaceSpawner(player, location, difference).thenAccept(canStack -> {
            if (!canStack) {
                // Must cancel on the region thread for Folia
                Scheduler.runAtLocation(location, () -> {
                    if (!event.isCancelled()) {
                        event.setCancelled(true);

                        // Send message to player
                        limitService.getSpawnerCount(new ChunkKey(location)).thenAccept(currentCount -> {
                            Scheduler.runAtLocation(player.getLocation(), () -> {
                                Map<String, String> placeholders = new HashMap<>();
                                placeholders.put("limit", String.valueOf(limitService.getMaxSpawnersPerChunk()));
                                placeholders.put("current", String.valueOf(currentCount));
                                plugin.getMessageService().sendMessage(player, "limit_reached", placeholders);
                            });
                        });
                    }
                });
            }
        });
    }

    /**
     * Handle spawner stack completion - update count
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerStackComplete(SpawnerStackEvent event) {
        Location location = event.getLocation();
        int oldQuantity = event.getOldQuantity();
        int newQuantity = event.getNewQuantity();

        // Update count asynchronously
        limitService.updateStackCount(location, oldQuantity, newQuantity);
    }

    /**
     * Handle spawner removal (from GUI or other means) - decrease count
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerRemove(SpawnerRemoveEvent event) {
        Location location = event.getLocation();
        int changeAmount = event.getChangeAmount();

        // changeAmount is the difference (can be negative when removing)
        limitService.removeSpawners(location, Math.abs(changeAmount));
    }
}

