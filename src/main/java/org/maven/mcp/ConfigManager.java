package org.maven.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 配置管理器，负责加载和管理应用程序配置
 * 支持从MCP配置（环境变量）、应用程序配置文件和默认值中读取配置
 * 提供配置验证和错误处理功能
 */
public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE = "application.properties";
    private static ConfigManager instance;
    private Properties properties;
    private boolean initialized = false;
    
    private ConfigManager() {
        loadProperties();
    }
    
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }
    
    /**
     * 重置单例实例（仅用于测试）
     */
    static synchronized void resetInstance() {
        instance = null;
    }
    
    private void loadProperties() {
        properties = new Properties();
        
        // 1. 设置默认值
        setDefaults();
        
        // 2. 从classpath加载配置文件（覆盖默认值）
        loadFromConfigFile();
        
        // 3. 从MCP配置（环境变量）加载（最高优先级）
        loadFromMcpConfig();
        
        // 4. 解析系统属性占位符
        resolveSystemProperties();
    }
    
    private void setDefaults() {
        properties.setProperty("maven.repo.path", "${user.home}/.m2/repository");
        properties.setProperty("cache.db.path", "${user.home}/.maven-context-mcp/cache.db");
    }
    
    private void loadFromConfigFile() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
                logger.info("成功加载配置文件: {}", CONFIG_FILE);
            } else {
                logger.warn("配置文件 {} 不存在，使用默认配置", CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.warn("无法加载配置文件 {}，使用默认配置: {}", CONFIG_FILE, e.getMessage());
        }
    }
    
    /**
     * 从MCP配置（环境变量）中加载配置
     * 这是最高优先级的配置源，会覆盖配置文件和默认值
     */
    private void loadFromMcpConfig() {
        // 从环境变量读取Maven仓库路径
        String mavenRepoPath = System.getenv("MAVEN_REPO_PATH");
        if (mavenRepoPath != null && !mavenRepoPath.trim().isEmpty()) {
            properties.setProperty("maven.repo.path", mavenRepoPath.trim());
            logger.info("从MCP配置加载Maven仓库路径: {}", mavenRepoPath);
        }
        
        // 从环境变量读取缓存数据库路径
        String cacheDbPath = System.getenv("CACHE_DB_PATH");
        if (cacheDbPath != null && !cacheDbPath.trim().isEmpty()) {
            properties.setProperty("cache.db.path", cacheDbPath.trim());
            logger.info("从MCP配置加载缓存数据库路径: {}", cacheDbPath);
        }
        
        // 支持其他可能的MCP配置参数
        String logLevel = System.getenv("LOG_LEVEL");
        if (logLevel != null && !logLevel.trim().isEmpty()) {
            if (isValidLogLevel(logLevel.trim())) {
                properties.setProperty("log.level", logLevel.trim());
                logger.info("从MCP配置加载日志级别: {}", logLevel);
            } else {
                logger.warn("无效的日志级别: {}，使用默认值", logLevel);
            }
        }
    }
    
    private void resolveSystemProperties() {
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if (value.contains("${user.home}")) {
                value = value.replace("${user.home}", System.getProperty("user.home"));
                properties.setProperty(key, value);
            }
            // 支持其他系统属性占位符
            if (value.contains("${java.io.tmpdir}")) {
                value = value.replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir"));
                properties.setProperty(key, value);
            }
        }
    }
    
    public String getMavenRepoPath() {
        return properties.getProperty("maven.repo.path");
    }
    
    public String getCacheDbPath() {
        return properties.getProperty("cache.db.path");
    }
    
    public String getLogLevel() {
        return properties.getProperty("log.level", "INFO");
    }
    
    /**
     * 验证日志级别是否有效
     */
    private boolean isValidLogLevel(String logLevel) {
        return logLevel != null && 
               ("TRACE".equalsIgnoreCase(logLevel) ||
                "DEBUG".equalsIgnoreCase(logLevel) ||
                "INFO".equalsIgnoreCase(logLevel) ||
                "WARN".equalsIgnoreCase(logLevel) ||
                "ERROR".equalsIgnoreCase(logLevel));
    }
    
    /**
     * 验证配置的有效性
     */
    public void validateConfiguration() throws ConfigurationException {
        validateMavenRepoPath();
        validateCacheDbPath();
        initialized = true;
        logger.info("配置验证完成");
    }
    
    /**
     * 验证Maven仓库路径
     */
    private void validateMavenRepoPath() throws ConfigurationException {
        String mavenRepoPath = getMavenRepoPath();
        if (mavenRepoPath == null || mavenRepoPath.trim().isEmpty()) {
            throw new ConfigurationException("Maven仓库路径不能为空");
        }
        
        Path repoPath = Paths.get(mavenRepoPath);
        if (!Files.exists(repoPath)) {
            logger.warn("Maven仓库路径不存在，将尝试创建: {}", mavenRepoPath);
            try {
                Files.createDirectories(repoPath);
                logger.info("成功创建Maven仓库目录: {}", mavenRepoPath);
            } catch (IOException e) {
                throw new ConfigurationException("无法创建Maven仓库目录: " + mavenRepoPath, e);
            }
        }
        
        if (!Files.isDirectory(repoPath)) {
            throw new ConfigurationException("Maven仓库路径不是目录: " + mavenRepoPath);
        }
        
        if (!Files.isReadable(repoPath)) {
            throw new ConfigurationException("Maven仓库路径不可读: " + mavenRepoPath);
        }
    }
    
    /**
     * 验证缓存数据库路径
     */
    private void validateCacheDbPath() throws ConfigurationException {
        String cacheDbPath = getCacheDbPath();
        if (cacheDbPath == null || cacheDbPath.trim().isEmpty()) {
            throw new ConfigurationException("缓存数据库路径不能为空");
        }
        
        Path dbPath = Paths.get(cacheDbPath);
        Path parentDir = dbPath.getParent();
        
        if (parentDir != null && !Files.exists(parentDir)) {
            try {
                Files.createDirectories(parentDir);
                logger.info("成功创建缓存目录: {}", parentDir);
            } catch (IOException e) {
                throw new ConfigurationException("无法创建缓存目录: " + parentDir, e);
            }
        }
        
        if (parentDir != null && !Files.isWritable(parentDir)) {
            throw new ConfigurationException("缓存目录不可写: " + parentDir);
        }
    }
    
    /**
     * 重新加载配置
     */
    public synchronized void reload() {
        logger.info("重新加载配置");
        initialized = false;
        loadProperties();
        try {
            validateConfiguration();
        } catch (ConfigurationException e) {
            logger.error("配置验证失败: {}", e.getMessage());
        }
    }
    
    /**
     * 检查配置是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 获取所有配置属性（用于调试）
     */
    public Properties getAllProperties() {
        return new Properties(properties);
    }
}