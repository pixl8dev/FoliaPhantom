package com.marvserver.folia_phantom_3.service;

import com.marvserver.folia_phantom_3.patcher.PluginPatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class PatchService {

    private final Logger logger = LoggerFactory.getLogger(PatchService.class);
    private final PluginPatcher pluginPatcher;

    public PatchService() {
        this.pluginPatcher = new PluginPatcher();
    }

    public byte[] patchJar(InputStream jarInputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        pluginPatcher.patchPlugin(jarInputStream, baos);
        logger.info("Patching service called, returning byte array.");
        return baos.toByteArray();
    }
}
