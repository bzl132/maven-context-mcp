#!/bin/bash

# 简化构建脚本（不依赖Maven）

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "开始构建Maven Context MCP项目..."

# 创建目录结构
mkdir -p target/classes
mkdir -p target/lib

# 下载依赖JAR文件（如果不存在）
LIB_DIR="target/lib"

# 依赖列表
declare -A DEPENDENCIES=(
    ["jackson-databind-2.15.2.jar"]="https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.15.2/jackson-databind-2.15.2.jar"
    ["jackson-core-2.15.2.jar"]="https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.15.2/jackson-core-2.15.2.jar"
    ["jackson-annotations-2.15.2.jar"]="https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.15.2/jackson-annotations-2.15.2.jar"
    ["asm-9.5.jar"]="https://repo1.maven.org/maven2/org/ow2/asm/asm/9.5/asm-9.5.jar"
    ["sqlite-jdbc-3.42.0.0.jar"]="https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.42.0.0/sqlite-jdbc-3.42.0.0.jar"
    ["slf4j-api-2.0.7.jar"]="https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.7/slf4j-api-2.0.7.jar"
    ["logback-classic-1.4.8.jar"]="https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.4.8/logback-classic-1.4.8.jar"
    ["logback-core-1.4.8.jar"]="https://repo1.maven.org/maven2/ch/qos/logback/logback-core/1.4.8/logback-core-1.4.8.jar"
)

# 下载依赖
echo "下载依赖..."
for jar in "${!DEPENDENCIES[@]}"; do
    if [ ! -f "$LIB_DIR/$jar" ]; then
        echo "下载 $jar..."
        curl -L -o "$LIB_DIR/$jar" "${DEPENDENCIES[$jar]}"
        if [ $? -ne 0 ]; then
            echo "警告: 下载 $jar 失败，将尝试继续构建"
        fi
    else
        echo "$jar 已存在，跳过下载"
    fi
done

# 构建classpath
CLASSPATH="target/classes"
for jar in "$LIB_DIR"/*.jar; do
    if [ -f "$jar" ]; then
        CLASSPATH="$CLASSPATH:$jar"
    fi
done

echo "使用classpath: $CLASSPATH"

# 编译Java源文件
echo "编译Java源文件..."
find src/main/java -name "*.java" -type f > sources.txt

if [ -s sources.txt ]; then
    javac -cp "$CLASSPATH" -d target/classes @sources.txt
    if [ $? -eq 0 ]; then
        echo "编译成功"
    else
        echo "编译失败"
        exit 1
    fi
else
    echo "未找到Java源文件"
    exit 1
fi

# 复制资源文件
echo "复制资源文件..."
if [ -d "src/main/resources" ]; then
    cp -r src/main/resources/* target/classes/ 2>/dev/null || true
fi

# 创建可执行JAR
echo "创建可执行JAR..."
cd target/classes

# 创建MANIFEST.MF
mkdir -p META-INF
cat > META-INF/MANIFEST.MF << EOF
Manifest-Version: 1.0
Main-Class: org.maven.mcp.McpServer
Class-Path: $(for jar in ../../target/lib/*.jar; do basename "$jar"; done | tr '\n' ' ')

EOF

# 打包JAR
jar cfm ../maven-context-mcp-1.0.0.jar META-INF/MANIFEST.MF .
cd ../..

# 复制依赖到target目录
cp target/lib/*.jar target/ 2>/dev/null || true

echo "构建完成！"
echo "可执行文件: target/maven-context-mcp-1.0.0.jar"
echo "运行命令: java -cp 'target/*' org.maven.mcp.McpServer"

# 清理临时文件
rm -f sources.txt

echo "构建脚本执行完成"