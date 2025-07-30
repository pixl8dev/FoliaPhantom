package summer.foliaPhantom;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import summer.foliaPhantom.patchers.FoliaPatcher.FoliaBukkitTask;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.logging.Level;

public class FoliaPhantom extends JavaPlugin {

    private final List<Plugin> wrappedPlugins = new ArrayList<>();
    private final List<File> tempJarFiles = new ArrayList<>();
    private PluginPatcher pluginPatcher;

    @Override
    public void onLoad() {
        getLogger().info("[Phantom] === FoliaPhantom onLoad (JAR Repackaging) ===");
        saveDefaultConfig();
        this.pluginPatcher = new PluginPatcher(getLogger());

        if (!getConfig().getBoolean("auto-scan-plugins.enabled", false)) {
            getLogger().info("[Phantom] Auto-scanning is disabled. No plugins will be patched.");
            return;
        }

        File tempDir = new File(getDataFolder(), "temp-jars");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        List<String> excludedPlugins = getConfig().getStringList("auto-scan-plugins.excluded-plugins");
        File pluginsDir = new File("./plugins");

        File[] pluginJars = pluginsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (pluginJars == null) {
            getLogger().warning("[Phantom] Could not list files in plugins directory.");
            return;
        }

        for (File originalJar : pluginJars) {
            try {
                String pluginName = PluginPatcher.getPluginNameFromJar(originalJar);
                if (pluginName == null || excludedPlugins.contains(pluginName) || pluginName.equals(this.getDescription().getName())) {
                    getLogger().info(String.format("[Phantom] Skipping excluded, invalid, or self plugin: %s", originalJar.getName()));
                    continue;
                }

                if (PluginPatcher.isFoliaSupported(originalJar)) {
                    getLogger().info(String.format("[Phantom] Plugin '%s' is already Folia-supported. Skipping.", pluginName));
                    continue;
                }

                File tempJar = new File(tempDir, "phantom-" + originalJar.getName());
                tempJarFiles.add(tempJar);

                pluginPatcher.patchPlugin(originalJar, tempJar);

                PluginManager pluginManager = Bukkit.getPluginManager();
                Plugin plugin = pluginManager.loadPlugin(tempJar);
                if (plugin != null) {
                    wrappedPlugins.add(plugin);
                    getLogger().info(String.format("[Phantom][%s] Patched plugin loaded successfully.", pluginName));
                } else {
                    getLogger().severe(String.format("[Phantom][%s] Failed to load the patched plugin.", pluginName));
                }

            } catch (Exception e) {
                getLogger().log(Level.SEVERE, String.format("[Phantom] Failed to process plugin %s", originalJar.getName()), e);
            }
        }
    }

    @Override
    public void onEnable() {
        getLogger().info("[Phantom] === FoliaPhantom onEnable ===");
        for (Plugin plugin : wrappedPlugins) {
            if (plugin != null && !plugin.isEnabled()) {
                getLogger().info(String.format("[Phantom][%s] Enabling patched plugin...", plugin.getName()));
                getServer().getPluginManager().enablePlugin(plugin);
            }
        }
        getLogger().info("[Phantom] All patched plugins have been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("[Phantom] === FoliaPhantom onDisable ===");
        wrappedPlugins.clear();

        getLogger().info("[Phantom] Cleaning up temporary files...");
        for (File file : tempJarFiles) {
            if (file.exists() && !file.delete()) {
                getLogger().warning("[Phantom] Could not delete temporary file: " + file.getPath());
                file.deleteOnExit();
            }
        }
        tempJarFiles.clear();
        getLogger().info("[Phantom] FoliaPhantom has been disabled.");
    }
}
