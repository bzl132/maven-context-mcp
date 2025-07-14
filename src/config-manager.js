import { readFile, access, mkdir } from 'fs/promises';
import { join, resolve } from 'path';
import { homedir } from 'os';

/**
 * 配置管理器类
 */
export class ConfigManager {
  constructor() {
    this.config = {
      mavenRepoPath: join(homedir(), '.m2', 'repository'),
      cacheDbPath: join(homedir(), '.maven-context-mcp', 'cache.db'),
      logLevel: 'info'
    };
  }

  /**
   * 初始化配置
   */
  async initialize() {
    // 从环境变量加载配置
    this.loadFromEnvironment();
    
    // 从配置文件加载配置
    await this.loadFromFile();
    
    // 验证配置
    await this.validateConfig();
    
    // 确保缓存目录存在
    await this.ensureCacheDirectory();
  }

  /**
   * 从环境变量加载配置
   */
  loadFromEnvironment() {
    if (process.env.MAVEN_REPO_PATH) {
      this.config.mavenRepoPath = resolve(process.env.MAVEN_REPO_PATH);
    }
    
    if (process.env.CACHE_DB_PATH) {
      this.config.cacheDbPath = resolve(process.env.CACHE_DB_PATH);
    }
    
    if (process.env.LOG_LEVEL) {
      this.config.logLevel = process.env.LOG_LEVEL.toLowerCase();
    }
  }

  /**
   * 从配置文件加载配置
   */
  async loadFromFile() {
    try {
      const configPath = join(process.cwd(), 'config.json');
      await access(configPath);
      
      const configContent = await readFile(configPath, 'utf-8');
      const fileConfig = JSON.parse(configContent);
      
      // 合并配置，环境变量优先级更高
      if (fileConfig.mavenRepoPath && !process.env.MAVEN_REPO_PATH) {
        this.config.mavenRepoPath = resolve(fileConfig.mavenRepoPath);
      }
      
      if (fileConfig.cacheDbPath && !process.env.CACHE_DB_PATH) {
        this.config.cacheDbPath = resolve(fileConfig.cacheDbPath);
      }
      
      if (fileConfig.logLevel && !process.env.LOG_LEVEL) {
        this.config.logLevel = fileConfig.logLevel.toLowerCase();
      }
      
    } catch (error) {
      // 配置文件不存在或无法读取，使用默认配置
    }
  }

  /**
   * 验证配置
   */
  async validateConfig() {
    try {
      await access(this.config.mavenRepoPath);
    } catch (error) {
      throw new Error(`Maven仓库目录不存在: ${this.config.mavenRepoPath}`);
    }
    
    const validLogLevels = ['error', 'warn', 'info', 'debug'];
    if (!validLogLevels.includes(this.config.logLevel)) {
      throw new Error(`无效的日志级别: ${this.config.logLevel}`);
    }
  }

  /**
   * 确保缓存目录存在
   */
  async ensureCacheDirectory() {
    const cacheDir = this.config.cacheDbPath.substring(0, this.config.cacheDbPath.lastIndexOf('/'));
    const logsDir = join(cacheDir, 'logs');
    
    try {
      await mkdir(cacheDir, { recursive: true });
      await mkdir(logsDir, { recursive: true });
    } catch (error) {
      throw new Error(`无法创建缓存目录: ${error.message}`);
    }
  }

  /**
   * 获取Maven仓库路径
   */
  getMavenRepoPath() {
    return this.config.mavenRepoPath;
  }

  /**
   * 获取缓存数据库路径
   */
  getCacheDbPath() {
    return this.config.cacheDbPath;
  }

  /**
   * 获取日志级别
   */
  getLogLevel() {
    return this.config.logLevel;
  }

  /**
   * 获取所有配置
   */
  getConfig() {
    return { ...this.config };
  }
}