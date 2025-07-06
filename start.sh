#!/bin/bash

# Maven Context MCP Server 启动脚本

# 设置脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境，请确保已安装Java 11或更高版本"
    exit 1
fi

# 检查Java版本
JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 11 ]; then
    echo "错误: 需要Java 11或更高版本，当前版本: $JAVA_VERSION"
    exit 1
fi

# 检查Maven
if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到Maven，请确保已安装Maven"
    exit 1
fi

# 构建项目（如果需要）
if [ ! -f "target/maven-context-mcp-1.0.0.jar" ]; then
    echo "正在构建项目..."
    mvn clean package -q
    if [ $? -ne 0 ]; then
        echo "错误: 项目构建失败"
        exit 1
    fi
fi

# 设置JVM参数
JVM_OPTS="-Xmx512m -Xms256m"

# 设置日志级别（可选：DEBUG, INFO, WARN, ERROR）
LOG_LEVEL="INFO"

# 启动MCP服务器
echo "启动Maven Context MCP服务器..."
echo "日志级别: $LOG_LEVEL"
echo "Maven仓库: $HOME/.m2/repository"
echo "缓存位置: $HOME/.maven-context-mcp/cache.db"
echo "---"

java $JVM_OPTS \
    -Dlogback.configurationFile=logback.xml \
    -Droot.level=$LOG_LEVEL \
    -jar target/maven-context-mcp-1.0.0.jar

echo "MCP服务器已停止"