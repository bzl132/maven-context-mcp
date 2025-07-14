#!/bin/bash

# Node.js MCP服务器启动脚本

set -e

# 检查Node.js版本
if ! command -v node &> /dev/null; then
    echo "错误: 未找到Node.js，请先安装Node.js 18或更高版本"
    exit 1
fi

NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 18 ]; then
    echo "错误: Node.js版本过低，需要18或更高版本，当前版本: $(node -v)"
    exit 1
fi

# 检查npm
if ! command -v npm &> /dev/null; then
    echo "错误: 未找到npm"
    exit 1
fi

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 检查package.json是否存在
if [ ! -f "package.json" ]; then
    echo "错误: 未找到package.json文件"
    exit 1
fi

# 安装依赖（如果node_modules不存在）
if [ ! -d "node_modules" ]; then
    echo "安装Node.js依赖..."
    npm install
fi

# 设置环境变量（如果未设置）
export MAVEN_REPO_PATH="${MAVEN_REPO_PATH:-$HOME/.m2/repository}"
export CACHE_DB_PATH="${CACHE_DB_PATH:-./cache/maven-classes.db}"
export LOG_LEVEL="${LOG_LEVEL:-info}"
export LOG_DIR="${LOG_DIR:-./logs}"

# 创建必要的目录
mkdir -p "$(dirname "$CACHE_DB_PATH")"
mkdir -p "$LOG_DIR"

echo "启动Node.js MCP服务器..."
echo "Maven仓库路径: $MAVEN_REPO_PATH"
echo "缓存数据库路径: $CACHE_DB_PATH"
echo "日志级别: $LOG_LEVEL"
echo "日志目录: $LOG_DIR"
echo ""

# 启动服务器
node src/index.js