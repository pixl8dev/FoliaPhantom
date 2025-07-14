package summer.foliaPhantom;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import summer.foliaPhantom.plugin.PluginLoader;
import summer.foliaPhantom.plugin.WrappedPlugin;
import summer.foliaPhantom.config.ConfigManager;
import summer.foliaPhantom.config.PluginConfig;
import summer.foliaPhantom.scheduler.SchedulerManager;

/**
 * FoliaPhantom – 任意の外部プラグインを Folia（Paper ThreadedRegions）対応に
 * ラップするゴースト・エンジン
 */
public class FoliaPhantom extends JavaPlugin {

    // Stores whether the server environment is Folia-based, determined at startup.
    private static boolean isFoliaServer;
    private SchedulerManager schedulerManager;

    // 設定から読み込んだ各プラグインのインスタンスを保持
    private final Map<String, WrappedPlugin> wrappedPlugins = new ConcurrentHashMap<>();
    // 各プラグイン用に作成した URLClassLoader を保持（後で close() するため）
    private PluginLoader pluginLoader;

    @Override
    public void onLoad() {
        getLogger().info("[Phantom] === FoliaPhantom onLoad ===");
        isFoliaServer = detectServerType();
        saveDefaultConfig();

        try {
            initializeComponents();
            loadWrappedPlugins();
            getLogger().info("[Phantom] onLoad completed.");
        } catch (Exception e) {
            getLogger().severe("[Phantom] FoliaPhantom onLoad 中に例外: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeComponents() {
        this.pluginLoader = new PluginLoader(this);
        this.schedulerManager = new SchedulerManager(this);
        if (this.schedulerManager.installProxy()) {
            getLogger().info("[Phantom] SchedulerManager initialized and proxy installed.");
        } else {
            getLogger().severe("[Phantom] Critical error: Failed to install scheduler proxy. FoliaPhantom will not function correctly.");
        }
    }

    private void loadWrappedPlugins() {
        ConfigManager configManager = new ConfigManager(this);
        List<PluginConfig> pluginConfigs = configManager.loadPluginConfigs();

        for (PluginConfig config : pluginConfigs) {
            getLogger().info("[Phantom][" + config.name() + "] Processing plugin configuration...");
            WrappedPlugin wrappedPlugin = new WrappedPlugin(config, pluginLoader, getDataFolder(), getLogger());
            if (wrappedPlugin.getBukkitPlugin() != null) {
                wrappedPlugins.put(config.name(), wrappedPlugin);
            } else {
                getLogger().severe("[Phantom][" + config.name() + "] Was not added to the list of active wrapped plugins due to loading failure.");
            }
        }
    }

    @Override
    public void onEnable() {
        getLogger().info("[Phantom] === FoliaPhantom onEnable ===");
        enablePlugins();
    }

    private void enablePlugins() {
        if (wrappedPlugins.isEmpty()) {
            getLogger().warning("[Phantom] ラップ対象プラグインが存在しません。FoliaPhantom を無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        for (WrappedPlugin wrappedPlugin : wrappedPlugins.values()) {
            Plugin plugin = wrappedPlugin.getBukkitPlugin();
            if (plugin != null && !plugin.isEnabled()) {
                getLogger().info("[Phantom][" + wrappedPlugin.getName() + "] Enabling wrapped plugin...");
                try {
                    getServer().getPluginManager().enablePlugin(plugin);
                    getLogger().info("[Phantom][" + wrappedPlugin.getName() + "] 有効化完了.");
                } catch (Throwable ex) {
                    getLogger().severe("[Phantom][" + wrappedPlugin.getName() + "] Exception during enablePlugin(): " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }
        getLogger().info("[Phantom] 全てのラップ対象プラグインを有効化しました。");
    }

    @Override
    public void onDisable() {
        getLogger().info("[Phantom] === FoliaPhantom onDisable ===");
        disablePlugins();
        cleanup();
    }

    private void disablePlugins() {
        for (WrappedPlugin wrappedPlugin : wrappedPlugins.values()) {
            Plugin plugin = wrappedPlugin.getBukkitPlugin();
            if (plugin != null && plugin.isEnabled()) {
                getLogger().info("[Phantom][" + wrappedPlugin.getName() + "] Disabling wrapped plugin...");
                try {
                    getServer().getPluginManager().disablePlugin(plugin);
                    getLogger().info("[Phantom][" + wrappedPlugin.getName() + "] 無効化完了.");
                } catch (Exception ex) {
                    getLogger().warning("[Phantom][" + wrappedPlugin.getName() + "] disablePlugin() 例外: " + ex.getMessage());
                }
            }
        }
        wrappedPlugins.clear();
    }

    private void cleanup() {
        if (this.schedulerManager != null) {
            this.schedulerManager.restoreOriginalServer();
        }
        if (this.pluginLoader != null) {
            pluginLoader.closeAllClassLoaders();
            getLogger().info("[Phantom] 全ての ClassLoader をクローズしました。");
        }
    }

    /**
     * 生成した各 URLClassLoader を閉じる
     */

    /**
     * Detects the server type by checking for the existence of a Folia-specific class.
     * This method is called during onLoad to determine if Folia-specific APIs are available.
     * @return true if a Folia-specific class (io.papermc.paper.threadedregions.RegionizedServer) is found, false otherwise.
     */
    private static boolean detectServerType() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            Bukkit.getLogger().info("[Phantom] Detected Folia server environment.");
            return true;
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().info("[Phantom] Detected Non-Folia server environment.");
            return false;
        }
    }

    /**
     * Gets the detected server type.
     * @return true if the server is determined to be a Folia server, false otherwise.
     */
    public static boolean isFoliaServer() {
        return isFoliaServer;
    }
}

