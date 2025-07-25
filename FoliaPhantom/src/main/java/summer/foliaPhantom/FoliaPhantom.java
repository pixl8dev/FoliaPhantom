package summer.foliaPhantom;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.plugin.java.PluginClassLoader;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.logging.Level;

/**
 * FoliaPhantom - 高性能バイトコードパッチ方式
 * 全ての機能を単一ファイルに統合したバージョン。
 * ランタイムパッチシステムの概念を、より堅牢なバイトコード変換アプローチで実現します。
 */
public class FoliaPhantom extends JavaPlugin {

    private final Map<String, Plugin> wrappedPlugins = new ConcurrentHashMap<>();
    private final Map<String, PluginPatcher> patchers = new ConcurrentHashMap<>();

    @Override
    public void onLoad() {
        getLogger().info("[Phantom] === FoliaPhantom onLoad (High-Performance Single-File) ===");
        saveDefaultConfig();

        List<Map<?, ?>> wrappedList = getConfig().getMapList("wrapped-plugins");
        if (wrappedList == null || wrappedList.isEmpty()) {
            getLogger().warning("[Phantom] config.yml に 'wrapped-plugins' が見つかりません。");
            return;
        }

        for (Map<?, ?> config : wrappedList) {
            Object nameObj = config.get("name");
            String name = (nameObj != null) ? nameObj.toString() : "UnknownPlugin";

            Object jarPathObj = config.get("original-jar-path");
            String jarPath = (jarPathObj != null) ? jarPathObj.toString() : "";

            if (jarPath.isEmpty()) {
                getLogger().severe(String.format("[Phantom][%s] 'original-jar-path' が未設定です。スキップします。", name));
                continue;
            }

            try {
                File pluginJar = new File(getDataFolder(), jarPath);
                if (!pluginJar.exists()) {
                    getLogger().severe(String.format("[Phantom][%s] JARファイルが見つかりません: %s", name, pluginJar.getAbsolutePath()));
                    continue;
                }

                PluginPatcher patcher = new PluginPatcher(this, name, pluginJar);
                patchers.put(name, patcher);

                Plugin plugin = patcher.loadPlugin();
                if (plugin != null) {
                    wrappedPlugins.put(name, plugin);
                    getLogger().info(String.format("[Phantom][%s] パッチ適用とロードが完了しました。", name));
                } else {
                    getLogger().severe(String.format("[Phantom][%s] プラグインのロードに失敗しました。", name));
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, String.format("[Phantom][%s] ロード処理中に致命的なエラーが発生しました。", name), e);
            }
        }
    }

    @Override
    public void onEnable() {
        getLogger().info("[Phantom] === FoliaPhantom onEnable ===");
        wrappedPlugins.forEach((name, plugin) -> {
            if (plugin != null && !plugin.isEnabled()) {
                getLogger().info(String.format("[Phantom][%s] Enabling patched plugin...", name));
                getServer().getPluginManager().enablePlugin(plugin);
            }
        });
        getLogger().info("[Phantom] 全てのパッチ済みプラグインを有効化しました。");
    }

    @Override
    public void onDisable() {
        getLogger().info("[Phantom] === FoliaPhantom onDisable ===");
        wrappedPlugins.values().forEach(plugin -> {
            if (plugin != null && plugin.isEnabled()) {
                getServer().getPluginManager().disablePlugin(plugin);
            }
        });
        wrappedPlugins.clear();

        patchers.values().forEach(patcher -> {
            try {
                patcher.close();
            } catch (Exception e) {
                // ignore
            }
        });
        patchers.clear();
        getLogger().info("[Phantom] FoliaPhantom has been disabled.");
    }

    // --- Nested Classes ---

    private static class PluginPatcher implements AutoCloseable {
        private final FoliaPhantom phantom;
        private final String pluginName;
        private final File pluginJar;
        private TransformingPluginClassLoader classLoader;

        public PluginPatcher(FoliaPhantom phantom, String pluginName, File pluginJar) {
            this.phantom = phantom;
            this.pluginName = pluginName;
            this.pluginJar = pluginJar;
        }

