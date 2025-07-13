package summer.foliaPhantom.proxy;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import summer.foliaPhantom.scheduler.FoliaSchedulerAdapter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class EntityInvocationHandler implements InvocationHandler {

    private final Entity originalEntity;
    private final FoliaSchedulerAdapter scheduler;
    private final Plugin plugin;
    private final Logger logger;

    public EntityInvocationHandler(Entity originalEntity, FoliaSchedulerAdapter scheduler, Plugin plugin) {
        this.originalEntity = originalEntity;
        this.scheduler = scheduler;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // Foliaで特に注意が必要なメソッドをハンドル
        switch (methodName) {
            case "teleport":
                if (args.length > 0 && args[0] instanceof Location) {
                    logger.info("[Phantom] Wrapping Entity#teleport in RegionScheduler.");
                    CompletableFuture<Boolean> future = new CompletableFuture<>();
                    // テレポート先のLocationでスケジュールするべきだが、まずはエンティティの現在地で実行
                    scheduler.runRegionSyncTask(() -> {
                        try {
                            future.complete((Boolean) method.invoke(originalEntity, args));
                        } catch (Throwable t) {
                            future.completeExceptionally(t);
                        }
                    }, originalEntity.getLocation());
                    return future.get(); // 結果を待つ
                }
                break;

            case "damage":
            case "setHealth":
            case "remove":
                logger.info("[Phantom] Wrapping Entity#" + methodName + " in RegionScheduler.");
                CompletableFuture<Object> future = new CompletableFuture<>();
                scheduler.runRegionSyncTask(() -> {
                    try {
                        future.complete(method.invoke(originalEntity, args));
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                }, originalEntity.getLocation());
                // 戻り値がある場合とない場合（void）を考慮
                if (method.getReturnType().equals(Void.TYPE)) {
                    future.get();
                    return null;
                }
                return future.get();

            // 他の書き込み系メソッドも同様に追加...
        }

        // 上記以外のメソッドはそのまま元のEntityオブジェクトに委譲
        return method.invoke(originalEntity, args);
    }
}
