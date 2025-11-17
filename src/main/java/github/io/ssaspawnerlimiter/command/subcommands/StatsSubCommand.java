package github.io.ssaspawnerlimiter.command.subcommands;

import com.mojang.brigadier.context.CommandContext;
import github.io.ssaspawnerlimiter.Scheduler;
import github.io.ssaspawnerlimiter.SSASpawnerLimiter;
import github.io.ssaspawnerlimiter.command.BaseSubCommand;
import github.io.ssaspawnerlimiter.service.ChunkLimitService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

@NullMarked
public class StatsSubCommand extends BaseSubCommand {

    public StatsSubCommand(SSASpawnerLimiter plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "stats";
    }

    @Override
    public String getPermission() {
        return "ssaspawnerlimiter.command.stats";
    }

    @Override
    public String getDescription() {
        return "View statistics about spawner limits";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        plugin.getMessageService().sendMessage(sender, "command_stats_header");

        // Run async to avoid blocking
        Scheduler.runTaskAsync(() -> {
            try {
                ChunkLimitService.Statistics stats = plugin.getChunkLimitService().getStatistics().get();

                // Send messages on appropriate thread
                Runnable sendMessages = () -> {
                    Map<String, String> chunkPlaceholders = new HashMap<>();
                    chunkPlaceholders.put("total", String.valueOf(stats.totalChunks()));
                    plugin.getMessageService().sendMessage(sender, "command_stats_total_chunks", chunkPlaceholders);

                    Map<String, String> spawnerPlaceholders = new HashMap<>();
                    spawnerPlaceholders.put("spawners", String.valueOf(stats.totalSpawners()));
                    plugin.getMessageService().sendMessage(sender, "command_stats_total_spawners", spawnerPlaceholders);

                    Map<String, String> cachePlaceholders = new HashMap<>();
                    cachePlaceholders.put("cache", String.valueOf(stats.cacheSize()));
                    plugin.getMessageService().sendMessage(sender, "command_stats_cache_size", cachePlaceholders);

                    Map<String, String> dbPlaceholders = new HashMap<>();
                    dbPlaceholders.put("database", "SQLite");
                    plugin.getMessageService().sendMessage(sender, "command_stats_database", dbPlaceholders);
                };

                if (sender instanceof Player player) {
                    Scheduler.runAtLocation(player.getLocation(), sendMessages);
                } else {
                    sendMessages.run();
                }
            } catch (Exception e) {
                java.util.Map<String, String> errorPlaceholders = new java.util.HashMap<>();
                errorPlaceholders.put("error", e.getMessage());
                plugin.getMessageService().sendMessage(sender, "stats_error", errorPlaceholders);
            }
        });

        return 1;
    }
}

