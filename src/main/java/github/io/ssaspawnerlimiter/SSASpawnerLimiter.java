package github.io.ssaspawnerlimiter;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.pluginlangcore.language.LanguageManager;
import io.github.pluginlangcore.language.MessageService;
import io.github.pluginlangcore.updater.LanguageUpdater;
import io.github.pluginupdatecore.updater.UpdateChecker;
import io.github.pluginupdatecore.updater.ConfigUpdater;
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.SmartSpawnerProvider;

import java.util.List;

@Getter
@Accessors(chain = false)
public final class SSASpawnerLimiter extends JavaPlugin {
    private final static String MODRINTH_PROJECT_ID = "";
    private LanguageManager languageManager;
    private MessageService messageService;
    private SmartSpawnerAPI api;

    private void checkSmartSpawnerAPI() {
        api = SmartSpawnerProvider.getAPI();
        if (api == null) {
            getLogger().warning("SmartSpawner not found!");
        } else {
            getLogger().info("SmartSpawner found, SSA Spawner Limiter is operational!");
        }
    }

    private void checkPluginUpdates() {
        UpdateChecker updateChecker = new UpdateChecker(this, MODRINTH_PROJECT_ID);
        updateChecker.checkForUpdates();
    }

    private void updateConfig() {
        ConfigUpdater configUpdater = new ConfigUpdater(this);
        configUpdater.checkAndUpdateConfig();
        reloadConfig();
    }

    private void initializeLanguageSystem() {
        new LanguageUpdater(this, List.of("en_US"));
        languageManager = new LanguageManager(this);
        messageService = new MessageService(this, languageManager);
        languageManager = new LanguageManager(this,
                LanguageManager.LanguageFileType.MESSAGES
        );
    }

    @Override
    public void onEnable() {
        checkSmartSpawnerAPI();
        checkPluginUpdates();
        updateConfig();
        initializeLanguageSystem();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
