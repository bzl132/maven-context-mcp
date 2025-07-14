# Maven Context MCP - Node.js 实现

这是 Maven Context MCP 的 Node.js 实现版本，提供了与 Java 版本相同的功能，但使用 Node.js 运行时。

## 系统要求

- Node.js 18.0 或更高版本
- npm 包管理器
- Maven 仓库（通常位于 `~/.m2/repository`）

## 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 配置环境变量

```bash
export MAVEN_REPO_PATH="$HOME/.m2/repository"
export CACHE_DB_PATH="./cache/maven-classes.db"
export LOG_LEVEL="info"
export LOG_DIR="./logs"
```

### 3. 启动服务器

使用启动脚本：
```bash
./start-node.sh
```

或直接运行：
```bash
node src/index.js
```

## 配置选项

### 环境变量

| 变量名 | 默认值 | 描述 |
|--------|--------|------|
| `MAVEN_REPO_PATH` | `$HOME/.m2/repository` | Maven 本地仓库路径 |
| `CACHE_DB_PATH` | `./cache/maven-classes.db` | SQLite 缓存数据库路径 |
| `LOG_LEVEL` | `info` | 日志级别 (error, warn, info, debug) |
| `LOG_DIR` | `./logs` | 日志文件目录 |

### 配置文件

可以创建 `config.json` 文件来覆盖默认配置：

```json
{
  "mavenRepoPath": "/path/to/maven/repository",
  "cacheDbPath": "./cache/maven-classes.db",
  "logLevel": "info",
  "logDir": "./logs"
}
```

## 可用工具

### 1. search_class
搜索 Java 类

**参数：**
- `query` (string): 搜索查询字符串
- `limit` (number, 可选): 返回结果的最大数量，默认 50

**示例：**
```json
{
  "tool": "search_class",
  "arguments": {
    "query": "ArrayList",
    "limit": 10
  }
}
```

### 2. get_class_detail
获取类的详细信息

**参数：**
- `className` (string): 完整的类名

**示例：**
```json
{
  "tool": "get_class_detail",
  "arguments": {
    "className": "java.util.ArrayList"
  }
}
```

### 3. get_class_content
获取类的字节码内容

**参数：**
- `className` (string): 完整的类名

**示例：**
```json
{
  "tool": "get_class_content",
  "arguments": {
    "className": "java.util.ArrayList"
  }
}
```

### 4. update_cache
更新 Maven 仓库缓存

**参数：**
- `force` (boolean, 可选): 是否强制重新扫描所有 JAR 文件，默认 false

**示例：**
```json
{
  "tool": "update_cache",
  "arguments": {
    "force": true
  }
}
```

## 客户端配置

### Tongyi Lingma 配置

在 Tongyi Lingma 中配置 MCP 服务器：

```json
{
  "mcpServers": {
    "maven-context": {
      "command": "node",
      "args": ["/path/to/maven-context-mcp/src/index.js"],
      "env": {
        "MAVEN_REPO_PATH": "/Users/username/.m2/repository",
        "LOG_LEVEL": "info"
      }
    }
  }
}
```

### Claude Desktop 配置

```json
{
  "mcpServers": {
    "maven-context": {
      "command": "node",
      "args": ["/path/to/maven-context-mcp/src/index.js"],
      "env": {
        "MAVEN_REPO_PATH": "/Users/username/.m2/repository"
      }
    }
  }
}
```

## 项目结构

```
maven-context-mcp/
├── src/
│   ├── index.js                    # 主入口文件
│   ├── config-manager.js           # 配置管理器
│   ├── mcp-server.js              # MCP 服务器实现
│   ├── maven-repository-scanner.js # Maven 仓库扫描器
│   └── class-search-service.js     # 类搜索服务
├── package.json                    # Node.js 项目配置
├── start-node.sh                   # 启动脚本
├── config.json.example             # 配置文件示例
└── README-nodejs.md               # 本文档
```

## 性能优化

### 缓存机制
- 使用 SQLite 数据库缓存类信息
- 支持增量更新，只扫描修改过的 JAR 文件
- 自动创建索引以提高搜索性能

### 内存管理
- 流式处理 JAR 文件，避免大文件占用过多内存
- 使用连接池管理数据库连接
- 及时释放不需要的资源

## 故障排除

### 常见问题

1. **服务器无法启动**
   - 检查 Node.js 版本是否 >= 18
   - 确认所有依赖已正确安装
   - 检查日志文件获取详细错误信息

2. **搜索结果为空**
   - 确认 Maven 仓库路径正确
   - 运行 `update_cache` 工具重新扫描
   - 检查缓存数据库是否可写

3. **性能问题**
   - 调整日志级别为 `warn` 或 `error`
   - 确保缓存数据库在 SSD 上
   - 考虑增加系统内存

### 调试模式

启用调试日志：
```bash
export LOG_LEVEL="debug"
node src/index.js
```

### 日志文件

日志文件位于 `LOG_DIR` 目录中：
- `maven-mcp.log`: 主日志文件
- `maven-mcp-error.log`: 错误日志文件

## 与 Java 版本的差异

1. **运行时要求**：Node.js 18+ vs Java 8+
2. **依赖管理**：npm vs Maven
3. **启动脚本**：`start-node.sh` vs `start.sh`
4. **性能特征**：JavaScript 异步处理 vs Java 多线程

## 开发

### 添加新功能

1. 在 `src/mcp-server.js` 中添加新的工具方法
2. 更新 `config.json.example` 中的工具定义
3. 添加相应的测试用例

### 代码风格

- 使用 ES6+ 语法
- 遵循 JavaScript Standard Style
- 添加适当的 JSDoc 注释

## 许可证

本项目采用 MIT 许可证。