package github.io.ssaspawnerlimiter;

import github.io.ssaspawnerlimiter.command.BrigadierCommandManager;
import github.io.ssaspawnerlimiter.database.DatabaseManager;
import github.io.ssaspawnerlimiter.listener.SpawnerLimitListener;
import github.io.ssaspawnerlimiter.service.ChunkLimitService;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.pluginlangcore.language.LanguageManager;
import io.github.pluginlangcore.language.MessageService;
import io.github.pluginlangcore.updater.LanguageUpdater;
import io.github.pluginupdatecore.updater.UpdateChecker;
import io.github.pluginupdatecore.updater.ConfigUpdater;
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.SmartSpawnerProvider;

import java.util.List;
import java.util.logging.Level;

@Getter
@Accessors(chain = false)
public final class SSASpawnerLimiter extends JavaPlugin {
    @Getter
    private static SSASpawnerLimiter instance;
    private final static String MODRINTH_PROJECT_ID = "";

    private LanguageManager languageManager;
    private MessageService messageService;
    private SmartSpawnerAPI api;
    private DatabaseManager databaseManager;
    private ChunkLimitService chunkLimitService;
    private BrigadierCommandManager commandManager;
    private Scheduler.Task cacheCleanupTask;

    private void checkSmartSpawnerAPI() {
        api = SmartSpawnerProvider.getAPI();
        if (api == null) {
            getLogger().warning("SmartSpawner not found! This addon requires SmartSpawner to work.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("SmartSpawner found, SSA Spawner Limiter is operational!");
    }

    private void checkPluginUpdates() {
        if (MODRINTH_PROJECT_ID.isEmpty()) {
            return;
        }
        UpdateChecker updateChecker = new UpdateChecker(this, MODRINTH_PROJECT_ID);
        updateChecker.checkForUpdates();
    }

    private void updateConfig() {
        saveDefaultConfig();
        ConfigUpdater configUpdater = new ConfigUpdater(this);
        configUpdater.checkAndUpdateConfig();
        reloadConfig();
    }

    private void initializeLanguageSystem() {
        new LanguageUpdater(this, List.of("en_US"));
        languageManager = new LanguageManager(this,
                LanguageManager.LanguageFileType.MESSAGES
        );
        messageService = new MessageService(this, languageManager);
        getLogger().info("Language system initialized");
    }

    private void initializeDatabase() {
        databaseManager = new DatabaseManager(this);

        // Initialize database asynchronously
        databaseManager.initialize().thenAccept(success -> {
            if (success) {
                getLogger().info("Database initialized successfully");
                initializeServices();
            } else {
                getLogger().severe("Failed to initialize database! Disabling plugin...");
                Scheduler.runTask(() -> getServer().getPluginManager().disablePlugin(this));
            }
        }).exceptionally(throwable -> {
            getLogger().log(Level.SEVERE, "Error initializing database", throwable);
            Scheduler.runTask(() -> getServer().getPluginManager().disablePlugin(this));
            return null;
        });
    }

    private void initializeServices() {
        // Initialize chunk limit service
        chunkLimitService = new ChunkLimitService(this, databaseManager);
        getLogger().info("Chunk limit service initialized");

        // Register event listeners
        Bukkit.getPluginManager().registerEvents(new SpawnerLimitListener(this, chunkLimitService), this);
        getLogger().info("Event listeners registered");

        // Start cache cleanup task (hardcoded: 5 minutes = 6000 ticks)
        long cleanupInterval = 6000L; // 5 minutes in ticks
        cacheCleanupTask = Scheduler.runTaskTimerAsync(() -> {
            chunkLimitService.cleanupExpiredCache();
        }, cleanupInterval, cleanupInterval);
        getLogger().info("Cache cleanup task started");
    }

    private void initializeCommands() {
        commandManager = new BrigadierCommandManager(this);
        commandManager.registerCommands();
        getLogger().info("Commands registered");
    }

    @Override
    public void onEnable() {
        instance = this;

        // Check for SmartSpawner API
        checkSmartSpawnerAPI();
        if (!isEnabled()) {
            return;
        }

        // Initialize configuration
        updateConfig();

        // Initialize language system
        initializeLanguageSystem();

        // Check for updates
        checkPluginUpdates();

        // Initialize database (async)
        initializeDatabase();

        // Initialize commands
        initializeCommands();

        getLogger().info("SSA Spawner Limiter has been enabled!");
    }

    @Override
    public void onDisable() {
        // Cancel cache cleanup task
        if (cacheCleanupTask != null) {
            cacheCleanupTask.cancel();
        }

        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("SSA Spawner Limiter has been disabled!");
    }
}
