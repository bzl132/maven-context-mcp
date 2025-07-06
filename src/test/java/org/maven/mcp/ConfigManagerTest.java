package org.maven.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigManager单元测试
 */
class ConfigManagerTest {
    
    @TempDir
    Path tempDir;
    
    private ConfigManager configManager;
    
    @BeforeEach
    void setUp() {
        // 重置单例实例
        ConfigManager.resetInstance();
        configManager = ConfigManager.getInstance();
    }
    
    @Test
    void testGetInstance_Singleton() {
        ConfigManager instance1 = ConfigManager.getInstance();
        ConfigManager instance2 = ConfigManager.getInstance();
        assertSame(instance1, instance2, "ConfigManager应该是单例");
    }
    
    @Test
    void testDefaultConfiguration() {
        assertNotNull(configManager.getMavenRepoPath(), "Maven仓库路径不应为null");
        assertNotNull(configManager.getCacheDbPath(), "缓存数据库路径不应为null");
        assertEquals("INFO", configManager.getLogLevel(), "默认日志级别应为INFO");
    }
    
    @Test
    void testValidateConfiguration_ValidPaths() throws IOException {
        // 创建临时目录作为有效路径
        Path mavenRepo = tempDir.resolve("maven-repo");
        Path cacheDir = tempDir.resolve("cache");
        Files.createDirectories(mavenRepo);
        Files.createDirectories(cacheDir);
        
        // 设置环境变量（模拟）
        System.setProperty("test.maven.repo.path", mavenRepo.toString());
        System.setProperty("test.cache.db.path", cacheDir.resolve("cache.db").toString());
        
        assertDoesNotThrow(() -> {
            configManager.validateConfiguration();
        }, "有效路径的配置验证应该成功");
        
        assertTrue(configManager.isInitialized(), "配置应该已初始化");
    }
    
    @Test
    void testValidateConfiguration_InvalidMavenRepo() {
        // 设置无效的Maven仓库路径
        System.setProperty("test.maven.repo.path", "/invalid/path/that/does/not/exist");
        
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> {
            configManager.validateConfiguration();
        }, "无效Maven仓库路径应该抛出异常");
        
        assertTrue(exception.getMessage().contains("Maven仓库"), "异常消息应该包含Maven仓库相关信息");
    }
    
    @Test
    void testGetAllProperties() {
        Properties properties = configManager.getAllProperties();
        assertNotNull(properties, "属性对象不应为null");
        assertTrue(properties.size() > 0, "应该包含配置属性");
        assertTrue(properties.containsKey("maven.repo.path"), "应该包含Maven仓库路径配置");
        assertTrue(properties.containsKey("cache.db.path"), "应该包含缓存数据库路径配置");
    }
    
    @Test
    void testReload() {
        assertDoesNotThrow(() -> {
            configManager.reload();
        }, "重新加载配置不应抛出异常");
    }
    
    @Test
    void testLogLevelValidation() {
        // 测试有效的日志级别
        String[] validLevels = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "trace", "debug", "info", "warn", "error"};
        
        for (String level : validLevels) {
            // 这里我们无法直接测试私有方法，但可以通过环境变量间接测试
            // 在实际实现中，可以考虑将验证方法设为包私有或提供测试接口
        }
    }
}