package summer.foliaPhantom.config;

import org.bukkit.configuration.file.FileConfiguration;
import summer.foliaPhantom.FoliaPhantom;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigManager {

    private final FoliaPhantom plugin;

    public ConfigManager(FoliaPhantom plugin) {
        this.plugin = plugin;
    }

    public List<PluginConfig> loadPluginConfigs() {
        FileConfiguration config = plugin.getConfig();
        List<Map<?, ?>> wrappedList = config.getMapList("wrapped-plugins");

        if (wrappedList == null || wrappedList.isEmpty()) {
            plugin.getLogger().warning("[Phantom] config.yml に wrapped-plugins が見つかりません。ラップ対象がありません。");
            return List.of();
        }

        return wrappedList.stream()
                .map(this::parsePluginConfig)
                .collect(Collectors.toList());
    }

    private PluginConfig parsePluginConfig(Map<?, ?> rawEntry) {
        if (rawEntry == null) {
            return null;
        }
        String name = (rawEntry.get("name") instanceof String)
                ? (String) rawEntry.get("name")
                : "<Unknown>";
        String originalPath = (rawEntry.get("original-jar-path") instanceof String)
                ? (String) rawEntry.get("original-jar-path")
                : "";
        String patchedPath = (rawEntry.get("patched-jar-path") instanceof String)
                ? (String) rawEntry.get("patched-jar-path")
                : originalPath;
        Boolean foliaEnabled = (rawEntry.get("folia-enabled") instanceof Boolean)
                ? (Boolean) rawEntry.get("folia-enabled")
                : Boolean.TRUE;

        return new PluginConfig(name, originalPath, patchedPath, foliaEnabled);
    }
}
