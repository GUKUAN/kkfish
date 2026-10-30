package me.kkfish.misc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import me.kkfish.kkfish;

public class DependencyManager {

    private final kkfish plugin;
    private final File libFolder;
    private final Properties versions = new Properties();

    public DependencyManager(kkfish plugin) {
        this.plugin = plugin;
        this.libFolder = new File(plugin.getDataFolder(), "lib");
        loadVersions();
        ensureLibFolderExists();
    }

    /** 版本号从 deps.properties 读，那文件里的占位符打包时由 pom 填好，升级只改 pom 一处 */
    private void loadVersions() {
        try (InputStream in = plugin.getResource("deps.properties")) {
            if (in == null) {
                kkfish.log("§e[Dependency] deps.properties missing, using built-in version fallbacks");
                return;
            }
            versions.load(in);
        } catch (IOException e) {
            // 读不到就走下面 versionOf 的兜底值，但要说一声，否则打包漏了文件根本发现不了
            kkfish.log("§e[Dependency] deps.properties unreadable, using built-in version fallbacks");
        }
    }

    private String versionOf(String key, String fallback) {
        return versions.getProperty(key, fallback);
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

        String xseries = versionOf("xseries", "14.0.0");
        String nbt = versionOf("nbt-api", "2.16.1");
        String sqlite = versionOf("sqlite", "3.54.1.0");
        String mysql = versionOf("mysql", "26.10.0");
        String slf4j = versionOf("slf4j", "1.7.36");

        String xseriesJar = "XSeries-" + xseries + ".jar";
        String nbtJar = "item-nbt-api-plugin-" + nbt + ".jar";
        String sqliteJar = "sqlite-jdbc-" + sqlite + ".jar";
        String mysqlJar = "mysql-connector-j-" + mysql + ".jar";
        String slf4jJar = "slf4j-api-" + slf4j + ".jar";

        if (!isDependencyDownloaded(xseriesJar)) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("dependency_downloading", "Downloading %s...", "XSeries"));
            if (downloadDependency(new String[]{
                    "https://maven.aliyun.com/repository/public/com/github/cryptomorin/XSeries/" + xseries + "/" + xseriesJar,
                    "https://repo1.maven.org/maven2/com/github/cryptomorin/XSeries/" + xseries + "/" + xseriesJar
            }, xseriesJar)) {
            } else {
                kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("dependency_download_failed", "%s download failed, plugin may not work properly", "XSeries"));
                allDownloaded = false;
            }
        }

        // NBT API 只有 codemc.io 一个发布源，阿里云镜像不收录
        // 必须用 plugin 版：item-nbt-api 那个包名是 de.tr7zw.changeme.nbtapi，反射找不到 NBTItem
        if (!isDependencyDownloaded(nbtJar)) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("dependency_downloading", "Downloading %s...", "NBT API"));
            if (downloadDependency(new String[]{
                    "https://repo.codemc.io/repository/maven-public/de/tr7zw/item-nbt-api-plugin/" + nbt + "/" + nbtJar
            }, nbtJar)) {
            } else {
                kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("dependency_download_failed", "%s download failed, plugin may not work properly", "NBT API"));
                allDownloaded = false;
            }
        }

        // JDBC 驱动不打进插件 jar（体积原因），启动时下载到 lib/ 再挂载类加载器
        // slf4j-api 是 sqlite-jdbc 的硬依赖，老版本 Spigot 核心不自带，必须一并下载
        if (!isDependencyDownloaded(sqliteJar)) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("dependency_downloading", "Downloading %s...", "SQLite JDBC"));
            if (downloadDependency(new String[]{
                    "https://maven.aliyun.com/repository/public/org/xerial/sqlite-jdbc/" + sqlite + "/" + sqliteJar,
                    "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/" + sqlite + "/" + sqliteJar
            }, sqliteJar)) {
            } else {
                kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("dependency_download_failed", "%s download failed, plugin may not work properly", "SQLite JDBC"));
                allDownloaded = false;
            }
        }

        if (!isDependencyDownloaded(mysqlJar)) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("dependency_downloading", "Downloading %s...", "MySQL JDBC"));
            if (downloadDependency(new String[]{
                    "https://maven.aliyun.com/repository/public/com/mysql/mysql-connector-j/" + mysql + "/" + mysqlJar,
                    "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/" + mysql + "/" + mysqlJar
            }, mysqlJar)) {
            } else {
                kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("dependency_download_failed", "%s download failed, plugin may not work properly", "MySQL JDBC"));
                allDownloaded = false;
            }
        }

        if (!isDependencyDownloaded(slf4jJar)) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("dependency_downloading", "Downloading %s...", "SLF4J"));
            if (downloadDependency(new String[]{
                    "https://maven.aliyun.com/repository/public/org/slf4j/slf4j-api/" + slf4j + "/" + slf4jJar,
                    "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/" + slf4j + "/" + slf4jJar
            }, slf4jJar)) {
            } else {
                kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("dependency_download_failed", "%s download failed, plugin may not work properly", "SLF4J"));
                allDownloaded = false;
            }
        }

        // 旧版本 jar 留在 lib/ 里会被 loadDependencies 一起挂进类加载器，先加载的那个生效，等于版本随机。
        // 必须放在下载之后：目标文件没到位就删旧的，网络一挂连旧版本都没了
        dropStale("XSeries-", xseriesJar);
        dropStale("item-nbt-api", nbtJar);
        dropStale("sqlite-jdbc-", sqliteJar);
        dropStale("mysql-connector-", mysqlJar);

        return allDownloaded;
    }

    /** 清理同前缀的旧 jar；keepName 不在就直接返回，下载失败时至少还能留着上一版 */
    private void dropStale(String prefix, String keepName) {
        if (!isDependencyDownloaded(keepName)) return;

        File[] stale = libFolder.listFiles((dir, name) ->
                name.startsWith(prefix) && name.endsWith(".jar") && !name.equals(keepName));
        if (stale == null) return;

        for (File file : stale) {
            if (file.delete()) {
                kkfish.log("§e[Dependency] Removed outdated " + file.getName());
            } else {
                kkfish.log("§c[Dependency] Could not delete " + file.getName() + ", it may be locked");
            }
        }
    }

    private boolean isDependencyDownloaded(String fileName) {
        File dependencyFile = new File(libFolder, fileName);
        return dependencyFile.exists() && dependencyFile.length() > 0;
    }

    /**
     * 多源依次尝试下载：任一候选源成功即返回。
     * 国内机房直连 Maven Central（repo1.maven.org）经常被解析到假节点返回 404，
     * 所以候选列表里阿里云镜像放前面兜底官方源
     */
    private boolean downloadDependency(String[] urls, String fileName) {
        for (String url : urls) {
            try {
                Path outputPath = new File(libFolder, fileName).toPath();
                URLConnection conn = new URL(url).openConnection();
                // 不设超时的话网络半死不活时会卡住主线程很久
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                try (InputStream in = conn.getInputStream()) {
                    Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }

                return true;
            } catch (IOException e) {
                // 当前源拉不下来，换下一个候选源继续试
            }
        }
        kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("dependency_download_failed", "Failed to download dependency: %s", fileName));
        return false;
    }

    public void loadDependencies() {
        try {
            File[] jarFiles = libFolder.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jarFiles != null) {
                for (File jarFile : jarFiles) {
                    boolean loaded = false;
                    
                    // JDBC 驱动与普通库的加载方式不同：必须把 jar 挂载进插件自身的类加载器，
                    // DB 里的 new org.sqlite.JDBC() 直连写法才能解析到类。
                    // 绝不能注册到 DriverManager——那会提前触发它全局只执行一次的驱动扫描，
                    // 导致其他自带驱动的插件（如 nightcore）报 No suitable driver
                    if (jarFile.getName().startsWith("sqlite-jdbc") || jarFile.getName().startsWith("mysql-connector") || jarFile.getName().startsWith("slf4j-api")) {
                        try {
                            ClassLoader pluginClassLoader = plugin.getClass().getClassLoader();
                            java.lang.reflect.Method addUrl = java.net.URLClassLoader.class.getDeclaredMethod("addURL", java.net.URL.class);
                            addUrl.setAccessible(true);
                            addUrl.invoke(pluginClassLoader, jarFile.toURI().toURL());
                            
                            // initialize=false 只验证类可见性，不执行静态初始化块
                            if (jarFile.getName().startsWith("sqlite-jdbc")) {
                                Class.forName("org.sqlite.JDBC", false, pluginClassLoader);
                            } else if (jarFile.getName().startsWith("mysql-connector")) {
                                Class.forName("com.mysql.cj.jdbc.Driver", false, pluginClassLoader);
                            }
                            loaded = true;
                        } catch (Throwable e) {
                            kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("dependency_download_failed", "Failed to load dependency: %s", jarFile.getName()));
                        }
                        
                        if (loaded) {
                            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.dependency_load_success", "Loaded %s successfully", jarFile.getName()));
                        } else {
                            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.dependency_load_failed", "Failed to load %s, plugin will try to continue using Bukkit's native methods and default values", jarFile.getName()));
                        }
                        continue;
                    }
                    
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
