package github.io.ssaspawnerlimiter.command;

import github.io.ssaspawnerlimiter.SSASpawnerLimiter;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class BrigadierCommandManager {
    private final SSASpawnerLimiter plugin;
    private final MainCommand mainCommand;

    public BrigadierCommandManager(SSASpawnerLimiter plugin) {
        this.plugin = plugin;
        this.mainCommand = new MainCommand(plugin);
    }

    /**
     * Register all Brigadier commands with Paper's command system
     */
    public void registerCommands() {
        LifecycleEventManager<Plugin> manager = plugin.getLifecycleManager();

        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            // Register main command
            commands.register(mainCommand.buildCommand(), "SSA Spawner Limiter main command");

            // Register aliases
            commands.register(mainCommand.buildAliasCommand(), "SSA Spawner Limiter alias");
        });
    }
}

