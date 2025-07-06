# Maven Context MCP Server

一个基于Java的MCP（Model Context Protocol）服务器，用于帮助AI在Maven本地仓库中搜索和查找JAR文件内的类信息。

## 功能特性

- 🔍 **智能搜索**: 支持按类名、包名、方法名进行模糊搜索
- 🎯 **忽略大小写**: 搜索时自动忽略大小写
- 💾 **智能缓存**: 使用SQLite缓存，支持增量更新
- 🔄 **手动更新**: 支持AI调用MCP工具手动更新缓存
- 📊 **详细信息**: 提供完整的类信息，包括方法签名和字段
- 📁 **类文件内容**: 支持获取和缓存类文件字节码内容
- 🔐 **Base64编码**: 类文件内容以Base64格式返回，便于AI处理
- ⚡ **高性能**: 使用ASM字节码解析，快速扫描JAR文件

## 系统要求

- Java 8 或更高版本
- Maven 3.6 或更高版本
- Maven本地仓库（通常位于 `~/.m2/repository`）

## 快速开始

### 1. 构建项目

```bash
# 克隆或下载项目到本地
cd maven-context-mcp

# 构建项目
mvn clean package
```

### 2. 启动服务器

```bash
# 使用启动脚本（推荐）
./start.sh

# 或者直接运行JAR文件
java -jar target/maven-context-mcp-1.0.0.jar
```

### 3. 配置MCP客户端

在您的MCP客户端配置中添加以下配置：

```json
{
  "mcpServers": {
    "maven-context": {
      "command": "java",
      "args": ["-jar", "/path/to/maven-context-mcp-1.0.0.jar"]
    }
  }
}
```

## MCP工具说明

### 1. search_class

在Maven本地仓库中搜索类，支持模糊搜索和忽略大小写。

**参数:**
- `query` (必需): 搜索关键词，可以是类名、包名或方法名
- `limit` (可选): 返回结果数量限制，默认50

**示例:**
```json
{
  "name": "search_class",
  "arguments": {
    "query": "ArrayList",
    "limit": 10
  }
}
```

### 2. get_class_detail

获取指定类的详细信息，包括所有方法和字段。

**参数:**
- `className` (必需): 完整的类名（包含包名）

**示例:**
```json
{
  "name": "get_class_detail",
  "arguments": {
    "className": "java.util.ArrayList"
  }
}
```

### 3. get_class_content

获取指定类的字节码内容，以Base64编码格式返回。

**参数:**
- `className` (必需): 完整的类名（包含包名）

**示例:**
```json
{
  "name": "get_class_content",
  "arguments": {
    "className": "java.util.ArrayList"
  }
}
```

### 4. update_cache

手动更新Maven仓库缓存，扫描新的JAR文件。

**参数:**
- `force` (可选): 是否强制重新扫描所有JAR文件，默认false

**示例:**
```json
{
  "name": "update_cache",
  "arguments": {
    "force": false
  }
}
```

## 配置选项
### MCP配置（环境变量）

通过环境变量配置MCP服务器，这些配置具有最高优先级：

- `MAVEN_REPO_PATH`: Maven仓库路径（默认: `~/.m2/repository`）
- `CACHE_DB_PATH`: 缓存数据库路径（默认: `~/.maven-context-mcp/cache.db`）
- `LOG_LEVEL`: 日志级别（DEBUG, INFO, WARN, ERROR，默认: INFO）

### MCP客户端配置示例

在MCP客户端（如Claude Desktop）中配置环境变量：

```json
{
  "mcpServers": {
    "maven-context": {
      "command": "java",
      "args": ["-jar", "/path/to/maven-context-mcp-1.0.0.jar"],
      "env": {
        "MAVEN_REPO_PATH": "/custom/maven/repository",
        "CACHE_DB_PATH": "/custom/cache/location/cache.db",
        "LOG_LEVEL": "DEBUG"
      }
    }
  }
}
```

### 配置文件

