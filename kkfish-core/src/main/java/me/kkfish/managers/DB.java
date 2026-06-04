package me.kkfish.managers;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import me.kkfish.kkfish;
import me.kkfish.gui.FishRecord;

public class DB {

    private final kkfish plugin;
    private Connection connection;
    private String dbType;
    private String tablePrefix;
    private boolean initialized = false; // 添加初始化状态标志
    
    // 缓存相关
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final int DEFAULT_CACHE_EXPIRY = 300; // 默认缓存过期时间（秒）
    private final int MAX_CACHE_SIZE = 1000;
    private int cacheWriteCount = 0;
    
    // 缓存条目类
    private class CacheEntry {
        private final Object value;
        private final long expiryTime;
        
        public CacheEntry(Object value, int expirySeconds) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + (expirySeconds * 1000L);
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
        
        public Object getValue() {
            return value;
        }
    }

    public DB(kkfish plugin) {
        this.plugin = plugin;
        try {
            initialize();
        } catch (SQLException | ClassNotFoundException e) {
            kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("log.database_init_failed", "Database initialization failed!")); e.printStackTrace();
            // 可以选择在初始化失败时设置一个标志，或者让插件禁用数据库功能
        }
    }

    private void initialize() throws SQLException, ClassNotFoundException {
        FileConfiguration config = plugin.getCustomConfig().getMainConfig();
        dbType = config.getString("database.type", "sqlite").toLowerCase();
        
        plugin.getCustomConfig().debugLog("开始数据库初始化，类型: " + dbType);
        
        try {
            // 根据数据库类型初始化连接
            if (dbType.equals("mysql")) {
                plugin.getCustomConfig().debugLog("准备初始化MySQL连接");
                initializeMySQL(config);
            } else {
                // 默认使用SQLite
                plugin.getCustomConfig().debugLog("准备初始化SQLite连接");
                initializeSQLite(config);
            }
            
            plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_success", "Database connection successful, preparing to create table structure"));
            
            // 创建数据表
            createTables();
            initialized = true; // 初始化成功
            plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_success", "Database initialization successful! Using " + dbType.toUpperCase() + " database~"));
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_success", "Database connection successful! Using %s database~", dbType.toUpperCase()));
        } catch (SQLException | ClassNotFoundException e) {
            initialized = false; // 初始化失败
            kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("log.database_init_failed", "Database initialization failed!")); e.printStackTrace();
            throw e; // 重新抛出异常，让调用者知道初始化失败
        }
    }

    /**
     * 初始化SQLite连接
     */
    private void initializeSQLite(FileConfiguration config) throws SQLException, ClassNotFoundException {
        // 加载SQLite驱动
        Class.forName("org.sqlite.JDBC");
        
        // 获取数据库文件路径
        String dbFile = config.getString("database.sqlite.file", "data.db");
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        String url = "jdbc:sqlite:" + new File(dataFolder, dbFile).getAbsolutePath();
        connection = DriverManager.getConnection(url);
        tablePrefix = "";
    }

    /**
     * 初始化MySQL连接
     */
    private void initializeMySQL(FileConfiguration config) throws SQLException, ClassNotFoundException {
        // 加载MySQL驱动
        Class.forName("com.mysql.cj.jdbc.Driver");
        
        // 获取MySQL配置
        String host = config.getString("database.mysql.host", "localhost");
        int port = config.getInt("database.mysql.port", 3306);
        String database = config.getString("database.mysql.database", "kkfish");
        String username = config.getString("database.mysql.username", "root");
        String password = config.getString("database.mysql.password", "password");
        tablePrefix = config.getString("database.mysql.table-prefix", "kkfish_");
        boolean useSSL = config.getBoolean("database.mysql.use-ssl", false);
        int timeout = config.getInt("database.mysql.timeout", 30000);
        
        // 构建连接URL
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + 
                "?useSSL=" + useSSL + 
                "&serverTimezone=UTC" + 
                "&connectTimeout=" + timeout;
        
        connection = DriverManager.getConnection(url, username, password);
    }

    /**
     * 创建数据表
     */
    private void createTables() throws SQLException {
        String autoIncrementType = dbType.equals("mysql") ? "INT PRIMARY KEY AUTO_INCREMENT" : "INTEGER PRIMARY KEY AUTOINCREMENT";

        String createPlayerStatsTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_fishing_stats (" +
                "player_uuid VARCHAR(36) PRIMARY KEY, " +
                "player_name VARCHAR(16), " +
                "total_fish_caught INT DEFAULT 0, " +
                "total_fishing_time INT DEFAULT 0, " +  // 秒
                "largest_fish_size DOUBLE DEFAULT 0.0, " +
                "most_valuable_fish INT DEFAULT 0, " +
                "success_rate DOUBLE DEFAULT 0.0, " +
                "last_fishing_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "legendary_fish_caught INT DEFAULT 0, " +
                "hook_material VARCHAR(32) DEFAULT 'wood', " +
                "player_language VARCHAR(16) DEFAULT 'zh_cn'" +
                ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPlayerStatsTable);
        }
        
        // 检查现有表是否有hook_material列，如果没有则添加
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tablePrefix + "player_fishing_stats)");
            boolean hasHookMaterial = false;
            boolean hasPlayerLanguage = false;
            while (rs.next()) {
                String columnName = rs.getString("name");
                if ("hook_material".equals(columnName)) {
                    hasHookMaterial = true;
                } else if ("player_language".equals(columnName)) {
                    hasPlayerLanguage = true;
                }
            }
            
            if (!hasHookMaterial) {
                stmt.execute("ALTER TABLE " + tablePrefix + "player_fishing_stats ADD COLUMN hook_material VARCHAR(32) DEFAULT 'wood'");
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.database_column_added", "Added %s column to existing table~", "hook_material"));
            }
            
            if (!hasPlayerLanguage) {
                stmt.execute("ALTER TABLE " + tablePrefix + "player_fishing_stats ADD COLUMN player_language VARCHAR(16) DEFAULT 'zh_cn'");
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.database_column_added", "Added %s column to existing table~", "player_language"));
            }
        } catch (SQLException e) {
            // 如果是MySQL数据库，PRAGMA不适用，这里会抛出异常
            if (dbType.equals("mysql")) {
                try (Statement stmt = connection.createStatement()) {
                    // 检查hook_material列
                    ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM " + tablePrefix + "player_fishing_stats LIKE 'hook_material'");
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE " + tablePrefix + "player_fishing_stats ADD COLUMN hook_material VARCHAR(32) DEFAULT 'wood'");
                        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.database_column_added", "Added %s column to existing table~", "hook_material"));
                    }
                    
                    // 检查player_language列
                    rs = stmt.executeQuery("SHOW COLUMNS FROM " + tablePrefix + "player_fishing_stats LIKE 'player_language'");
                    if (!rs.next()) {
                        stmt.execute("ALTER TABLE " + tablePrefix + "player_fishing_stats ADD COLUMN player_language VARCHAR(16) DEFAULT 'zh_cn'");
                        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.database_column_added", "Added %s column to existing table~", "player_language"));
                    }
                }
            } else {
                kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.database_column_check_error", "Error checking/adding columns")); e.printStackTrace();
            }
        }
        
        // 创建钓鱼记录表
        String createFishingLogTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "fishing_log (" +
                "id " + autoIncrementType + ", " +
                "player_uuid VARCHAR(36), " +
                "fish_name VARCHAR(64), " +
                "fish_level VARCHAR(32), " +
                "fish_size DOUBLE, " +
                "fish_value INT, " +
                "catch_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "location_x DOUBLE, " +
                "location_y DOUBLE, " +
                "location_z DOUBLE, " +
                "world_name VARCHAR(64)" +
                ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createFishingLogTable);
        }
        
        // 创建玩家已购买鱼钩表
        String createPlayerPurchasedHooksTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_purchased_hooks (" +
                "player_uuid VARCHAR(36), " +
                "hook_material VARCHAR(32), " +
                "purchase_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (player_uuid, hook_material)" +
                ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPlayerPurchasedHooksTable);
        }
        
        // 创建鱼的UUID价值表
        String createFishUUIDValuesTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "fish_uuid_values (" +
                "fish_uuid VARCHAR(36) PRIMARY KEY, " +
                "fish_value INT NOT NULL, " +
                "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createFishUUIDValuesTable);
        }
        
        // 创建鱼效果表
        String createFishEffectsTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "fish_effects (" +
                "id " + autoIncrementType + ", " +
                "fish_uuid VARCHAR(36), " +
                "effect_type VARCHAR(32), " +
                "effect_level INT, " +
                "effect_duration INT, " +
                "FOREIGN KEY (fish_uuid) REFERENCES " + tablePrefix + "fish_uuid_values(fish_uuid)" +
                ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createFishEffectsTable);
        }
    }

    /**
     * 记录玩家钓鱼数据
     * @param player 玩家
     * @param fishName 鱼的名称
     * @param fishLevel 鱼的等级
     * @param fishSize 鱼的大小
     * @param fishValue 鱼的价值
     */
    public void logFishing(Player player, String fishName, String fishLevel, double fishSize, int fishValue) {
        try {
            // 记录到钓鱼日志
            String insertLog = "INSERT INTO " + tablePrefix + "fishing_log (player_uuid, fish_name, fish_level, fish_size, fish_value, location_x, location_y, location_z, world_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = getConnection().prepareStatement(insertLog)) {
                pstmt.setString(1, player.getUniqueId().toString());
                pstmt.setString(2, fishName);
                pstmt.setString(3, fishLevel);
                pstmt.setDouble(4, fishSize);
                pstmt.setInt(5, fishValue);
                pstmt.setDouble(6, player.getLocation().getX());
                pstmt.setDouble(7, player.getLocation().getY());
                pstmt.setDouble(8, player.getLocation().getZ());
                pstmt.setString(9, player.getWorld().getName());
                pstmt.executeUpdate();
            }
            
            // 更新玩家统计数据
            updatePlayerStats(player, fishSize, fishValue, fishLevel.contains(plugin.getMessageManager().getMessageWithoutPrefix("rarity_name.legendary", "legendary")));
            
        } catch (SQLException e) {
            kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("log.database_fishing_log_failed", "Failed to log fishing data!")); e.printStackTrace();
        }
    }

    /**
     * 更新玩家统计数据
     */
    private void updatePlayerStats(Player player, double fishSize, int fishValue, boolean isLegendary) throws SQLException {
        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        int legendaryIncrement = isLegendary ? 1 : 0;

        if (dbType.equals("mysql")) {
            String query = "INSERT INTO " + tablePrefix + "player_fishing_stats " +
                    "(player_uuid, player_name, total_fish_caught, largest_fish_size, most_valuable_fish, legendary_fish_caught, last_fishing_time) " +
                    "VALUES (?, ?, 1, ?, ?, ?, CURRENT_TIMESTAMP) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "player_name = VALUES(player_name), " +
                    "total_fish_caught = total_fish_caught + 1, " +
                    "largest_fish_size = GREATEST(largest_fish_size, VALUES(largest_fish_size)), " +
                    "most_valuable_fish = GREATEST(most_valuable_fish, VALUES(most_valuable_fish)), " +
                    "legendary_fish_caught = legendary_fish_caught + ?, " +
                    "last_fishing_time = CURRENT_TIMESTAMP";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, uuid);
                pstmt.setString(2, name);
                pstmt.setDouble(3, fishSize);
                pstmt.setInt(4, fishValue);
                pstmt.setInt(5, legendaryIncrement);
                pstmt.setInt(6, legendaryIncrement);
                pstmt.executeUpdate();
            }
        } else {
            String query = "INSERT OR REPLACE INTO " + tablePrefix + "player_fishing_stats " +
                    "(player_uuid, player_name, total_fish_caught, largest_fish_size, most_valuable_fish, success_rate, last_fishing_time, legendary_fish_caught, hook_material, player_language) " +
                    "SELECT ?, ?, " +
                    "COALESCE(MAX(total_fish_caught), 0) + 1, " +
                    "MAX(?, COALESCE(MAX(largest_fish_size), 0)), " +
                    "MAX(?, COALESCE(MAX(most_valuable_fish), 0)), " +
                    "COALESCE(MAX(success_rate), 0.0), " +
                    "CURRENT_TIMESTAMP, " +
                    "COALESCE(MAX(legendary_fish_caught), 0) + ?, " +
                    "COALESCE(MAX(hook_material), 'wood'), " +
                    "COALESCE(MAX(player_language), 'zh_cn') " +
                    "FROM " + tablePrefix + "player_fishing_stats WHERE player_uuid = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, uuid);
                pstmt.setString(2, name);
                pstmt.setDouble(3, fishSize);
                pstmt.setInt(4, fishValue);
                pstmt.setInt(5, legendaryIncrement);
                pstmt.setString(6, uuid);
                pstmt.executeUpdate();
            }
        }
    }

    /**
     * 关闭数据库连接
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.database_closed", "Database connection closed~"));
            } catch (SQLException e) {
                kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("log.database_close_failed", "Failed to close database connection!")); e.printStackTrace();
            }
        }
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() {
        plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_request", "Requesting database connection"));
        try {
            // 检查连接是否为空、已关闭或不可用
            if (connection == null) {
                plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_empty", "Database connection is null, need to initialize"));
            } else if (connection.isClosed()) {
                plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_closed", "Database connection is closed, need to reconnect"));
            } else if (!connection.isValid(5)) {
                plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_invalid", "Database connection is invalid, need to reconnect"));
            } else {
                plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_valid", "Database connection is valid, returning directly"));
                return connection;
            }
            
            // 关闭旧连接（如果有）
            if (connection != null && !connection.isClosed()) {
                plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_close_old", "Closing old database connection"));
                try {
                    connection.close();
                    plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_closed_old", "Old database connection has been closed"));
                } catch (SQLException e) {
                    kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_close_failed", "Failed to close old database connection!")); e.printStackTrace();
                }
            }
            
            // 重新初始化连接
            plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_reinitialize", "Starting to reinitialize database connection"));
            initialize();
            plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_result", "Database connection result: " + (connection != null ? "Success" : "Failed")));
        } catch (SQLException | ClassNotFoundException e) {
            kkfish.log("§c" + plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_reconnect_failed", "Failed to reconnect to database!")); e.printStackTrace();
            connection = null;
        }
        if (connection == null) {
            throw new RuntimeException("Failed to establish database connection");
        }
        return connection;
    }
    
    /**
     * 调试方法：打印数据库连接状态
     */
    public void debugConnectionStatus() {
        plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_status_header", "===== Database Connection Status ===="));
        plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_initialized", "Initialization status: " + initialized));
        plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_object", "Connection object: " + (connection != null ? "Exists" : "Not exists")));
        
        if (connection != null) {
            try {
                plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_is_closed", "Connection closed: " + connection.isClosed()));
                plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_is_valid", "Connection valid: " + connection.isValid(5)));
                plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_type", "Database type: " + dbType));
            } catch (SQLException e) {
                kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.database_connect_check_error", "Error checking connection status")); e.printStackTrace();
            }
        }
        plugin.getCustomConfig().debugLog("======================");
    }

    /**
     * 检查数据库连接是否可用
     */
    public boolean isDatabaseAvailable() {
        plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_check_availability", "Checking database availability"));
        try {
            // 首先检查是否初始化成功
            if (!initialized) {
                plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_not_initialized", "Database not initialized, unavailable"));
                return false;
            }
            
            boolean result = connection != null && !connection.isClosed() && connection.isValid(5);
            plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("log.database_availability_result", "Database availability check result: %s, Connection: %s", result, (connection != null ? "Exists" : "Not exists")));
            return result;
        } catch (SQLException e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.database_connection_check_error", "Error checking database connection status: ") + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查数据库是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 获取当前使用的数据库类型
     */
    public String getDbType() {
        return dbType;
    }

    /**
     * 获取表前缀
     */
    public String getTablePrefix() {
        return tablePrefix;
    }
    
    /**
     * 添加缓存条目
     */
    private void addToCache(String key, Object value, int expirySeconds) {
        cacheWriteCount++;
        if (cacheWriteCount % 100 == 0) {
            cleanupCache();
        }
        
        // 如果缓存已满，清除一些旧条目
        if (cache.size() >= MAX_CACHE_SIZE) {
            cache.values().removeIf(CacheEntry::isExpired);
            // 如果仍然已满，清除最早的条目
            if (cache.size() >= MAX_CACHE_SIZE) {
                String oldestKey = null;
                long oldestTime = Long.MAX_VALUE;
                for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                    if (entry.getValue().expiryTime < oldestTime) {
                        oldestTime = entry.getValue().expiryTime;
                        oldestKey = entry.getKey();
                    }
                }
                if (oldestKey != null) {
                    cache.remove(oldestKey);
                }
            }
        }
        
        cache.put(key, new CacheEntry(value, expirySeconds));
    }
    
    /**
     * 获取缓存条目
     */
    private Object getFromCache(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            if (!entry.isExpired()) {
                return entry.getValue();
            } else {
                cache.remove(key);
            }
        }
        return null;
    }
    
    /**
     * 清除过期缓存
     */
    private void cleanupCache() {
        cache.values().removeIf(CacheEntry::isExpired);
    }
    
    /**
     * 清除特定缓存
     */
    private void clearCache(String key) {
        cache.remove(key);
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        cache.clear();
    }

    public void clearPlayerCache(String playerId) {
        cache.keySet().removeIf(key -> key.endsWith(":" + playerId) || key.contains(":" + playerId + ":"));
    }
    
    /**
     * 通用方法：从玩家统计数据表获取字符串类型的值
     */
    private boolean isValidColumnName(String name) {
        return name != null && name.matches("[a-zA-Z0-9_]+");
    }

    private String getPlayerStringValue(String playerId, String columnName, String defaultValue, String cacheKeyPrefix, Consumer<String> setter) {
        if (!isValidColumnName(columnName)) {
            return defaultValue;
        }
        // 生成缓存键
        String cacheKey = cacheKeyPrefix + ":" + playerId;
        
        // 先从缓存获取
        Object cachedValue = getFromCache(cacheKey);
        if (cachedValue != null) {
            return (String) cachedValue;
        }
        
        String value = defaultValue;
        try {
            String query = "SELECT " + columnName + " FROM " + tablePrefix + "player_fishing_stats WHERE player_uuid = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, playerId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next() && rs.getString(columnName) != null) {
                    value = rs.getString(columnName);
                } else {
                    // 如果没有记录，先添加默认值
                    setter.accept(value);
                }
                
                // 将结果添加到缓存
                addToCache(cacheKey, value, DEFAULT_CACHE_EXPIRY);
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "获取玩家" + columnName + "失败！"); e.printStackTrace();
        }
        return value;
    }
    
    /**
     * 通用方法：设置玩家统计数据表的字符串类型值
     */
    private void setPlayerStringValue(String playerId, String columnName, String value, String cacheKeyPrefix) {
        if (!isValidColumnName(columnName)) {
            return;
        }
        try {
            // 先检查玩家是否存在
            String checkQuery = "SELECT * FROM " + tablePrefix + "player_fishing_stats WHERE player_uuid = ?";
            try (PreparedStatement checkStmt = getConnection().prepareStatement(checkQuery)) {
                checkStmt.setString(1, playerId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    // 存在则更新
                    String updateQuery = "UPDATE " + tablePrefix + "player_fishing_stats SET " + columnName + " = ? WHERE player_uuid = ?";
                    try (PreparedStatement updateStmt = getConnection().prepareStatement(updateQuery)) {
                        updateStmt.setString(1, value);
                        updateStmt.setString(2, playerId);
                        updateStmt.executeUpdate();
                    }
                } else {
                    // 不存在则插入
                    String insertQuery = "INSERT INTO " + tablePrefix + "player_fishing_stats (player_uuid, " + columnName + ") VALUES (?, ?)";
                    try (PreparedStatement insertStmt = getConnection().prepareStatement(insertQuery)) {
                        insertStmt.setString(1, playerId);
                        insertStmt.setString(2, value);
                        insertStmt.executeUpdate();
                    }
                }
                
                // 清除缓存
                clearCache(cacheKeyPrefix + ":" + playerId);
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "设置玩家" + columnName + "失败！"); e.printStackTrace();
        }
    }
    
    /**
     * 获取玩家语言设置
     */
    public String getPlayerLanguage(String playerId) {
        return getPlayerStringValue(playerId, "player_language", "zh_cn", "language", language -> setPlayerLanguage(playerId, language));
    }
    
    /**
     * 设置玩家语言
     */
    public void setPlayerLanguage(String playerId, String language) {
        setPlayerStringValue(playerId, "player_language", language, "language");
    }
    
    /**
     * 检查玩家是否拥有特定的鱼钩材质
     */
    public boolean hasPlayerPurchasedHook(String playerId, String hookMaterial) {
        // 生成缓存键
        String cacheKey = "purchased_hook:" + playerId + ":" + hookMaterial;
        
        // 先从缓存获取
        Object cachedValue = getFromCache(cacheKey);
        if (cachedValue != null) {
            return (Boolean) cachedValue;
        }
        
        try {
            String query = "SELECT * FROM " + tablePrefix + "player_purchased_hooks WHERE player_uuid = ? AND hook_material = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, playerId);
                pstmt.setString(2, hookMaterial);
                ResultSet rs = pstmt.executeQuery();
                boolean result = rs.next();
                
                // 将结果添加到缓存
                addToCache(cacheKey, result, DEFAULT_CACHE_EXPIRY);
                return result;
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "检查玩家是否拥有鱼钩材质失败！"); e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 将鱼钩材质标记为玩家已购买
     */
    public void markHookAsPurchased(String playerId, String hookMaterial) {
        try {
            String query = (dbType.equals("mysql") ? "REPLACE INTO " : "INSERT OR REPLACE INTO ") + tablePrefix + "player_purchased_hooks (player_uuid, hook_material) VALUES (?, ?)";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, playerId);
                pstmt.setString(2, hookMaterial);
                pstmt.executeUpdate();
                
                // 清除相关缓存
                clearCache("purchased_hook:" + playerId + ":" + hookMaterial);
                clearCache("purchased_hooks:" + playerId);
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "标记鱼钩材质为已购买失败！"); e.printStackTrace();
        }
    }
    
    /**
     * 获取玩家已购买的所有鱼钩材质
     */
    public List<String> getPlayerPurchasedHooks(String playerId) {
        // 生成缓存键
        String cacheKey = "purchased_hooks:" + playerId;
        
        // 先从缓存获取
        Object cachedValue = getFromCache(cacheKey);
        if (cachedValue != null) {
            return new ArrayList<>((List<String>) cachedValue);
        }
        
        List<String> hooks = new ArrayList<>();
        try {
            String query = "SELECT hook_material FROM " + tablePrefix + "player_purchased_hooks WHERE player_uuid = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, playerId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    hooks.add(rs.getString("hook_material"));
                }
                
                // 将结果添加到缓存
                addToCache(cacheKey, hooks, DEFAULT_CACHE_EXPIRY);
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "获取玩家已购买鱼钩材质失败！"); e.printStackTrace();
        }
        return hooks;
    }
    
    /**
     * 获取玩家鱼钩材质
     */
    public String getPlayerHookMaterial(String playerId) {
        return getPlayerStringValue(playerId, "hook_material", "wood", "hook", material -> setPlayerHookMaterial(playerId, material));
    }
    
    /**
     * 设置玩家鱼钩材质
     */
    public void setPlayerHookMaterial(String playerId, String materialType) {
        // 只有在debug模式下才记录调试日志
        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log("[DEBUG] 开始设置玩家鱼钩材质 | 玩家: " + playerId + " 材质: " + materialType);
        }
        try {
            setPlayerStringValue(playerId, "hook_material", materialType, "hook");
        } catch (Exception e) {
            kkfish.log("§c" + "设置玩家鱼钩材质失败！"); e.printStackTrace();
            throw new RuntimeException("数据库操作失败", e); // 重新抛出异常以便调用者能够捕获
        }
    }
    
    /**
     * 获取玩家钓鱼记录
     */
    public FishRecord getPlayerFishRecord(String playerId) {
        FishRecord record = new FishRecord();
        try {
            String query = "SELECT * FROM " + tablePrefix + "player_fishing_stats WHERE player_uuid = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, playerId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    record.setTotalFishCaught(rs.getInt("total_fish_caught"));
                    record.setRareFishCaught(0); // 暂时设为0，后续可完善
                    record.setLegendaryFishCaught(rs.getInt("legendary_fish_caught"));
                }
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "获取玩家钓鱼记录失败！"); e.printStackTrace();
        }
        return record;
    }
    
    /**
     * 获取玩家特定鱼类的钓鱼记录（钓到次数和最大尺寸）
     */
    public Map<String, Object> getPlayerFishStats(String playerId, String fishName) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("caughtCount", 0);
        stats.put("maxSize", 0.0);
        
        try {
            String query = "SELECT COUNT(*) AS caught_count, MAX(fish_size) AS max_size FROM " + tablePrefix + "fishing_log WHERE player_uuid = ? AND fish_name = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, playerId);
                pstmt.setString(2, fishName);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int caughtCount = rs.getInt("caught_count");
                    stats.put("caughtCount", caughtCount);
                    // 只有当caughtCount大于0时，才更新maxSize，否则保持默认值0.0
                    if (caughtCount > 0) {
                        // 使用rs.getObject()检查NULL，避免getDouble()将NULL转换为0.0
                        Object maxSizeObj = rs.getObject("max_size");
                        if (maxSizeObj != null) {
                            stats.put("maxSize", rs.getDouble("max_size"));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "获取玩家特定鱼类记录失败！"); e.printStackTrace();
        }
        
        return stats;
    }
    
    /**
     * 存储鱼的UUID和价值到数据库
     */
    public void storeFishUUIDValue(String fishUUID, int fishValue) {
        try {
            String query = (dbType.equals("mysql") ? "REPLACE INTO " : "INSERT OR REPLACE INTO ") + tablePrefix + "fish_uuid_values (fish_uuid, fish_value) VALUES (?, ?)";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, fishUUID);
                pstmt.setInt(2, fishValue);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "存储鱼UUID价值失败！"); e.printStackTrace();
        }
    }
    
    /**
     * 存储鱼的特殊效果到数据库
     */
    public void storeFishEffects(String fishUUID, List<String> effects) {
        if (effects == null || effects.isEmpty()) {
            return;
        }
        
        try {
            String query = "INSERT INTO " + tablePrefix + "fish_effects (fish_uuid, effect_type, effect_level, effect_duration) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                for (String effectStr : effects) {
                    try {
                        // 解析特效格式: "效果类型 等级:持续时间"
                        String[] parts = effectStr.split(" ");
                        if (parts.length < 2) continue;
                          
                        String effectType = parts[0];
                        String[] levelDuration = parts[1].split(":");
                          
                        if (levelDuration.length < 2) continue;
                          
                        int level = Integer.parseInt(levelDuration[0]);
                        int duration = Integer.parseInt(levelDuration[1]);
                          
                        pstmt.setString(1, fishUUID);
                        pstmt.setString(2, effectType);
                        pstmt.setInt(3, level);
                        pstmt.setInt(4, duration);
                        pstmt.addBatch();
                    } catch (Exception e) {
                        kkfish.log("§e" + "解析鱼特效失败: " + effectStr + " - " + e.getMessage());
                    }
                }
                
                pstmt.executeBatch();
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "存储鱼特效失败！"); e.printStackTrace();
        }
    }
    
    /**
     * 获取鱼的特殊效果
     */
    public List<String> getFishEffectsByUUID(String fishUUID) {
        List<String> effects = new ArrayList<>();
        
        try {
            String query = "SELECT effect_type, effect_level, effect_duration FROM " + tablePrefix + "fish_effects WHERE fish_uuid = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, fishUUID);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    String effectType = rs.getString("effect_type");
                    int level = rs.getInt("effect_level");
                    int duration = rs.getInt("effect_duration");
                    
                    effects.add(effectType + " " + level + ":" + duration);
                }
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "获取鱼特效失败！"); e.printStackTrace();
        }
        
        return effects;
    }
    
    /**
     * 从数据库中获取鱼的价值
     */
    public int getFishValueByUUID(String fishUUID) {
        try {
            String query = "SELECT fish_value FROM " + tablePrefix + "fish_uuid_values WHERE fish_uuid = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, fishUUID);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("fish_value");
                }
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "获取鱼UUID价值失败！"); e.printStackTrace();
        }
        return 0;
    }
    
    /**
     * 从数据库中删除鱼的UUID记录
     */
    public void removeFishUUIDValue(String fishUUID) {
        try {
            String query = "DELETE FROM " + tablePrefix + "fish_uuid_values WHERE fish_uuid = ?";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, fishUUID);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "删除鱼UUID价值失败！"); e.printStackTrace();
        }
    }
    
    /**
     * 解锁玩家特定鱼类的图鉴
     * @param playerId 玩家UUID
     * @param fishName 鱼类名称
     * @param unlockSize 解锁时记录的鱼尺寸
     */
    public void unlockFishForPlayer(String playerId, String fishName, double unlockSize) {
        try {
            // 检查是否已经解锁
            Map<String, Object> stats = getPlayerFishStats(playerId, fishName);
            if ((int) stats.get("caughtCount") > 0) {
                return; // 已经解锁，不需要重复操作
            }
            
            // 插入一条记录，解锁鱼类图鉴
            String query = "INSERT INTO " + tablePrefix + "fishing_log (player_uuid, fish_name, fish_level, fish_size, fish_value, location_x, location_y, location_z, world_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                pstmt.setString(1, playerId);
                pstmt.setString(2, fishName);
                pstmt.setString(3, "common"); // 使用普通等级
                pstmt.setDouble(4, unlockSize);
                pstmt.setInt(5, 0); // 价值为0，不影响实际游戏
                pstmt.setDouble(6, 0); // 默认位置X
                pstmt.setDouble(7, 0); // 默认位置Y
                pstmt.setDouble(8, 0); // 默认位置Z
                pstmt.setString(9, "world"); // 默认世界名称
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "解锁玩家鱼类图鉴失败！"); e.printStackTrace();
        }
    }
    
    /**
     * 锁定玩家特定鱼类的图鉴
     * @param playerId 玩家UUID
     * @param fishName 鱼类名称
     */
    public void lockFishForPlayer(String playerId, String fishName) {
        try {
            String query;
            if ("all".equalsIgnoreCase(fishName)) {
                // 锁定所有鱼类，删除该玩家的所有钓鱼记录
                query = "DELETE FROM " + tablePrefix + "fishing_log WHERE player_uuid = ?";
                try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                    pstmt.setString(1, playerId);
                    pstmt.executeUpdate();
                }
            } else {
                // 锁定特定鱼类，删除该玩家的特定鱼类记录
                query = "DELETE FROM " + tablePrefix + "fishing_log WHERE player_uuid = ? AND fish_name = ?";
                try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                    pstmt.setString(1, playerId);
                    pstmt.setString(2, fishName);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "锁定玩家鱼类图鉴失败！"); e.printStackTrace();
        }
    }
    
    /**
     * 获取所有鱼类名称
     * @return 所有鱼类名称列表
     */
    public List<String> getAllFishNames() {
        List<String> fishNames = new ArrayList<>();
        try {
            // 查询所有不同的鱼类名称
            String query = "SELECT DISTINCT fish_name FROM " + tablePrefix + "fishing_log";
            try (PreparedStatement pstmt = getConnection().prepareStatement(query)) {
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    fishNames.add(rs.getString("fish_name"));
                }
            }
        } catch (SQLException e) {
            kkfish.log("§c" + "获取所有鱼类名称失败！"); e.printStackTrace();
        }
        return fishNames;
    }
    
}