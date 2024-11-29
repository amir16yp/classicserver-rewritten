package net.classicube.api;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PluginLoader {

    private final List<JavaPlugin> loadedPlugins = new ArrayList<>();

    public void loadPlugins(String pluginFolderPath) {
        List<JavaPlugin> loadedPlugins = new ArrayList<>();
        File pluginFolder = new File(pluginFolderPath);

        if (!pluginFolder.exists() || !pluginFolder.isDirectory()) {
            System.out.println("Plugin folder not found or is not a directory");
            return;
        }

        File[] jarFiles = pluginFolder.listFiles((dir, name) -> name.endsWith(".jar"));

        if (jarFiles == null) {
            System.out.println("No .jar files found in the plugin folder");
            return;
        }

        for (File jarFile : jarFiles) {
            try {
                JarFile jar = new JarFile(jarFile);
                URL[] urls = {new URL("jar:file:" + jarFile.getAbsolutePath() + "!/")};
                URLClassLoader classLoader = new URLClassLoader(urls, this.getClass().getClassLoader());

                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                        continue;
                    }

                    String className = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
                    Class<?> clazz = classLoader.loadClass(className);

                    if (JavaPlugin.class.isAssignableFrom(clazz)) {
                        JavaPlugin plugin = (JavaPlugin) clazz.getDeclaredConstructor().newInstance();
                        loadedPlugins.add(plugin);
                        System.out.println("Loaded plugin: " + plugin.getClass().getName());
                    }
                }

                jar.close();
                classLoader.close();
            } catch (Exception e) {
                System.out.println("Error loading plugin from " + jarFile.getName() + ": " + e.getMessage());
            }
        }

        //return loadedPlugins;
    }

    public List<JavaPlugin> getLoadedPlugins() {
        return loadedPlugins;
    }

    public void enablePlugins() {
        for (JavaPlugin javaPlugin : getLoadedPlugins()) {
            javaPlugin.onEnable();
        }
    }

    public void disablePlugins() {
        for (JavaPlugin javaPlugin : getLoadedPlugins()) {
            javaPlugin.onDisable();
        }
    }
}