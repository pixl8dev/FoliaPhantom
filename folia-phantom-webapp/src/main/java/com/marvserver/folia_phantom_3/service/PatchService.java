package com.marvserver.folia_phantom_3.service;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import summer.foliaPhantom.PluginPatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Logger;

@Service
public class PatchService {

    private final Logger logger = Logger.getLogger(PatchService.class.getName());
    private final PluginPatcher pluginPatcher;

    public PatchService() {
        this.pluginPatcher = new PluginPatcher(Logger.getLogger(PluginPatcher.class.getName()));
    }

    public byte[] patchJar(InputStream jarInputStream) throws IOException {
        File tempInputFile = null;
        File tempOutputFile = null;
        try {
            tempInputFile = Files.createTempFile("folia-phantom-in-", ".jar").toFile();
            try (FileOutputStream out = new FileOutputStream(tempInputFile)) {
                jarInputStream.transferTo(out);
            }

            tempOutputFile = Files.createTempFile("folia-phantom-out-", ".jar").toFile();

            pluginPatcher.patchPlugin(tempInputFile, tempOutputFile);

            return Files.readAllBytes(tempOutputFile.toPath());
        } finally {
            if (tempInputFile != null) {
                tempInputFile.delete();
            }
            if (tempOutputFile != null) {
                tempOutputFile.delete();
            }
        }
    }
}
