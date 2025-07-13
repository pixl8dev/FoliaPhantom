package summer.foliaPhantom.jar;

import java.io.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import java.nio.charset.StandardCharsets;

public class JarPatcher {

    /**
     * Creates a new JAR file with a modified plugin.yml to support Folia.
     *
     * @param originalJar The original plugin JAR file.
     * @param patchedJar  The destination file for the patched JAR.
     * @throws IOException If any I/O error occurs during patching.
     */
    public static void createFoliaSupportedJar(File originalJar, File patchedJar) throws IOException {
        try (JarInputStream jis = new JarInputStream(new BufferedInputStream(new FileInputStream(originalJar)));
             JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(patchedJar)))) {

            byte[] buffer = new byte[8192];
            boolean pluginYmlFound = false;

            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if ("plugin.yml".equals(entry.getName())) {
                    pluginYmlFound = true;
                    String originalContent = new String(jis.readAllBytes(), StandardCharsets.UTF_8);
                    String modifiedContent = addFoliaSupport(originalContent);
                    jos.putNextEntry(new JarEntry("plugin.yml"));
                    jos.write(modifiedContent.getBytes(StandardCharsets.UTF_8));
                } else {
                    jos.putNextEntry(new JarEntry(entry.getName()));
                    int len;
                    while ((len = jis.read(buffer)) > 0) {
                        jos.write(buffer, 0, len);
                    }
                }
                jos.closeEntry();
            }

            if (!pluginYmlFound) {
                throw new IOException("patch: plugin.yml was not found in " + originalJar.getName());
            }
        }
    }

    /**
     * Modifies the plugin.yml content to add or update 'folia-supported: true'.
     *
     * @param originalContent The original content of plugin.yml.
     * @return The modified content.
     */
    private static String addFoliaSupport(String originalContent) {
        StringBuilder sb = new StringBuilder(originalContent);
        if (!originalContent.contains("folia-supported:")) {
            if (!originalContent.endsWith("\n")) sb.append("\n");

            sb.append("folia-supported: true\n");

        } else {
            // Ensure replacement handles various spacing and existing true/false
            sb = new StringBuilder(originalContent.replaceAll(
                    "folia-supported:\\s*(true|false)", "folia-supported: true"));
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar <jar-file-name>.jar <original-jar-path> <patched-jar-path>");
            System.exit(1);
        }

        File originalJar = new File(args[0]);
        File patchedJar = new File(args[1]);

        if (!originalJar.exists()) {
            System.err.println("Error: Original JAR file not found at " + originalJar.getAbsolutePath());
            System.exit(1);
        }

        try {
            System.out.println("Patching " + originalJar.getName() + " to " + patchedJar.getName() + "...");
            createFoliaSupportedJar(originalJar, patchedJar);
            System.out.println("Successfully patched JAR: " + patchedJar.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error during patching: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
