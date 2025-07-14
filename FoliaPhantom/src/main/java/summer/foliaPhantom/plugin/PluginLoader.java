package summer.foliaPhantom.plugin;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin; // For getLogger, or pass Logger instance

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PluginLoader {
    private final PluginManager pluginManager;
    private final Logger logger;
    private final File dataFolder; // To resolve relative paths for JARs if needed, though PluginConfig has full paths

    // Each loaded plugin will have its own classloader
    private final Map<String, URLClassLoader> pluginClassLoaders = new ConcurrentHashMap<>();

    public PluginLoader(JavaPlugin hostingPlugin) {
        this.pluginManager = hostingPlugin.getServer().getPluginManager();
        this.logger = hostingPlugin.getLogger();
        this.dataFolder = hostingPlugin.getDataFolder(); // Useful for context if ever needed
    }

    /**
     * Loads a plugin JAR using a dedicated URLClassLoader.
     * The ClassLoader is stored for later cleanup.
     */
    public Plugin loadPlugin(String pluginName, File jarFile) {
        if (!jarFile.exists()) {
            logger.severe("[Phantom][" + pluginName + "] JAR file not found for loading: " + jarFile.getAbsolutePath());
            return null;
        }

        URLClassLoader loader;
        try {
            URL url = jarFile.toURI().toURL();
            loader = new URLClassLoader(new URL[]{url}, getClass().getClassLoader());
            pluginClassLoaders.put(pluginName, loader);

            Thread.currentThread().setContextClassLoader(loader);
            Plugin plugin = pluginManager.loadPlugin(jarFile);

            if (plugin != null) {
                logger.info("[Phantom][" + pluginName + "] Plugin loaded successfully: " + plugin.getName() + " v" + plugin.getDescription().getVersion());
                return plugin;
            } else {
                logger.severe("[Phantom][" + pluginName + "] Failed to load plugin from JAR: " + jarFile.getName());
                closeClassLoader(pluginName);
                return null;
            }
        } catch (Exception e) {
            logger.severe("[Phantom][" + pluginName + "] Exception during PluginManager.loadPlugin(): " + e.getMessage());
            e.printStackTrace();
            closeClassLoader(pluginName); // Ensure cleanup on failure
            return null;
        } finally {
            // Reset context class loader in a finally block to guarantee execution
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        }
    }

    /**
     * Closes and removes the ClassLoader for a specific plugin.
     */
    public void closeClassLoader(String pluginName) {
        URLClassLoader loader = pluginClassLoaders.remove(pluginName);
        if (loader != null) {
            try {
                loader.close();
                logger.info("[Phantom][" + pluginName + "] ClassLoader closed.");
            } catch (IOException e) {
                logger.warning("[Phantom][" + pluginName + "] Error closing ClassLoader: " + e.getMessage());
            }
        }
    }

    /**
     * Closes all managed ClassLoaders.
     */
    public void closeAllClassLoaders() {
        // Use an iterator to safely remove elements during iteration
        pluginClassLoaders.keySet().removeIf(pluginName -> {
            closeClassLoader(pluginName);
            return true; // Mark for removal
        });
        logger.info("[Phantom] All managed plugin ClassLoaders have been requested to close.");
    }

    public Map<String, URLClassLoader> getPluginClassLoaders() {
         return pluginClassLoaders;
    }
}
