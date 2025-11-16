package github.io.ssaspawnerlimiter.command.subcommands;

import com.mojang.brigadier.context.CommandContext;
import github.io.ssaspawnerlimiter.SSASpawnerLimiter;
import github.io.ssaspawnerlimiter.command.BaseSubCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ReloadSubCommand extends BaseSubCommand {

    public ReloadSubCommand(SSASpawnerLimiter plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return "ssaspawnerlimiter.command.reload";
    }

    @Override
    public String getDescription() {
        return "Reload the plugin configuration";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        try {
            plugin.getMessageService().sendMessage(sender, "command_reload");

            // Reload config
            plugin.reloadConfig();

            // Reinitialize language system
            plugin.getLanguageManager().reloadLanguages();

            // Clear cache
            if (plugin.getChunkLimitService() != null) {
                plugin.getChunkLimitService().clearCache();
            }

            plugin.getMessageService().sendMessage(sender, "reload_success");
            return 1;
        } catch (Exception e) {
            plugin.getLogger().severe("Error during reload: " + e.getMessage());
            e.printStackTrace();
            plugin.getMessageService().sendMessage(sender, "reload_failed");
            return 0;
        }
    }
}

