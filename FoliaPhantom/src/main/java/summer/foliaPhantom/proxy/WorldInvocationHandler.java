package summer.foliaPhantom.proxy;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import summer.foliaPhantom.scheduler.FoliaSchedulerAdapter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import java.util.stream.Collectors;
import java.util.List;

public class WorldInvocationHandler implements InvocationHandler {

    private final World originalWorld;
    private final FoliaSchedulerAdapter scheduler;
    private final Plugin plugin;
    private final Logger logger;
    private final ProxyManager proxyManager;


    public WorldInvocationHandler(World originalWorld, FoliaSchedulerAdapter scheduler, Plugin plugin, ProxyManager proxyManager) {
        this.originalWorld = originalWorld;
        this.scheduler = scheduler;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.proxyManager = proxyManager;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // Foliaで非推奨/危険なメソッドをハンドル
        switch (methodName) {
            case "getEntities":
            case "getPlayers":
                logger.warning("[Phantom] " + methodName + " is called on World. Wrapping in RegionScheduler and proxying results. This may impact performance.");
                CompletableFuture<Object> future = new CompletableFuture<>();
                scheduler.runRegionSyncTask(() -> {
                    try {
                        Object rawResult = method.invoke(originalWorld, args);
                        if (rawResult instanceof List) {
                            List<?> rawList = (List<?>) rawResult;
                            future.complete(rawList.stream()
                                    .filter(e -> e instanceof org.bukkit.entity.Entity)
                                    .map(e -> proxyManager.getProxiedEntity((org.bukkit.entity.Entity) e))
                                    .collect(Collectors.toList()));
                        } else {
                            future.complete(rawResult);
                        }
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                }, originalWorld.getSpawnLocation());
                return future.get();

            case "loadChunk":
            case "unloadChunk":
                if (args.length >= 2 && args[0] instanceof Integer && args[1] instanceof Integer) {
                    int x = (int) args[0];
                    int z = (int) args[1];
                    logger.info("[Phantom] Wrapping " + methodName + " in RegionScheduler for chunk (" + x + ", " + z + ").");

                    CompletableFuture<Object> chunkFuture = new CompletableFuture<>();
                    scheduler.runRegionSyncTask(() -> {
                        try {
                            chunkFuture.complete(method.invoke(originalWorld, args));
                        } catch (Throwable t) {
                            chunkFuture.completeExceptionally(t);
                        }
                    }, originalWorld, x, z);
                    return chunkFuture.get();
                }
                break;

            // 他にもラップすべきメソッドがあればここに追加...
        }

        // 上記以外のメソッドはそのまま元のWorldオブジェクトに委譲
        return method.invoke(originalWorld, args);
    }
}
