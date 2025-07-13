package summer.foliaPhantom.config;

public record PluginConfig(String name, String originalJarPath, String patchedJarPath, boolean foliaEnabled) {
    public PluginConfig {
        if (name == null || originalJarPath == null || patchedJarPath == null) {
            throw new IllegalArgumentException("PluginConfig fields cannot be null");
        }
    }
}
