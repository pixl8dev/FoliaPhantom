package summer.foliaPhantom.plugin;

import org.bukkit.plugin.Plugin;
import summer.foliaPhantom.config.PluginConfig;
import summer.foliaPhantom.jar.JarPatcher; // Assuming JarPatcher is in this package

import java.io.File;
import java.util.logging.Logger;

public class WrappedPlugin {
    private final PluginConfig config;
    private final PluginLoader pluginLoader;
    private final File dataFolder; // For resolving JAR paths
    private final Logger logger;
    private Plugin bukkitPlugin; // The actual loaded Bukkit/Paper plugin instance

    public WrappedPlugin(PluginConfig config, PluginLoader pluginLoader, File dataFolder, Logger logger) {
        this.config = config;
        this.pluginLoader = pluginLoader;
        this.dataFolder = dataFolder;
        this.logger = logger;
        load();
    }

    private void load() {
        File jarToLoad = prepareJarToLoad();
        if (jarToLoad != null) {
            this.bukkitPlugin = pluginLoader.loadPlugin(config.name(), jarToLoad);
            if (this.bukkitPlugin == null) {
                logger.severe("[Phantom][" + config.name() + "] Failed to load the plugin JAR: " + jarToLoad.getAbsolutePath());
            }
        }
    }

    private File prepareJarToLoad() {
        File originalJar = new File(dataFolder, config.originalJarPath());
        logger.info("[Phantom][" + config.name() + "] Processing plugin. Original JAR: " + originalJar.getAbsolutePath());

        if (!ensureDirectoryExists(originalJar.getParentFile()) || !originalJar.exists()) {
            logger.severe("[Phantom][" + config.name() + "] ERROR: Original JAR file not found at " + originalJar.getPath());
            return null;
        }

        if (config.foliaEnabled()) {
            return createPatchedJar(originalJar);
        } else {
            logger.info("[Phantom][" + config.name() + "] Folia patching disabled. Using original JAR.");
            return originalJar;
        }
    }

    private File createPatchedJar(File originalJar) {
        File patchedJar = new File(dataFolder, config.patchedJarPath());
        if (!ensureDirectoryExists(patchedJar.getParentFile())) {
            logger.warning("[Phantom][" + config.name() + "] Could not create directory for patched JAR. Falling back to original JAR.");
            return originalJar;
        }

        boolean needsPatching = !patchedJar.exists() || originalJar.lastModified() > patchedJar.lastModified();
        if (needsPatching) {
            logger.info("[Phantom][" + config.name() + "] Generating Folia-supported JAR...");
            try {
                JarPatcher.createFoliaSupportedJar(originalJar, patchedJar);
                logger.info("[Phantom][" + config.name() + "] Folia-supported JAR generated: " + patchedJar.length() + " bytes");
                return patchedJar;
            } catch (Exception e) {
                logger.severe("[Phantom][" + config.name() + "] Failed to create patched JAR: " + e.getMessage());
                e.printStackTrace();
                logger.warning("[Phantom][" + config.name() + "] Falling back to original JAR.");
                return originalJar; // Fallback to original
            }
        } else {
            logger.info("[Phantom][" + config.name() + "] Using existing patched JAR: " + patchedJar.getAbsolutePath());
            return patchedJar;
        }
    }

    private boolean ensureDirectoryExists(File directory) {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                logger.warning("[Phantom][" + config.name() + "] Failed to create directory: " + directory.getAbsolutePath());
                return false;
            }
        }
        return true;
    }

    public Plugin getBukkitPlugin() {
        return bukkitPlugin;
    }

    public String getName() {
        return config.name();
    }

    // Optional: method to explicitly unload/cleanup this specific wrapped plugin if needed later
    public void unload() {
        if (bukkitPlugin != null) {
            // Bukkit's PluginManager should handle actual disabling.
            // This class's responsibility is more about the loading phase.
            logger.info("[Phantom][" + getName() + "] Unload requested (Note: actual disabling is via PluginManager).");
        }
        // The PluginLoader is responsible for closing the classloader by plugin name
        pluginLoader.closeClassLoader(getName());
    }
}
