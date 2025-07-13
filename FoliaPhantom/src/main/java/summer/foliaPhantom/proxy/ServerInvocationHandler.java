package summer.foliaPhantom.proxy;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ServerInvocationHandler implements InvocationHandler {

    private final Server originalServer;
    private final ProxyManager proxyManager;
    private final BukkitScheduler proxiedScheduler;

    public ServerInvocationHandler(Server originalServer, BukkitScheduler proxiedScheduler, ProxyManager proxyManager) {
        this.originalServer = originalServer;
        this.proxiedScheduler = proxiedScheduler;
        this.proxyManager = proxyManager;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        switch (methodName) {
            case "getScheduler":
                return proxiedScheduler;

            case "getWorlds":
                List<World> originalWorlds = (List<World>) method.invoke(originalServer, args);
                return originalWorlds.stream()
                        .map(proxyManager::getProxiedWorld)
                        .collect(Collectors.toList());

            case "getWorld":
                World originalWorld = (World) method.invoke(originalServer, args);
                return proxyManager.getProxiedWorld(originalWorld);

            case "getOnlinePlayers":
                Collection<? extends Player> originalPlayers = (Collection<? extends Player>) method.invoke(originalServer, args);
                return originalPlayers.stream()
                        .map(proxyManager::getProxiedPlayer)
                        .collect(Collectors.toList());

            case "getPlayer":
                Player originalPlayer = (Player) method.invoke(originalServer, args);
                return proxyManager.getProxiedPlayer(originalPlayer);
        }

        return method.invoke(originalServer, args);
    }
}
