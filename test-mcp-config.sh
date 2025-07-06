#!/bin/bash

# 测试MCP配置功能的脚本

echo "测试MCP配置功能..."
echo "设置环境变量:"
echo "  MAVEN_REPO_PATH=/tmp/test-maven-repo"
echo "  CACHE_DB_PATH=/tmp/test-cache.db"
echo "  LOG_LEVEL=DEBUG"
echo ""

# 设置环境变量
export MAVEN_REPO_PATH="/tmp/test-maven-repo"
export CACHE_DB_PATH="/tmp/test-cache.db"
export LOG_LEVEL="DEBUG"

# 启动服务器
echo "启动MCP服务器..."
java -cp 'target/*' org.maven.mcp.McpServer