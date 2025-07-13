package summer.foliaPhantom.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import summer.foliaPhantom.FoliaPhantom;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;

public class SchedulerManager {
    private final Plugin owningPlugin;
    private final Logger logger;
    private FoliaSchedulerAdapter schedulerAdapter;

    private Object serverInstance;
    private Field schedulerField;
    private BukkitScheduler originalScheduler;
    private BukkitScheduler proxiedScheduler;

    public SchedulerManager(Plugin owningPlugin) {
        this.owningPlugin = owningPlugin;
        this.logger = owningPlugin.getLogger();
    }

    public boolean installProxy() {
        try {
            boolean isFolia = FoliaPhantom.isFoliaServer();
            logger.info("[Phantom] Installing scheduler proxy for " + (isFolia ? "Folia" : "Non-Folia") + " environment.");

            this.schedulerAdapter = new FoliaSchedulerAdapter(this.owningPlugin);
            this.originalScheduler = Bukkit.getScheduler();
            this.proxiedScheduler = (BukkitScheduler) Proxy.newProxyInstance(
                    BukkitScheduler.class.getClassLoader(),
                    new Class<?>[]{BukkitScheduler.class},
                    new FoliaSchedulerProxy(this.originalScheduler, this.schedulerAdapter, isFolia)
            );

            this.serverInstance = Bukkit.getServer();
            this.schedulerField = findSchedulerField(serverInstance.getClass());

            if (this.schedulerField == null) {
                throw new NoSuchFieldException("Failed to find BukkitScheduler field in Server instance or its superclasses.");
            }

            this.schedulerField.setAccessible(true);
            this.schedulerField.set(serverInstance, this.proxiedScheduler);

            logger.info("[Phantom] Folia Scheduler Proxy successfully installed.");
            return true;
        } catch (Exception e) {
            logger.severe("[Phantom] Failed to install Folia Scheduler Proxy: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void restoreOriginalScheduler() {
        if (serverInstance == null || schedulerField == null || originalScheduler == null) {
            logger.warning("[Phantom] SchedulerManager not fully initialized or already restored. Cannot restore scheduler.");
            return;
        }
        try {
            Object currentScheduler = schedulerField.get(serverInstance);
            if (currentScheduler == proxiedScheduler) {
                schedulerField.set(serverInstance, originalScheduler);
                logger.info("[Phantom] Original BukkitScheduler restored successfully.");
            } else {
                logger.warning("[Phantom] Current scheduler is not our proxy. Original scheduler not restored to prevent conflicts.");
            }
        } catch (IllegalAccessException e) {
            logger.severe("[Phantom] Critical error while restoring original scheduler: " + e.getMessage());
            e.printStackTrace();
        } finally {
            clearSchedulerReferences();
        }
    }

    private void clearSchedulerReferences() {
        this.originalScheduler = null;
        this.proxiedScheduler = null;
        this.schedulerAdapter = null;
    }

    private Field findSchedulerField(Class<?> clazz) {
        if (clazz == null) return null;
        for (Field f : clazz.getDeclaredFields()) {
            if (BukkitScheduler.class.isAssignableFrom(f.getType())) {
                return f;
            }
        }
        // Recursively check superclasses
        return findSchedulerField(clazz.getSuperclass());
    }

    // Getter for the adapter if other parts of FoliaPhantom need it (e.g. for direct Folia scheduling)
    // This might not be needed if all scheduling is meant to go through the proxy.
    public FoliaSchedulerAdapter getSchedulerAdapter() {
        return schedulerAdapter;
    }
}