也可以通过 `src/main/resources/application.properties` 文件配置（优先级低于环境变量）：

```properties
maven.repo.path=${user.home}/.m2/repository
cache.db.path=${user.home}/.maven-context-mcp/cache.db
log.level=INFO
```

### JVM参数

```bash
# 调整内存设置
java -Xmx1g -Xms512m -jar maven-context-mcp-1.0.0.jar

# 设置日志级别
java -Droot.level=DEBUG -jar maven-context-mcp-1.0.0.jar
```

## 缓存机制

- **自动初始化**: 首次运行时自动扫描Maven仓库
- **增量更新**: 只扫描修改过的JAR文件
- **智能缓存**: 基于文件修改时间判断是否需要重新扫描
- **数据库存储**: 使用SQLite存储，支持复杂查询和索引

## 搜索算法

搜索结果按以下优先级排序：

1. **完全匹配简单类名** (分数: 1000)
2. **简单类名开头匹配** (分数: 500)
3. **简单类名包含匹配** (分数: 200)
4. **完整类名包含匹配** (分数: 100)
5. **包名匹配** (分数: 50)
6. **方法名匹配** (分数: 30)
7. **字段名匹配** (分数: 20)

## 日志和调试

### 日志文件位置

- 日志文件: `~/.maven-context-mcp/logs/mcp-server.log`
- 日志轮转: 每天轮转，保留30天
- 最大文件大小: 10MB

### 调试模式

```bash
# 启用调试日志
export LOG_LEVEL=DEBUG
./start.sh
```

## 性能优化

### 大型仓库优化

对于包含大量JAR文件的Maven仓库：

1. **增加内存**: `-Xmx2g -Xms1g`
2. **并行扫描**: 未来版本将支持
3. **选择性扫描**: 可以通过配置排除某些目录

### 数据库优化

- 自动创建索引提高查询性能
- 使用预编译语句防止SQL注入
- 支持事务确保数据一致性

## 故障排除

### 常见问题

1. **找不到Maven仓库**
   ```
   错误: Maven仓库目录不存在
   解决: 检查 ~/.m2/repository 是否存在
   ```

2. **权限问题**
   ```
   错误: 无法创建缓存目录
   解决: 确保有写入 ~/.maven-context-mcp 的权限
   ```

3. **内存不足**
   ```
   错误: OutOfMemoryError
   解决: 增加JVM内存 -Xmx1g
   ```

4. **JAR文件损坏**
   ```
   警告: 扫描JAR文件失败
   解决: 检查JAR文件完整性，重新下载依赖
   ```

### 重置缓存

```bash
# 删除缓存数据库
rm -rf ~/.maven-context-mcp/cache.db

# 重新启动服务器将自动重建缓存
./start.sh
```

## 开发和贡献

### 项目结构

```
src/main/java/org/maven/mcp/
├── McpServer.java              # MCP服务器主类
├── MavenRepositoryScanner.java # Maven仓库扫描器
├── ClassSearchService.java     # 类搜索服务
├── ClassInfo.java             # 类信息数据模型
├── ConfigManager.java          # 配置管理器
└── ConfigurationException.java # 配置异常类
```

### 构建和测试

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 打包
mvn package

# 清理
mvn clean
```

## 许可证

本项目采用 MIT 许可证。详见 LICENSE 文件。

## 更新日志

### v1.1.0
- 🔄 **包路径重构**: 从 `com.example.maven.mcp` 迁移到 `org.maven.mcp`
- 📁 **类文件内容缓存**: 新增类文件字节码内容的缓存和获取功能
- 🔐 **Base64编码支持**: 类文件内容以Base64格式返回
- 🛠️ **配置管理优化**: 改进配置管理和异常处理
- 📝 **代码注释完善**: 添加详细的代码注释和文档
- 🧪 **测试脚本增强**: 完善测试脚本和验证流程

### v1.0.0
- 初始版本发布
- 支持基本的类搜索功能
- 实现SQLite缓存机制
- 提供完整的MCP协议支持