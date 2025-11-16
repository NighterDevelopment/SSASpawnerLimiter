package github.io.ssaspawnerlimiter.command.subcommands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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

import java.util.HashMap;
import java.util.Map;

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

        // Add player argument
        builder.then(Commands.argument("player", StringArgumentType.word())
                .suggests((context, suggestionsBuilder) -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        suggestionsBuilder.suggest(p.getName());
                    }
                    return suggestionsBuilder.buildFuture();
                })
                .executes(this::executeWithPlayer));

        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        // This should not be called as we override build()
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage("Usage: /ssalimiter check <player>");
        return 0;
    }

    private int executeWithPlayer(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String playerName = context.getArgument("player", String.class);

        Player target = Bukkit.getPlayer(playerName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("Player not found or not online!");
            return 0;
        }

        Chunk chunk = target.getLocation().getChunk();
        ChunkKey key = new ChunkKey(chunk);

        plugin.getChunkLimitService().getSpawnerCount(key).thenAccept(count -> {
            int limit = plugin.getChunkLimitService().getMaxSpawnersPerChunk();

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", target.getName());
            placeholders.put("current", String.valueOf(count));
            placeholders.put("limit", String.valueOf(limit));

            sender.sendMessage(String.format("Player %s's chunk has %d/%d spawners.",
                target.getName(), count, limit));
        });

        return 1;
    }
}