        @SuppressWarnings("deprecation")
        public Plugin loadPlugin() throws Exception {
            PluginDescriptionFile desc = phantom.getPluginLoader().getPluginDescription(pluginJar);
            File dataFolder = new File(phantom.getDataFolder().getParentFile(), desc.getName());
            dataFolder.mkdirs();

            this.classLoader = new TransformingPluginClassLoader(
                    (JavaPluginLoader) phantom.getPluginLoader(),
                    phantom.getClass().getClassLoader(),
                    desc,
                    pluginName
            );

            Class<?> mainClass = classLoader.loadClass(desc.getMain());
            Class<? extends JavaPlugin> pluginClass = mainClass.asSubclass(JavaPlugin.class);

            Constructor<? extends JavaPlugin> constructor = pluginClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            JavaPlugin instance = constructor.newInstance();

            // PluginClassLoader が classLoader フィールドを初期化するので、手動での設定は不要。
            // しかし、他のフィールドは手動で設定する必要がある。
            initPluginField(instance, "server", phantom.getServer());
            initPluginField(instance, "dataFolder", dataFolder);
            // onLoadを呼び出す（JavaPluginLoaderのロジックを模倣）
            instance.onLoad();

            return instance;
        }

        private void initPluginField(JavaPlugin plugin, String fieldName, Object value) {
            try {
                Field field = JavaPlugin.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(plugin, value);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                phantom.getLogger().log(Level.WARNING, "Failed to initialize field " + fieldName + " for " + pluginName, e);
            }
        }

        @Override
        public void close() throws IOException {
            if (classLoader != null) {
                classLoader.close();
            }
        }
    }

    // FIX: URLClassLoaderではなくPluginClassLoaderを継承する
    private static class TransformingPluginClassLoader extends PluginClassLoader {
        private static final SchedulerClassTransformer TRANSFORMER = new SchedulerClassTransformer();
        private final String pluginName;
        private final Map<String, Class<?>> transformedCache = new ConcurrentHashMap<>();

        public TransformingPluginClassLoader(JavaPluginLoader loader, ClassLoader parent, PluginDescriptionFile description, String pluginName) throws InvalidPluginException, MalformedURLException {
            super(loader, parent, description);
            this.pluginName = pluginName;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            // すでにロード済みの場合はキャッシュから返す
             if (transformedCache.containsKey(name)) {
                return transformedCache.get(name);
            }
            
            // 変換対象外のクラスは親（PluginClassLoader）に任せる
            if (name.startsWith("java.") || name.startsWith("org.bukkit.") || name.startsWith("io.papermc.") || name.startsWith("com.destroystokyo.paper.") || name.startsWith("net.minecraft.")) {
                return super.findClass(name);
            }

            String path = name.replace('.', '/').concat(".class");
            try (InputStream is = this.getResourceAsStream(path)) {
                if (is == null) {
                    // 親に任せる
                    return super.findClass(name);
                }
                byte[] originalBytes = is.readAllBytes();
                byte[] transformedBytes = TRANSFORMER.transform(originalBytes);
                
                if (originalBytes.length != transformedBytes.length) {
                    Bukkit.getLogger().info(String.format("[Phantom][%s] Patched class: %s", pluginName, name));
                }

                Class<?> definedClass = defineClass(name, transformedBytes, 0, transformedBytes.length);
                transformedCache.put(name, definedClass);
                return definedClass;

            } catch (Throwable e) {
                Bukkit.getLogger().log(Level.WARNING, String.format("[Phantom][%s] クラス '%s' の変換に失敗しました。オリジナルのクラスをロードします。", pluginName, name), e);
                // 変換に失敗した場合、フォールバックとして親のfindClassを呼び出す
                return super.findClass(name);
            }
        }
    }


    private static class SchedulerClassTransformer {
        public byte[] transform(byte[] originalBytes) {
            ClassReader cr = new ClassReader(originalBytes);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new SchedulerClassVisitor(cw);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            return cw.toByteArray();
        }

        private static class SchedulerClassVisitor extends ClassVisitor {
            public SchedulerClassVisitor(ClassVisitor cv) { super(Opcodes.ASM9, cv); }
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                return new SchedulerMethodVisitor(super.visitMethod(access, name, desc, sig, ex));
            }
        }

