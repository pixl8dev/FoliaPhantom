package com.marvserver.folia_phantom_3.service;

import com.marvserver.folia_phantom_3.patcher.PluginPatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class PatchService {

    private final Logger logger = LoggerFactory.getLogger(PatchService.class);
    private final Map<String, byte[]> helperClasses;
    private final PluginPatcher pluginPatcher;

    public PatchService() throws IOException {
        this.helperClasses = loadHelperClasses();
        this.pluginPatcher = new PluginPatcher();
    }

    public byte[] patchJar(InputStream jarInputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        pluginPatcher.patchPlugin(jarInputStream, baos, this.helperClasses);
        logger.info("Patching service called, returning byte array.");
        return baos.toByteArray();
    }

    private Map<String, byte[]> loadHelperClasses() throws IOException {
        logger.info("Loading helper classes from original JAR...");
        Map<String, byte[]> classes = new HashMap<>();
        // The JAR is expected to be in the root of the original project structure
        File originalJar = new File("FoliaPhantom/target/FoliaPhantom-extra-2.0-SNAPSHOT.jar");

        if (!originalJar.exists()) {
            logger.error("Original JAR file not found at: {}. This is required for helper classes.", originalJar.getAbsolutePath());
            logger.error("Please ensure the original 'FoliaPhantom' project has been built with 'mvn clean package'.");
            throw new IOException("Could not find the helper JAR: " + originalJar.getAbsolutePath());
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(originalJar))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                final String entryName = entry.getName();
                // We need FoliaPatcher and its inner classes.
                if (entryName.startsWith("summer/foliaPhantom/patchers/FoliaPatcher") && entryName.endsWith(".class")) {
                    logger.info("Found helper class: {}", entryName);
                    classes.put(entryName, zis.readAllBytes());
                }
            }
        }
        logger.info("Loaded {} helper classes.", classes.size());
        return classes;
    }
}
