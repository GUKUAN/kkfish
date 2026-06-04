package me.kkfish.misc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import me.kkfish.kkfish;

public class DependencyManager {

    private final kkfish plugin;
    private final File libFolder;

    public DependencyManager(kkfish plugin) {
        this.plugin = plugin;
        this.libFolder = new File(plugin.getDataFolder(), "lib");
        ensureLibFolderExists();
    }

    private void ensureLibFolderExists() {
        if (!libFolder.exists()) {
            if (libFolder.mkdirs()) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("dependency_lib_folder_created", "Successfully created dependency library folder: %s", libFolder.getPath()));
            } else {
                kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("dependency_lib_folder_failed", "Failed to create dependency library folder: %s", libFolder.getPath()));
            }
        }
    }

    public boolean checkAndDownloadDependencies() {
        boolean allDownloaded = true;

        if (!isDependencyDownloaded("XSeries-13.6.0.jar")) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("dependency_downloading", "Downloading %s...", "XSeries"));
            if (downloadDependency("https://repo1.maven.org/maven2/com/github/cryptomorin/XSeries/13.6.0/XSeries-13.6.0.jar", "XSeries-13.6.0.jar")) {
            } else {
                kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("dependency_download_failed", "%s download failed, plugin may not work properly", "XSeries"));
                allDownloaded = false;
            }
        }

        if (!isDependencyDownloaded("item-nbt-api-2.11.1.jar")) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("dependency_downloading", "Downloading %s...", "NBT API"));
            if (downloadDependency("https://repo.codemc.io/repository/maven-public/de/tr7zw/item-nbt-api/2.11.1/item-nbt-api-2.11.1.jar", "item-nbt-api-2.11.1.jar")) {
            } else {
                kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("dependency_download_failed", "%s download failed, plugin may not work properly", "NBT API"));
                allDownloaded = false;
            }
        }

        return allDownloaded;
    }

    private boolean isDependencyDownloaded(String fileName) {
        File dependencyFile = new File(libFolder, fileName);
        return dependencyFile.exists() && dependencyFile.length() > 0;
    }

    private boolean downloadDependency(String url, String fileName) {
        try {
            Path outputPath = new File(libFolder, fileName).toPath();
            URL downloadUrl = new URL(url);

            try (InputStream in = downloadUrl.openStream()) {
                Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return true;
        } catch (IOException e) {
            kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("dependency_download_failed", "Failed to download dependency: %s", e.getMessage()));
            return false;
        }
    }

    public void loadDependencies() {
        try {
            File[] jarFiles = libFolder.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jarFiles != null) {
                for (File jarFile : jarFiles) {
                    boolean loaded = false;
                    
                    try {
                        addJarToClasspath(jarFile);
                        loaded = true;
                    } catch (Exception e) {
                    }
                    
                    if (!loaded) {
                        try {
                            ClassLoader parentClassLoader = plugin.getClass().getClassLoader();
                            
                            java.net.URL[] urls = new java.net.URL[1];
                            urls[0] = jarFile.toURI().toURL();
                            
                            java.net.URLClassLoader customClassLoader = new java.net.URLClassLoader(urls, parentClassLoader);
                            
                            Thread.currentThread().setContextClassLoader(customClassLoader);
                            
                            try {
                                if (jarFile.getName().contains("XSeries")) {
                                    Class<?> xMaterialClass = customClassLoader.loadClass("com.cryptomorin.xseries.XMaterial");
                                    Class<?> xSoundClass = customClassLoader.loadClass("com.cryptomorin.xseries.XSound");
                                    loaded = true;
                                } else if (jarFile.getName().contains("item-nbt-api")) {
                                    Class<?> nbtApiClass = customClassLoader.loadClass("de.tr7zw.nbtapi.NBTItem");
                                    loaded = true;
                                }
                            } catch (ClassNotFoundException e) {
                            }
                        } catch (Exception e) {
                        }
                    }
                    
                    if (!loaded) {
                        try {
                            ClassLoader parentClassLoader = plugin.getClass().getClassLoader();
                            
                            java.net.URL[] urls = new java.net.URL[1];
                            urls[0] = jarFile.toURI().toURL();
                            
                            Class<?> urlClassLoaderClass = Class.forName("java.net.URLClassLoader");
                            java.lang.reflect.Constructor<?> constructor = urlClassLoaderClass.getDeclaredConstructor(java.net.URL[].class, ClassLoader.class);
                            Object customClassLoader = constructor.newInstance(urls, parentClassLoader);
                            
                            java.lang.reflect.Method loadClassMethod = urlClassLoaderClass.getMethod("loadClass", String.class);
                            if (jarFile.getName().contains("XSeries")) {
                                Object xMaterialClass = loadClassMethod.invoke(customClassLoader, "com.cryptomorin.xseries.XMaterial");
                                Object xSoundClass = loadClassMethod.invoke(customClassLoader, "com.cryptomorin.xseries.XSound");
                                loaded = true;
                            } else if (jarFile.getName().contains("item-nbt-api")) {
                                Object nbtApiClass = loadClassMethod.invoke(customClassLoader, "de.tr7zw.nbtapi.NBTItem");
                                loaded = true;
                            }
                        } catch (Exception e) {
                        }
                    }
                    
                    if (!loaded && jarFile.getName().contains("XSeries")) {
                        try {
                            Class<?> xSeriesUtilClass = Class.forName("me.kkfish.utils.XSeriesUtil");
                            
                            java.net.URL[] urls = new java.net.URL[1];
                            urls[0] = jarFile.toURI().toURL();
                            
                            java.net.URLClassLoader customClassLoader = new java.net.URLClassLoader(urls, xSeriesUtilClass.getClassLoader());
                            
                            Class<?> xMaterialClass = customClassLoader.loadClass("com.cryptomorin.xseries.XMaterial");
                            Class<?> xSoundClass = customClassLoader.loadClass("com.cryptomorin.xseries.XSound");
                            
                            java.lang.reflect.Field xMaterialClassField = xSeriesUtilClass.getDeclaredField("xMaterialClass");
                            java.lang.reflect.Field xSoundClassField = xSeriesUtilClass.getDeclaredField("xSoundClass");
                            java.lang.reflect.Field initializedField = xSeriesUtilClass.getDeclaredField("initialized");
                            
                            xMaterialClassField.setAccessible(true);
                            xSoundClassField.setAccessible(true);
                            initializedField.setAccessible(true);
                            
                            xMaterialClassField.set(null, xMaterialClass);
                            xSoundClassField.set(null, xSoundClass);
                            initializedField.set(null, true);
                            
                            loaded = true;
                        } catch (Exception e) {
                        }
                    }
                    
                    if (!loaded && jarFile.getName().contains("item-nbt-api")) {
                        try {
                            Class<?> nbtUtilAPIClass = Class.forName("me.kkfish.utils.NBTUtilAPI");
                            
                            java.net.URL[] urls = new java.net.URL[1];
                            urls[0] = jarFile.toURI().toURL();
                            
                            java.net.URLClassLoader customClassLoader = new java.net.URLClassLoader(urls, nbtUtilAPIClass.getClassLoader());
                            
                            Class<?> nbtItemClass = customClassLoader.loadClass("de.tr7zw.nbtapi.NBTItem");
                            
                            java.lang.reflect.Field nbtApiAvailableField = nbtUtilAPIClass.getDeclaredField("nbtApiAvailable");
                            nbtApiAvailableField.setAccessible(true);
                            nbtApiAvailableField.set(null, true);
                            
                            loaded = true;
                        } catch (Exception e) {
                        }
                    }
                    
                    if (loaded) {
                        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.dependency_load_success", "Loaded %s successfully", jarFile.getName()));
                    } else {
                        kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.dependency_load_failed", "Failed to load %s, plugin will try to continue using Bukkit's native methods and default values", jarFile.getName()));
                    }
                }
            }
        } catch (Exception e) {
            kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("dependency_load_failed", "Failed to load dependencies: %s", e.getMessage()));
        }
    }

    private void addJarToClasspath(File jarFile) throws Exception {
        try {
            ClassLoader currentClassLoader = plugin.getClass().getClassLoader();
            java.net.URL[] urls;
            if (currentClassLoader instanceof java.net.URLClassLoader) {
                java.net.URLClassLoader urlClassLoader = (java.net.URLClassLoader) currentClassLoader;
                urls = urlClassLoader.getURLs();
            } else {
                urls = new java.net.URL[0];
            }
            
            java.net.URL[] newUrls = new java.net.URL[urls.length + 1];
            System.arraycopy(urls, 0, newUrls, 0, urls.length);
            newUrls[urls.length] = jarFile.toURI().toURL();
            
            java.net.URLClassLoader newClassLoader = new java.net.URLClassLoader(newUrls, currentClassLoader);
            
            Thread.currentThread().setContextClassLoader(newClassLoader);
            
            if (jarFile.getName().contains("XSeries")) {
                newClassLoader.loadClass("com.cryptomorin.xseries.XMaterial");
                newClassLoader.loadClass("com.cryptomorin.xseries.XSound");
            } else if (jarFile.getName().contains("item-nbt-api")) {
                newClassLoader.loadClass("de.tr7zw.nbtapi.NBTItem");
            }
        } catch (Exception e) {
        }
    }
}
