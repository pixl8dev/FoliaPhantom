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
    private BukkitScheduler originalScheduler;
    private Field schedulerField;


    public SchedulerManager(Plugin owningPlugin) {
        this.owningPlugin = owningPlugin;
        this.logger = owningPlugin.getLogger();
    }

    public boolean installProxy() {
        if (this.originalScheduler != null) {
            logger.warning("[Phantom] Scheduler proxy is already installed.");
            return true;
        }
        try {
            boolean isFolia = FoliaPhantom.isFoliaServer();
            logger.info("[Phantom] Installing scheduler for " + (isFolia ? "Folia" : "Non-Folia") + " environment.");

            this.originalScheduler = Bukkit.getScheduler();
            FoliaSchedulerAdapter schedulerAdapter = new FoliaSchedulerAdapter(this.owningPlugin);
            BukkitScheduler proxiedScheduler = (BukkitScheduler) Proxy.newProxyInstance(
                    this.owningPlugin.getClass().getClassLoader(),
                    new Class<?>[]{BukkitScheduler.class},
                    new FoliaSchedulerProxy(this.originalScheduler, schedulerAdapter, isFolia, this.owningPlugin)
            );

            Server server = Bukkit.getServer();
            this.schedulerField = server.getClass().getDeclaredField("scheduler");
            this.schedulerField.setAccessible(true);

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(this.schedulerField, this.schedulerField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

            this.schedulerField.set(server, proxiedScheduler);

            logger.info("[Phantom] Scheduler proxy successfully installed.");
            return true;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.severe("[Phantom] CRITICAL: Failed to install scheduler proxy due to reflection error. FoliaPhantom cannot function.");
            logger.severe("[Phantom] This might be due to an unsupported server version. Disabling FoliaPhantom is recommended.");
            e.printStackTrace();
            this.originalScheduler = null; // Reset on failure
            return false;
        } catch (Exception e) {
            logger.severe("[Phantom] An unexpected error occurred while installing the scheduler proxy: " + e.getMessage());
            e.printStackTrace();
            this.originalScheduler = null; // Reset on failure
            return false;
        }
    }

    public void restoreOriginalServer() {
        if (this.originalScheduler == null || this.schedulerField == null) {
            logger.info("[Phantom] Original scheduler was not replaced or already restored.");
            return;
        }
        try {
            this.schedulerField.set(Bukkit.getServer(), this.originalScheduler);
            logger.info("[Phantom] Original scheduler restored successfully.");
        } catch (IllegalAccessException e) {
            logger.severe("[Phantom] CRITICAL: Failed to restore original scheduler due to a security manager or reflection issue.");
            e.printStackTrace();
        } finally {
            this.originalScheduler = null;
            this.schedulerField = null;
        }
    }
}
