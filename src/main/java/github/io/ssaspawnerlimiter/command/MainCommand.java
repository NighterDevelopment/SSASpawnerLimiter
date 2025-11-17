package github.io.ssaspawnerlimiter.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import github.io.ssaspawnerlimiter.SSASpawnerLimiter;
import github.io.ssaspawnerlimiter.command.subcommands.CheckSubCommand;
import github.io.ssaspawnerlimiter.command.subcommands.InfoSubCommand;
import github.io.ssaspawnerlimiter.command.subcommands.ReloadSubCommand;
import github.io.ssaspawnerlimiter.command.subcommands.StatsSubCommand;
import io.github.pluginlangcore.language.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
@RequiredArgsConstructor
public class MainCommand {
    private final SSASpawnerLimiter plugin;
    private final MessageService messageService;
    private final List<BaseSubCommand> subCommands;

    public MainCommand(SSASpawnerLimiter plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.subCommands = List.of(
                new ReloadSubCommand(plugin),
                new InfoSubCommand(plugin),
                new CheckSubCommand(plugin),
                new StatsSubCommand(plugin)
        );
    }

    // Build the main command with all subcommands
    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return buildCommandWithName("ssaspawnerlimiter");
    }

    // Build the alias command
    public LiteralCommandNode<CommandSourceStack> buildAliasCommand() {
        return buildCommandWithName("ssalimiter");
    }

    // Helper method to build command with any name
    private LiteralCommandNode<CommandSourceStack> buildCommandWithName(String name) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(name);

        // Add permission requirement that works for console/RCON
        builder.requires(source -> {
            CommandSender sender = source.getSender();

            // Always allow console and RCON
            if (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender) {
                return true;
            }

            // For players, check the base permission
            if (sender instanceof Player player) {
                return player.hasPermission("ssaspawnerlimiter.command.use") || player.isOp();
            }

            // Allow other command senders (like command blocks) if they have permission
            return sender.hasPermission("ssaspawnerlimiter.command.use");
        });

        // Add execute handler for when no subcommand is provided (show usage)
        builder.executes(context -> {
            CommandSender sender = context.getSource().getSender();
            sender.sendMessage("§6§l━━━━━ §e§lSSA Spawner Limiter §6§l━━━━━");
            sender.sendMessage("§fAvailable commands:");
            for (BaseSubCommand subCommand : subCommands) {
                sender.sendMessage("§8  • §e/" + name + " " + subCommand.getName() + " §7- §f" + subCommand.getDescription());
            }
            sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return 1;
        });

        // Add all subcommands to the builder
        for (BaseSubCommand subCommand : subCommands) {
            builder.then(subCommand.build());
        }

        return builder.build();
    }
}

