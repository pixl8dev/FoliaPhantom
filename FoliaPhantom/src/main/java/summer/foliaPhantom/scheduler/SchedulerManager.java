package summer.foliaPhantom.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import summer.foliaPhantom.FoliaPhantom;
import summer.foliaPhantom.proxy.ProxyManager;
import summer.foliaPhantom.proxy.ServerInvocationHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;

public class SchedulerManager {
    private final Plugin owningPlugin;
    private final Logger logger;

    private Server originalServer;

    public SchedulerManager(Plugin owningPlugin) {
        this.owningPlugin = owningPlugin;
        this.logger = owningPlugin.getLogger();
    }

    public boolean installProxy() {
        try {
            boolean isFolia = FoliaPhantom.isFoliaServer();
            logger.info("[Phantom] Installing proxies for " + (isFolia ? "Folia" : "Non-Folia") + " environment.");

            FoliaSchedulerAdapter schedulerAdapter = new FoliaSchedulerAdapter(this.owningPlugin);
            BukkitScheduler originalScheduler = Bukkit.getScheduler();
            BukkitScheduler proxiedScheduler = (BukkitScheduler) Proxy.newProxyInstance(
                    BukkitScheduler.class.getClassLoader(),
                    new Class<?>[]{BukkitScheduler.class},
                    new FoliaSchedulerProxy(originalScheduler, schedulerAdapter, isFolia, this.owningPlugin)
            );

            ProxyManager proxyManager = new ProxyManager(schedulerAdapter, this.owningPlugin);

            this.originalServer = Bukkit.getServer();
            Server proxiedServer = (Server) Proxy.newProxyInstance(
                this.originalServer.getClass().getClassLoader(),
                this.originalServer.getClass().getInterfaces(),
                new ServerInvocationHandler(this.originalServer, proxiedScheduler, proxyManager)
            );

            setStaticServer(proxiedServer);

            logger.info("[Phantom] Server and Scheduler proxies successfully installed.");
            return true;
        } catch (Exception e) {
            logger.severe("[Phantom] Failed to install proxies: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void restoreOriginalServer() {
        if (this.originalServer != null) {
            try {
                setStaticServer(this.originalServer);
                logger.info("[Phantom] Original Server instance restored successfully.");
            } catch (Exception e) {
                logger.severe("[Phantom] Critical error while restoring original Server: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void setStaticServer(Server server) throws NoSuchFieldException, IllegalAccessException {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
    }
}
