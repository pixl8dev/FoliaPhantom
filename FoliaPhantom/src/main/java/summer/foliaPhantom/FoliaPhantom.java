package summer.foliaPhantom;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
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
            // FIX: Handle Map<?, ?> safely to avoid "incompatible types" error.
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

                // 各プラグインに専用のパッチャーを用意
                PluginPatcher patcher = new PluginPatcher(this, name, pluginJar);
                patchers.put(name, patcher);

                // パッチを適用しつつプラグインをロード
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
            if (plugin.isEnabled()) return;
            getLogger().info(String.format("[Phantom][%s] Enabling patched plugin...", name));
            getServer().getPluginManager().enablePlugin(plugin);
        });
        getLogger().info("[Phantom] 全てのパッチ済みプラグインを有効化しました。");
    }

    @Override
    public void onDisable() {
        getLogger().info("[Phantom] === FoliaPhantom onDisable ===");
        wrappedPlugins.forEach((name, plugin) -> {
            if (!plugin.isEnabled()) return;
            getLogger().info(String.format("[Phantom][%s] Disabling patched plugin...", name));
            getServer().getPluginManager().disablePlugin(plugin);
        });
        wrappedPlugins.clear();

        patchers.forEach((name, patcher) -> {
            try {
                patcher.close();
            } catch (Exception e) {
                getLogger().warning(String.format("[Phantom][%s] Patcherのクリーンアップ中にエラー: %s", name, e.getMessage()));
            }
        });
        patchers.clear();
        getLogger().info("[Phantom] FoliaPhantom has been disabled.");
    }

    // --- Nested Classes ---

    /**
     * 特定のプラグインに対するパッチ処理（クラスローダー管理、変換、ロード）をカプセル化します。
     */
    private static class PluginPatcher implements AutoCloseable {
        private final FoliaPhantom phantom;
        private final String pluginName;
        private final File pluginJar;
        private final PatchingClassLoader classLoader;
        private final SchedulerClassTransformer transformer = new SchedulerClassTransformer();
        private final Map<String, byte[]> transformedCache = new ConcurrentHashMap<>();

        public PluginPatcher(FoliaPhantom phantom, String pluginName, File pluginJar) throws IOException {
            this.phantom = phantom;
            this.pluginName = pluginName;
            this.pluginJar = pluginJar;
            this.classLoader = new PatchingClassLoader(new URL[]{pluginJar.toURI().toURL()}, phantom.getClass().getClassLoader());
        }

        @SuppressWarnings("deprecation") // FIX: Suppress warning for getPluginLoader(), as it's necessary for this use case.
        public Plugin loadPlugin() throws Exception {
            PluginDescriptionFile desc = phantom.getPluginLoader().getPluginDescription(pluginJar);
            File dataFolder = new File(phantom.getDataFolder().getParentFile(), desc.getName());
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            Class<?> mainClass = classLoader.loadClass(desc.getMain());
            Class<? extends JavaPlugin> pluginClass = mainClass.asSubclass(JavaPlugin.class);

            Constructor<? extends JavaPlugin> constructor = pluginClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            JavaPlugin instance = constructor.newInstance();

            // リフレクションを使ってプラグインの内部フィールドを初期化 (Bukkitのローダーの模倣)
            initPluginField(instance, "loader", phantom.getPluginLoader());
            initPluginField(instance, "server", phantom.getServer());
            initPluginField(instance, "description", desc);
            initPluginField(instance, "dataFolder", dataFolder);
            initPluginField(instance, "classLoader", classLoader);
            
            // onLoadを呼び出す
            instance.onLoad();

            return instance;
        }

        private void initPluginField(JavaPlugin plugin, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
            Field field = JavaPlugin.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(plugin, value);
        }

        @Override
        public void close() throws IOException {
            if (classLoader != null) {
                classLoader.close();
            }
            transformedCache.clear();
        }

        private class PatchingClassLoader extends URLClassLoader {
            public PatchingClassLoader(URL[] urls, ClassLoader parent) {
                super(urls, parent);
            }

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded != null) return loaded;

                    // 変換対象外のクラスは親に委譲 (高速化)
                    if (name.startsWith("java.") || name.startsWith("org.bukkit.") || name.startsWith("io.papermc.") || name.startsWith("com.destroystokyo.paper.") || name.startsWith("net.minecraft.")) {
                        return super.loadClass(name, resolve);
                    }
                    
                    // キャッシュを確認
                    if (transformedCache.containsKey(name)) {
                        byte[] bytes = transformedCache.get(name);
                        return defineClass(name, bytes, 0, bytes.length);
                    }

                    try (InputStream is = getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (is == null) {
                            return super.loadClass(name, resolve);
                        }
                        byte[] originalBytes = is.readAllBytes();
                        byte[] transformedBytes = transformer.transform(originalBytes);
                        
                        if (originalBytes.length != transformedBytes.length) {
                             phantom.getLogger().info(String.format("[%s] Patched class: %s", pluginName, name));
                        }
                        
                        transformedCache.put(name, transformedBytes);
                        Class<?> definedClass = defineClass(name, transformedBytes, 0, transformedBytes.length);
                        if (resolve) {
                            resolveClass(definedClass);
                        }
                        return definedClass;
                    } catch (Throwable e) {
                        phantom.getLogger().log(Level.WARNING, String.format("[%s] クラス '%s' の変換に失敗しました。オリジナルのクラスをロードします。", pluginName, name), e);
                        return super.loadClass(name, resolve);
                    }
                }
            }
        }
    }

    /**
     * ASMを使用してバイトコードを実際に変換するクラス。
     */
    private static class SchedulerClassTransformer {
        public byte[] transform(byte[] originalBytes) {
            ClassReader cr = new ClassReader(originalBytes);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
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
                    // BukkitSchedulerのメソッド呼び出しを検知
                    String newDesc = "(Lorg/bukkit/scheduler/BukkitScheduler;" + desc.substring(1);
                    if (FoliaPatcher.REPLACEMENT_MAP.containsKey(name + desc)) {
                        // マップに存在するメソッドなら、静的メソッド呼び出しに置き換える
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "summer/foliaPhantom/FoliaPhantom$FoliaPatcher", name, newDesc, false);
                        return;
                    }
                }
                super.visitMethodInsn(opcode, owner, name, desc, isInterface);
            }
        }
    }

    /**
     * バイトコードの書き換え先となる静的メソッド群。
     * このクラスは必ず public static でなければならない。
     * BukkitSchedulerの呼び出しは、Foliaの対応するScheduler APIに変換される。
     */
    public static final class FoliaPatcher {
        private static final AtomicInteger taskIdCounter = new AtomicInteger(Integer.MAX_VALUE / 2);
        private static final Map<Integer, ScheduledTask> runningTasks = new ConcurrentHashMap<>();
        
        // 置換対象のメソッドシグネチャをキーとするマップ
        public static final Map<String, Boolean> REPLACEMENT_MAP = Map.ofEntries(
            Map.entry("runTask(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskLater(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;J)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskTimer(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;JJ)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskAsynchronously(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskLaterAsynchronously(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;J)Lorg/bukkit/scheduler/BukkitTask;", true),
            Map.entry("runTaskTimerAsynchronously(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;JJ)Lorg/bukkit/scheduler/BukkitTask;", true)
        );

        private FoliaPatcher() {}

        // 実行リージョンを取得するためのフォールバックロケーションを取得する
        private static Location getFallbackLocation() {
            List<World> worlds = Bukkit.getWorlds();
            return worlds.isEmpty() ? null : worlds.get(0).getSpawnLocation();
        }

        // タスクIDでタスクをキャンセルする
        private static void cancelTaskById(int taskId) {
            ScheduledTask task = runningTasks.remove(taskId);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }

        // --- Replacement Methods (Synchronous) ---
        
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
                // FIX: Removed the 'loc' argument from the GlobalRegionScheduler call, as it does not take a location.
                : Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> runnable.run(), Math.max(1, delay), Math.max(1, period));
             int taskId = taskIdCounter.getAndIncrement();
             runningTasks.put(taskId, foliaTask);
             return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
        }

        // --- Replacement Methods (Asynchronous) ---

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

    /**
     * BukkitTaskインターフェースを満たすためのラッパークラス。
     * FoliaのScheduledTaskをラップし、BukkitのAPIとの互換性を保つ。
     */
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
