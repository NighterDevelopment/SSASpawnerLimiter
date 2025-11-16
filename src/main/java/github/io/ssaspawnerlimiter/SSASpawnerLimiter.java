package github.io.ssaspawnerlimiter;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.plugin.java.JavaPlugin;
import io.github.pluginlangcore.language.LanguageManager;
import io.github.pluginlangcore.language.MessageService;
import io.github.pluginlangcore.updater.LanguageUpdater;

import java.util.List;

@Getter
@Accessors(chain = false)
public final class SSASpawnerLimiter extends JavaPlugin {
    private LanguageManager languageManager;
    private MessageService messageService;

    @Override
    public void onEnable() {
        new LanguageUpdater(this, List.of("en_US"));
        languageManager = new LanguageManager(this);
        messageService = new MessageService(this, languageManager);
        languageManager = new LanguageManager(this,
                LanguageManager.LanguageFileType.MESSAGES
        );
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
