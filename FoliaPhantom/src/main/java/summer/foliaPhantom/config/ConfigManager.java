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
        String name = getString(rawEntry, "name", "<Unknown>");
        String originalPath = getString(rawEntry, "original-jar-path", "");
        String patchedPath = getString(rawEntry, "patched-jar-path", originalPath);
        boolean foliaEnabled = getBoolean(rawEntry, "folia-enabled", true);

        return new PluginConfig(name, originalPath, patchedPath, foliaEnabled);
    }

    private String getString(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        return (value instanceof String) ? (String) value : defaultValue;
    }

    private boolean getBoolean(Map<?, ?> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        return (value instanceof Boolean) ? (Boolean) value : defaultValue;
    }
}
