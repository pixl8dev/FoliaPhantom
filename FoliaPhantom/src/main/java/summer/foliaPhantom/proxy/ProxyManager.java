package summer.foliaPhantom.proxy;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import summer.foliaPhantom.scheduler.FoliaSchedulerAdapter;

import java.lang.reflect.Proxy;

public class ProxyManager {

    private final FoliaSchedulerAdapter scheduler;
    private final Plugin plugin;

    public ProxyManager(FoliaSchedulerAdapter scheduler, Plugin plugin) {
        this.scheduler = scheduler;
        this.plugin = plugin;
    }

    public World getProxiedWorld(World originalWorld) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                new WorldInvocationHandler(originalWorld, scheduler, plugin, this)
        );
    }

    public <T extends org.bukkit.entity.Entity> T getProxiedEntity(T originalEntity) {
        if (originalEntity == null) {
            return null;
        }

        InvocationHandler handler = new EntityInvocationHandler(originalEntity, scheduler, plugin);

        @SuppressWarnings("unchecked")
        T proxiedEntity = (T) Proxy.newProxyInstance(
                originalEntity.getClass().getClassLoader(),
                originalEntity.getClass().getInterfaces(),
                handler
        );
        return proxiedEntity;
    }

    public org.bukkit.entity.Player getProxiedPlayer(org.bukkit.entity.Player originalPlayer) {
        return getProxiedEntity(originalPlayer);
    }
}
