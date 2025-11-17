package github.io.ssaspawnerlimiter.command.subcommands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import github.io.ssaspawnerlimiter.Scheduler;
import github.io.ssaspawnerlimiter.SSASpawnerLimiter;
import github.io.ssaspawnerlimiter.command.BaseSubCommand;
import github.io.ssaspawnerlimiter.util.ChunkKey;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class CheckSubCommand extends BaseSubCommand {

    public CheckSubCommand(SSASpawnerLimiter plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "check";
    }

    @Override
    public String getPermission() {
        return "ssaspawnerlimiter.command.check";
    }

    @Override
    public String getDescription() {
        return "Check spawner count for a specific player's chunk";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());

        builder.requires(source -> hasPermission(source.getSender()));

        // Add player argument with suggestions
        builder.then(Commands.argument("player", StringArgumentType.word())
                .suggests((context, suggestionsBuilder) -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        suggestionsBuilder.suggest(p.getName());
                    }
                    return suggestionsBuilder.buildFuture();
                })
                .executes(this::executeWithPlayer));

        // Add execute for when no player is provided (show usage)
        builder.executes(this::execute);

        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        plugin.getMessageService().sendMessage(sender, "command_usage_check");
        return 0;
    }

    private int executeWithPlayer(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String playerName = context.getArgument("player", String.class);

        Player target = Bukkit.getPlayer(playerName);
        if (target == null || !target.isOnline()) {
            plugin.getMessageService().sendMessage(sender, "command_check_player_not_found");
            return 0;
        }

        Chunk chunk = target.getLocation().getChunk();
        ChunkKey key = new ChunkKey(chunk);

        // Run async to avoid blocking
        Scheduler.runTaskAsync(() -> {
            int count = plugin.getChunkLimitService().getSpawnerCount(key);
            int limit = plugin.getChunkLimitService().getMaxSpawnersPerChunk();

            // Send message on appropriate thread
            java.util.Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("player", target.getName());
            placeholders.put("current", String.valueOf(count));
            placeholders.put("limit", String.valueOf(limit));

            if (sender instanceof Player player) {
                Scheduler.runAtLocation(player.getLocation(), () ->
                    plugin.getMessageService().sendMessage(sender, "command_check_result", placeholders)
                );
            } else {
                plugin.getMessageService().sendMessage(sender, "command_check_result", placeholders);
            }
        });

        return 1;
    }
}

