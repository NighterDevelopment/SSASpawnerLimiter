package github.io.ssaspawnerlimiter.command.subcommands;

import com.mojang.brigadier.context.CommandContext;
import github.io.ssaspawnerlimiter.SSASpawnerLimiter;
import github.io.ssaspawnerlimiter.command.BaseSubCommand;
import github.io.ssaspawnerlimiter.util.ChunkKey;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

@NullMarked
public class InfoSubCommand extends BaseSubCommand {

    public InfoSubCommand(SSASpawnerLimiter plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String getPermission() {
        return "ssaspawnerlimiter.command.info";
    }

    @Override
    public String getDescription() {
        return "Check spawner count in current chunk";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        if (!isPlayer(sender)) {
            plugin.getMessageService().sendMessage(sender, "player_only");
            return 0;
        }

        Player player = getPlayer(sender);
        if (player == null) {
            return 0;
        }

        Chunk chunk = player.getLocation().getChunk();
        ChunkKey key = new ChunkKey(chunk);

        boolean hasBypass = player.hasPermission("ssaspawnerlimiter.bypass");

        plugin.getMessageService().sendMessage(player, "command_info_header");

        plugin.getChunkLimitService().getSpawnerCount(key).thenAccept(count -> {
            int limit = plugin.getChunkLimitService().getMaxSpawnersPerChunk();

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("current", String.valueOf(count));
            placeholders.put("limit", String.valueOf(limit));
            plugin.getMessageService().sendMessage(player, "command_info_chunk", placeholders);

            Map<String, String> locationPlaceholders = new HashMap<>();
            locationPlaceholders.put("x", String.valueOf(chunk.getX()));
            locationPlaceholders.put("z", String.valueOf(chunk.getZ()));
            locationPlaceholders.put("world", chunk.getWorld().getName());
            plugin.getMessageService().sendMessage(player, "command_info_location", locationPlaceholders);

            Map<String, String> bypassPlaceholders = new HashMap<>();
            bypassPlaceholders.put("bypass", hasBypass ? "Yes" : "No");
            plugin.getMessageService().sendMessage(player, "command_info_bypass", bypassPlaceholders);
        });

        return 1;
    }
}

