package com.marvserver.folia_phantom_3.patcher;

import java.util.Map;
import com.marvserver.folia_phantom_3.patcher.transformers.ClassTransformer;
import com.marvserver.folia_phantom_3.patcher.transformers.EntitySchedulerTransformer;
import com.marvserver.folia_phantom_3.patcher.transformers.SchedulerClassTransformer;
import com.marvserver.folia_phantom_3.patcher.transformers.ThreadSafetyTransformer;
import com.marvserver.folia_phantom_3.patcher.transformers.WorldGenClassTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PluginPatcher {

    private final Logger logger = LoggerFactory.getLogger(PluginPatcher.class);
    private final List<ClassTransformer> transformers = new ArrayList<>();

    public PluginPatcher() {
        // Register all transformers here. Order is critical.
        this.transformers.add(new ThreadSafetyTransformer());
        this.transformers.add(new WorldGenClassTransformer());
        this.transformers.add(new EntitySchedulerTransformer());
        this.transformers.add(new SchedulerClassTransformer());
    }

    public void patchPlugin(InputStream originalJarStream, OutputStream patchedJarStream, Map<String, byte[]> helperClasses) throws IOException {
        logger.info("Starting JAR patching process...");
        try (
            ZipInputStream zis = new ZipInputStream(originalJarStream);
            ZipOutputStream zos = new ZipOutputStream(patchedJarStream)
        ) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Do not copy the helper classes if they already exist in the original JAR
                if (helperClasses.containsKey(entry.getName())) {
                    continue;
                }
                zos.putNextEntry(new ZipEntry(entry.getName()));

                if (!entry.isDirectory()) {
                    if (entry.getName().equals("plugin.yml")) {
                        String originalYml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                        String modifiedYml = addFoliaSupportedFlag(originalYml);
                        zos.write(modifiedYml.getBytes(StandardCharsets.UTF_8));
                    } else if (entry.getName().endsWith(".class")) {
                        byte[] classBytes = zis.readAllBytes();
                        for (ClassTransformer transformer : transformers) {
                            classBytes = transformer.transform(classBytes);
                        }
                        zos.write(classBytes);
                    } else {
                        copyStream(zis, zos);
                    }
                }
                zos.closeEntry();
            }

            // Inject the helper classes into the JAR
            logger.info("Injecting {} helper classes...", helperClasses.size());
            for (Map.Entry<String, byte[]> helperEntry : helperClasses.entrySet()) {
                ZipEntry newEntry = new ZipEntry(helperEntry.getKey());
                zos.putNextEntry(newEntry);
                zos.write(helperEntry.getValue());
                zos.closeEntry();
            }
            logger.info("JAR patching process complete.");

        } catch (Exception e) {
            logger.error("Failed to patch JAR", e);
            throw new IOException("Failed to patch JAR file.", e);
        }
    }

    private String addFoliaSupportedFlag(String pluginYml) {
        if (pluginYml.lines().anyMatch(line -> line.trim().startsWith("folia-supported:"))) {
            return pluginYml.replaceAll("(?m)^\\s*folia-supported:.*$", "folia-supported: true");
        } else {
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
}