        private static class SchedulerMethodVisitor extends MethodVisitor {
            public SchedulerMethodVisitor(MethodVisitor mv) { super(Opcodes.ASM9, mv); }
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                if (opcode == Opcodes.INVOKEINTERFACE && "org/bukkit/scheduler/BukkitScheduler".equals(owner)) {
                    String newDesc = "(Lorg/bukkit/scheduler/BukkitScheduler;" + desc.substring(1);
                    if (FoliaPatcher.REPLACEMENT_MAP.containsKey(name + desc)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "summer/foliaPhantom/FoliaPhantom$FoliaPatcher", name, newDesc, false);
                        return;
                    }
                }
                super.visitMethodInsn(opcode, owner, name, desc, isInterface);
            }
        }
    }

    public static final class FoliaPatcher {
        private static final AtomicInteger taskIdCounter = new AtomicInteger(Integer.MAX_VALUE / 2);
        private static final Map<Integer, ScheduledTask> runningTasks = new ConcurrentHashMap<>();
        
        public static final Map<String, Boolean> REPLACEMENT_MAP = Map.ofEntries(
            Map.entry("runTask(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskLater(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;J)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskTimer(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;JJ)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskAsynchronously(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskLaterAsynchronously(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;J)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskTimerAsynchronously(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;JJ)Lorg/bukkit/scheduler/BukkitTask;", true)
        );

        private FoliaPatcher() {}

        private static Location getFallbackLocation() {
            List<World> worlds = Bukkit.getWorlds();
            return worlds.isEmpty() ? null : worlds.get(0).getSpawnLocation();
        }

        private static void cancelTaskById(int taskId) {
            ScheduledTask task = runningTasks.remove(taskId);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }

        // --- Replacement Methods ---
        
        public static BukkitTask runTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable) {
            Location loc = getFallbackLocation();
            ScheduledTask foliaTask = loc != null
                ? Bukkit.getRegionScheduler().run(plugin, loc, t -> runnable.run())
                : Bukkit.getGlobalRegionScheduler().run(plugin, t -> runnable.run());
            int taskId = taskIdCounter.getAndIncrement();
            runningTasks.put(taskId, foliaTask);
            return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
        }

        public static BukkitTask runTaskLater(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay) {
            Location loc = getFallbackLocation();
            ScheduledTask foliaTask = loc != null
                ? Bukkit.getRegionScheduler().runDelayed(plugin, loc, t -> runnable.run(), Math.max(1, delay))
                : Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> runnable.run(), Math.max(1, delay));
            int taskId = taskIdCounter.getAndIncrement();
            runningTasks.put(taskId, foliaTask);
            return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
        }
        
        public static BukkitTask runTaskTimer(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay, long period) {
             Location loc = getFallbackLocation();
             ScheduledTask foliaTask = loc != null
                ? Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc, t -> runnable.run(), Math.max(1, delay), Math.max(1, period))
                : Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> runnable.run(), Math.max(1, delay), Math.max(1, period));
             int taskId = taskIdCounter.getAndIncrement();
             runningTasks.put(taskId, foliaTask);
             return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
        }

        public static BukkitTask runTaskAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable) {
            ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runNow(plugin, t -> runnable.run());
            int taskId = taskIdCounter.getAndIncrement();
            runningTasks.put(taskId, foliaTask);
            return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
        }

        public static BukkitTask runTaskLaterAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay) {
            ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runDelayed(plugin, t -> runnable.run(), delay * 50, TimeUnit.MILLISECONDS);
            int taskId = taskIdCounter.getAndIncrement();
            runningTasks.put(taskId, foliaTask);
            return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
        }

        public static BukkitTask runTaskTimerAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay, long period) {
            ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> runnable.run(), delay * 50, period * 50, TimeUnit.MILLISECONDS);
            int taskId = taskIdCounter.getAndIncrement();
            runningTasks.put(taskId, foliaTask);
            return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
        }
    }

    public static class FoliaBukkitTask implements BukkitTask {
        private final int taskId;
        private final Plugin owner;
        private final IntConsumer cancellationCallback;
        private final boolean isSync;
        private final ScheduledTask underlyingTask;
        
        public FoliaBukkitTask(int taskId, Plugin owner, IntConsumer cc, boolean isSync, ScheduledTask underlyingTask) {
            this.taskId = taskId;
            this.owner = owner;
            this.cancellationCallback = cc;
            this.isSync = isSync;
            this.underlyingTask = underlyingTask;
        }

        @Override public int getTaskId() { return taskId; }
        @Override public Plugin getOwner() { return owner; }
        @Override public boolean isSync() { return isSync; }
        @Override public boolean isCancelled() { return underlyingTask.isCancelled(); }
        
        @Override public void cancel() {
            if (!isCancelled()) {
                cancellationCallback.accept(this.taskId);
            }
        }
    }
}
