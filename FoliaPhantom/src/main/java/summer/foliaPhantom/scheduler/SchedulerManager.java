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
        try {
            boolean isFolia = FoliaPhantom.isFoliaServer();
            logger.info("[Phantom] Installing scheduler for " + (isFolia ? "Folia" : "Non-Folia") + " environment.");

            FoliaSchedulerAdapter schedulerAdapter = new FoliaSchedulerAdapter(this.owningPlugin);
            this.originalScheduler = Bukkit.getScheduler();
            BukkitScheduler proxiedScheduler = (BukkitScheduler) Proxy.newProxyInstance(
                    this.owningPlugin.getClass().getClassLoader(),
                    new Class<?>[]{BukkitScheduler.class},
                    new FoliaSchedulerProxy(this.originalScheduler, schedulerAdapter, isFolia, this.owningPlugin)
            );

            Server server = Bukkit.getServer();
            this.schedulerField = server.getClass().getDeclaredField("scheduler");
            this.schedulerField.setAccessible(true);
            this.schedulerField.set(server, proxiedScheduler);


            logger.info("[Phantom] Scheduler proxy successfully installed.");
            return true;
        } catch (Exception e) {
            logger.severe("[Phantom] Failed to install scheduler proxy: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void restoreOriginalServer() {
        if (this.originalScheduler != null && this.schedulerField != null) {
            try {
                this.schedulerField.set(Bukkit.getServer(), this.originalScheduler);
                logger.info("[Phantom] Original scheduler restored successfully.");
            } catch (Exception e) {
                logger.severe("[Phantom] Critical error while restoring original scheduler: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
