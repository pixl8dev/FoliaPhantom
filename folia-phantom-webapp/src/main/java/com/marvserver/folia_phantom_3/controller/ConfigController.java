package com.marvserver.folia_phantom_3.controller;

import com.marvserver.folia_phantom_3.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final AppProperties appProperties;

    public ConfigController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @GetMapping
    public AppProperties getConfig() {
        return appProperties;
    }
}
