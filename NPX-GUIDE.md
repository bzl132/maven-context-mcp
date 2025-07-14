# Maven Context MCP - NPX 使用指南

本指南介绍如何通过 NPX 配置和使用 Maven Context MCP 服务器。

## 什么是 NPX

NPX 是 npm 5.2+ 版本自带的包执行器，允许你直接运行 npm 包而无需全局安装。

## 快速开始

### 1. 直接运行（推荐）

```bash
# 使用默认配置运行
npx maven-context-mcp

# 指定 Maven 仓库路径
npx maven-context-mcp --maven-repo /path/to/your/maven/repo

# 指定日志级别
npx maven-context-mcp --log-level debug

# 完整配置示例
npx maven-context-mcp \
  --maven-repo ~/.m2/repository \
  --cache-db ./cache/classes.db \
  --log-level info \
  --log-dir ./logs
```

### 2. 全局安装后使用

```bash
# 全局安装
npm install -g maven-context-mcp

# 直接运行
maven-context-mcp

# 或使用参数
maven-context-mcp --maven-repo ~/.m2/repository --log-level debug
```

## 命令行选项

| 选项 | 描述 | 默认值 |
|------|------|--------|
| `--maven-repo <path>` | Maven 仓库路径 | `~/.m2/repository` |
| `--cache-db <path>` | 缓存数据库路径 | `./cache/maven-classes.db` |
| `--log-level <level>` | 日志级别 (error, warn, info, debug) | `info` |
| `--log-dir <path>` | 日志文件目录 | `./logs` |
| `-h, --help` | 显示帮助信息 | - |
| `-v, --version` | 显示版本信息 | - |

## MCP 客户端配置

### Tongyi Lingma 配置

在 Tongyi Lingma 的 MCP 配置中：

```json
{
  "mcpServers": {
    "maven-context": {
      "command": "npx",
      "args": [
        "maven-context-mcp",
        "--maven-repo", "/Users/jiaolongbao/.m2/repository",
        "--log-level", "info"
      ],
      "env": {
        "NODE_ENV": "production"
      }
    }
  }
}
```

"maven-context": {
      "command": "npx",
      "args": [
        "maven-context-mcp",
        "--maven-repo", "/Users/jiaolongbao/tools/apache-maven-3.6.3/repo",
        "--log-level", "info"
      ]
    }

    npx maven-context-mcp --maven-repo /Users/jiaolongbao/tools/apache-maven-3.6.3/repo --log-level debug

### Claude Desktop 配置

在 Claude Desktop 的配置文件中：

```json
{
  "mcpServers": {
    "maven-context": {
      "command": "npx",
      "args": [
        "maven-context-mcp",
        "--maven-repo", "/Users/jiaolongbao/tools/apache-maven-3.6.3/repo",
        "--log-level", "debug"
      ]
    }
  }
}
```

### 其他 MCP 客户端

对于支持 MCP 协议的其他客户端，使用类似的配置：

```json
{
  "command": "npx",
  "args": ["maven-context-mcp", "--maven-repo", "/path/to/maven/repo"]
}
```

## 环境变量配置

除了命令行参数，你也可以使用环境变量：

```bash
# 设置环境变量
export MAVEN_REPO_PATH="/Users/username/.m2/repository"
export CACHE_DB_PATH="./cache/maven-classes.db"
export LOG_LEVEL="info"
export LOG_DIR="./logs"

# 运行服务器
npx maven-context-mcp
```

## 优势

### 1. 无需本地安装
- 不需要克隆仓库
- 不需要手动安装依赖
- NPX 会自动下载和运行最新版本

### 2. 版本管理
```bash
# 运行特定版本
npx maven-context-mcp@1.0.0

# 总是使用最新版本
npx maven-context-mcp@latest
```

### 3. 简化配置
- 统一的命令行接口
- 清晰的参数传递
- 标准的帮助和版本信息

## 故障排除

### 1. NPX 找不到包

如果遇到 "package not found" 错误：

```bash
# 清除 NPX 缓存
npx clear-npx-cache

# 或者强制重新下载
npx --yes maven-context-mcp
```

### 2. 权限问题

在某些系统上可能需要额外权限：

```bash
# macOS/Linux
sudo npx maven-context-mcp

# 或者使用用户级别的 npm 配置
npm config set prefix ~/.npm-global
export PATH=~/.npm-global/bin:$PATH
```

### 3. Node.js 版本问题

确保 Node.js 版本 >= 18：

```bash
# 检查版本
node --version

# 如果版本过低，使用 nvm 升级
nvm install 18
nvm use 18
```

### 4. 网络问题

如果下载缓慢，可以配置 npm 镜像：

```bash
# 使用淘宝镜像
npm config set registry https://registry.npmmirror.com

# 或者临时使用
npx --registry https://registry.npmmirror.com maven-context-mcp
```

## 开发和调试

### 本地开发

如果你正在开发或修改代码：

```bash
# 克隆仓库
git clone <repository-url>
cd maven-context-mcp

# 安装依赖
npm install

# 本地链接（用于测试 npx 功能）
npm link

# 测试本地版本
npx maven-context-mcp
```

### 调试模式

```bash
# 启用详细日志
npx maven-context-mcp --log-level debug

# 使用 Node.js 调试器
node --inspect $(which maven-context-mcp)
```

## 发布到 NPM

如果你想发布自己的版本：

```bash
# 登录 NPM
npm login

# 发布
npm publish

# 发布测试版本
npm publish --tag beta
```

## 最佳实践

1. **在 MCP 配置中使用绝对路径**：
   ```json
   {
     "args": ["maven-context-mcp", "--maven-repo", "/Users/username/.m2/repository"]
   }
   ```

2. **设置合适的日志级别**：
   - 生产环境：`info` 或 `warn`
   - 开发环境：`debug`

3. **使用环境变量进行配置**：
   ```bash
   # 在 ~/.bashrc 或 ~/.zshrc 中
   export MAVEN_REPO_PATH="$HOME/.m2/repository"
   export LOG_LEVEL="info"
   ```

4. **定期更新**：
   ```bash
   # NPX 会自动使用最新版本，但可以强制更新缓存
   npx clear-npx-cache
   npx maven-context-mcp
   ```

通过 NPX，你可以轻松地在任何支持 MCP 的客户端中使用 Maven Context MCP，无需复杂的安装和配置过程。