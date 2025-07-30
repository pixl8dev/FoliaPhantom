package summer.foliaPhantom;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * FoliaPhantom - 高性能バイトコードパッチ方式（JAR再パッケージング版）
 * 全ての機能を単一ファイルに統合したバージョン。
 * サーバー起動時にパッチ済みの一時JARを生成し、公式ローダーにロードさせることで問題を解決します。
 */
public class FoliaPhantom extends JavaPlugin {

    private final List<Plugin> wrappedPlugins = new ArrayList<>();
    private final List<File> tempJarFiles = new ArrayList<>();
    private SchedulerClassTransformer transformer;

    @Override
    public void onLoad() {
        getLogger().info("[Phantom] === FoliaPhantom onLoad (JAR Repackaging) ===");
        this.transformer = new SchedulerClassTransformer(getLogger());
        saveDefaultConfig();

        File tempDir = new File(getDataFolder(), "temp-jars");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

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

            File originalJar = new File(getDataFolder(), jarPath);
            if (!originalJar.exists()) {
                getLogger().severe(String.format("[Phantom][%s] JARファイルが見つかりません: %s", name, originalJar.getAbsolutePath()));
                continue;
            }
            
            try {
                // 一時的なパッチ済みJARを作成
                File tempJar = File.createTempFile("phantom-" + name + "-", ".jar", tempDir);
                tempJarFiles.add(tempJar);

                getLogger().info(String.format("[Phantom][%s] Creating patched JAR at: %s", name, tempJar.getPath()));
                createPatchedJar(originalJar.toPath(), tempJar.toPath());

                // パッチ済みJARをロード
                PluginManager pluginManager = Bukkit.getPluginManager();
                Plugin plugin = pluginManager.loadPlugin(tempJar);
                if (plugin != null) {
                    wrappedPlugins.add(plugin);
                    getLogger().info(String.format("[Phantom][%s] Patched plugin loaded successfully.", name));
                } else {
                     getLogger().severe(String.format("[Phantom][%s] Failed to load the patched plugin.", name));
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, String.format("[Phantom][%s] ロード処理中に致命的なエラーが発生しました。", name), e);
            }
        }
    }

    private void createPatchedJar(Path source, Path destination) throws IOException {
        try (
            ZipInputStream zis = new ZipInputStream(Files.newInputStream(source));
            ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(destination))
        ) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));

                if (!entry.isDirectory()) {
                    if (entry.getName().equals("plugin.yml")) {
                        // plugin.ymlを読み込み、folia-supportedフラグを追加/修正して書き込む
                        String originalYml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                        String modifiedYml = addFoliaSupportedFlag(originalYml);
                        zos.write(modifiedYml.getBytes(StandardCharsets.UTF_8));
                    } else if (entry.getName().endsWith(".class")) {
                        // .classファイルを変換して書き込む
                        byte[] originalBytes = zis.readAllBytes();
                        byte[] transformedBytes = transformer.transform(originalBytes);
                        zos.write(transformedBytes);
                    } else {
                        // その他のファイルはそのままコピー
                        copyStream(zis, zos);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private String addFoliaSupportedFlag(String pluginYml) {
        // 既に 'folia-supported' キーが存在するかどうかをチェック
        if (pluginYml.lines().anyMatch(line -> line.trim().startsWith("folia-supported:"))) {
            // 存在する場合、その値を 'true' に書き換える
            return pluginYml.replaceAll("(?m)^\\s*folia-supported:.*$", "folia-supported: true");
        } else {
            // 存在しない場合、ファイルの末尾に追加する
            return pluginYml.trim() + "\nfolia-supported: true\n";
        }
    }

    private void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
    }

    @Override
    public void onEnable() {
        getLogger().info("[Phantom] === FoliaPhantom onEnable ===");
        for (Plugin plugin : wrappedPlugins) {
            if (plugin != null && !plugin.isEnabled()) {
                getLogger().info(String.format("[Phantom][%s] Enabling patched plugin...", plugin.getName()));
                getServer().getPluginManager().enablePlugin(plugin);
            }
        }
        getLogger().info("[Phantom] 全てのパッチ済みプラグインを有効化しました。");
    }

    @Override
    public void onDisable() {
        getLogger().info("[Phantom] === FoliaPhantom onDisable ===");
        // プラグインはBukkitが管理するので、アンロードは不要。
        wrappedPlugins.clear();

        // 一時ファイルをクリーンアップ
        getLogger().info("[Phantom] Cleaning up temporary files...");
        for (File file : tempJarFiles) {
            if (file.exists() && !file.delete()) {
                getLogger().warning("[Phantom] Could not delete temporary file: " + file.getPath());
                file.deleteOnExit();
            }
        }
        tempJarFiles.clear();
        getLogger().info("[Phantom] FoliaPhantom has been disabled.");
    }

    private static class SchedulerClassTransformer {
        // We can't get the logger directly, so we pass it from the main class.
        private final java.util.logging.Logger logger;

        public SchedulerClassTransformer(java.util.logging.Logger logger) {
            this.logger = logger;
        }

        public byte[] transform(byte[] originalBytes) {
            String className = "Unknown";
            try {
                ClassReader cr = new ClassReader(originalBytes);
                className = cr.getClassName();
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                ClassVisitor cv = new SchedulerClassVisitor(cw);
                cr.accept(cv, ClassReader.EXPAND_FRAMES);
                return cw.toByteArray();
            } catch (Exception e) {
                logger.log(Level.WARNING, "[Phantom] Failed to transform class " + className + ". Returning original bytes.", e);
                return originalBytes;
            }
        }

        private static class SchedulerClassVisitor extends ClassVisitor {
            public SchedulerClassVisitor(ClassVisitor cv) { super(Opcodes.ASM9, cv); }
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                return new SchedulerMethodVisitor(super.visitMethod(access, name, desc, sig, ex));
            }
        }

        private static class SchedulerMethodVisitor extends MethodVisitor {
            private static final String PATCHER_INTERNAL_NAME = Type.getInternalName(FoliaPatcher.class);

            public SchedulerMethodVisitor(MethodVisitor mv) { super(Opcodes.ASM9, mv); }
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                if (opcode == Opcodes.INVOKEINTERFACE && "org/bukkit/scheduler/BukkitScheduler".equals(owner)) {
                    String newDesc = "(Lorg/bukkit/scheduler/BukkitScheduler;" + desc.substring(1);
                    if (FoliaPatcher.REPLACEMENT_MAP.containsKey(name + desc)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER_INTERNAL_NAME, name, newDesc, false);
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
            Map.entry("runTaskTimerAsynchronously(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;JJ)Lorg/bukkit/scheduler/BukkitTask;", true),
            // Legacy methods returning int
            Map.entry("scheduleSyncDelayedTask(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;J)I", true),
            Map.entry("scheduleSyncRepeatingTask(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;JJ)I", true),
            Map.entry("scheduleAsyncDelayedTask(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;J)I", true),
            Map.entry("scheduleAsyncRepeatingTask(Lorg/bukkit/plugin/Plugin;Ljava/lang/Runnable;JJ)I", true),
            // Cancel tasks
            Map.entry("cancelTask(I)V", true),
            Map.entry("cancelTasks(Lorg/bukkit/plugin/Plugin;)V", true),
            Map.entry("cancelAllTasks()V", true)
        );

        private FoliaPatcher() {}

        private static Location getFallbackLocation() {
            // Try to get the main overworld first.
            World mainWorld = Bukkit.getWorld("world");
            if (mainWorld != null) {
                return mainWorld.getSpawnLocation();
            }
            // Fallback to the first loaded world if the main world is not available.
            List<World> worlds = Bukkit.getWorlds();
            if (!worlds.isEmpty()) {
                return worlds.get(0).getSpawnLocation();
            }
            // Return null if no worlds are loaded.
            return null;
        }

        private static void cancelTaskById(int taskId) {
            ScheduledTask task = runningTasks.remove(taskId);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }

        private static Runnable wrapRunnable(Runnable original, int taskId, boolean isRepeating) {
            if (isRepeating) {
                return original; // For repeating tasks, cleanup is done only on cancellation.
            }
            return () -> {
                try {
                    original.run();
                } finally {
                    runningTasks.remove(taskId);
                }
            };
        }
        
        public static BukkitTask runTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable) {
            int taskId = taskIdCounter.getAndIncrement();
            Runnable wrappedRunnable = wrapRunnable(runnable, taskId, false);
            Location loc = getFallbackLocation();
            ScheduledTask foliaTask = loc != null
                ? Bukkit.getRegionScheduler().run(plugin, loc, t -> wrappedRunnable.run())
                : Bukkit.getGlobalRegionScheduler().run(plugin, t -> wrappedRunnable.run());
            runningTasks.put(taskId, foliaTask);
            return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, true, foliaTask);
        }

        public static BukkitTask runTaskLater(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay) {
            int taskId = taskIdCounter.getAndIncrement();
            Runnable wrappedRunnable = wrapRunnable(runnable, taskId, false);
            Location loc = getFallbackLocation();
            ScheduledTask foliaTask = loc != null
                ? Bukkit.getRegionScheduler().runDelayed(plugin, loc, t -> wrappedRunnable.run(), Math.max(1, delay))
                : Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> wrappedRunnable.run(), Math.max(1, delay));
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
            int taskId = taskIdCounter.getAndIncrement();
            Runnable wrappedRunnable = wrapRunnable(runnable, taskId, false);
            ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runNow(plugin, t -> wrappedRunnable.run());
            runningTasks.put(taskId, foliaTask);
            return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
        }

        public static BukkitTask runTaskLaterAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay) {
            int taskId = taskIdCounter.getAndIncrement();
            Runnable wrappedRunnable = wrapRunnable(runnable, taskId, false);
            // Note: Assumes a fixed 20 TPS (50ms per tick) for delay conversion.
            ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runDelayed(plugin, t -> wrappedRunnable.run(), delay * 50, TimeUnit.MILLISECONDS);
            runningTasks.put(taskId, foliaTask);
            return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
        }

        public static BukkitTask runTaskTimerAsynchronously(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay, long period) {
            // Note: Assumes a fixed 20 TPS (50ms per tick) for delay and period conversion.
            ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> runnable.run(), delay * 50, period * 50, TimeUnit.MILLISECONDS);
            int taskId = taskIdCounter.getAndIncrement();
            runningTasks.put(taskId, foliaTask);
            return new FoliaBukkitTask(taskId, plugin, FoliaPatcher::cancelTaskById, false, foliaTask);
        }

        // --- Legacy Methods ---
        public static int scheduleSyncDelayedTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay) {
            return runTaskLater(ignored, plugin, runnable, delay).getTaskId();
        }

        public static int scheduleSyncRepeatingTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay, long period) {
            return runTaskTimer(ignored, plugin, runnable, delay, period).getTaskId();
        }

        public static int scheduleAsyncDelayedTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay) {
            return runTaskLaterAsynchronously(ignored, plugin, runnable, delay).getTaskId();
        }

        public static int scheduleAsyncRepeatingTask(BukkitScheduler ignored, Plugin plugin, Runnable runnable, long delay, long period) {
            return runTaskTimerAsynchronously(ignored, plugin, runnable, delay, period).getTaskId();
        }

        // --- Cancellation Methods ---
        public static void cancelTask(BukkitScheduler ignored, int taskId) {
            cancelTaskById(taskId);
        }

        public static void cancelTasks(BukkitScheduler ignored, Plugin plugin) {
            runningTasks.entrySet().removeIf(entry -> {
                ScheduledTask scheduledTask = entry.getValue();
                boolean ownedByPlugin = scheduledTask.getOwningPlugin().equals(plugin);
                if (ownedByPlugin && !scheduledTask.isCancelled()) {
                    scheduledTask.cancel();
                }
                return ownedByPlugin; // remove if owned by the plugin
            });
        }

        public static void cancelAllTasks() {
            runningTasks.values().forEach(task -> {
                if (!task.isCancelled()) {
                    task.cancel();
                }
            });
            runningTasks.clear();
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
