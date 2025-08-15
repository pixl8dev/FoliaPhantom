package com.marvserver.folia_phantom_3.patcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.foliaPhantom.transformers.ClassTransformer;
import summer.foliaPhantom.transformers.EntitySchedulerTransformer;
import summer.foliaPhantom.transformers.SchedulerClassTransformer;
import summer.foliaPhantom.transformers.ThreadSafetyTransformer;
import summer.foliaPhantom.transformers.WorldGenClassTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * This class is responsible for patching plugin JAR files.
 * It uses a series of ClassTransformers to modify the bytecode of the classes within the JAR.
 * This is necessary to make the plugins compatible with the Folia server software.
 * The use of bytecode manipulation can sometimes be flagged as suspicious by antivirus software,
 * but in this case, it is a legitimate and necessary part of the patching process.
 */
public class PluginPatcher {

    private final Logger logger = LoggerFactory.getLogger(PluginPatcher.class);
    private final List<ClassTransformer> transformers = new ArrayList<>();

    public PluginPatcher() {
        // The transformers from the original project use java.util.logging.Logger.
        // We are using slf4j in this project, so we need to create a jul logger to pass to the transformers.
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(PluginPatcher.class.getName());

        // The order of these transformers is important.
        this.transformers.add(new ThreadSafetyTransformer(julLogger));
        this.transformers.add(new WorldGenClassTransformer(julLogger));
        this.transformers.add(new EntitySchedulerTransformer(julLogger));
        this.transformers.add(new SchedulerClassTransformer(julLogger));
    }

    public void patchPlugin(InputStream inputStream, OutputStream outputStream) throws IOException {
        try (
            ZipInputStream zis = new ZipInputStream(inputStream);
            ZipOutputStream zos = new ZipOutputStream(outputStream)
        ) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));

                if (!entry.isDirectory()) {
                    if (entry.getName().equals("plugin.yml")) {
                        String originalYml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                        String modifiedYml = addFoliaSupportedFlag(originalYml);
                        zos.write(modifiedYml.getBytes(StandardCharsets.UTF_8));
                    } else if (entry.getName().endsWith(".class")) {
                        // This is where the bytecode manipulation happens.
                        // We read the class bytes and then apply each transformer in order.
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
