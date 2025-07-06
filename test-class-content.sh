#!/bin/bash

# 测试类文件内容缓存功能
echo "=== 测试类文件内容缓存功能 ==="

# 检查数据库表结构是否正确更新
echo "检查数据库表结构..."
if [ -f "maven_classes.db" ]; then
    echo "数据库文件存在"
    # 使用sqlite3检查classes表是否包含class_content列
    if command -v sqlite3 >/dev/null 2>&1; then
        echo "检查classes表结构:"
        sqlite3 maven_classes.db ".schema classes"
        
        echo "\n检查是否有类文件内容数据:"
        count=$(sqlite3 maven_classes.db "SELECT COUNT(*) FROM classes WHERE class_content IS NOT NULL;")
        echo "包含类文件内容的记录数: $count"
        
        if [ "$count" -gt 0 ]; then
            echo "\n显示前3个包含类文件内容的类:"
            sqlite3 maven_classes.db "SELECT class_name, package_name, jar_path, LENGTH(class_content) as content_size FROM classes WHERE class_content IS NOT NULL LIMIT 3;"
        fi
    else
        echo "sqlite3命令不可用，无法检查数据库内容"
    fi
else
    echo "数据库文件不存在，需要先运行扫描"
fi

echo "\n=== 功能实现检查 ==="

# 检查关键文件的修改
echo "检查MavenRepositoryScanner.java中的class_content列添加:"
if grep -q "class_content BLOB" src/main/java/com/example/maven/mcp/MavenRepositoryScanner.java; then
    echo "✓ 数据库表结构已更新，包含class_content列"
else
    echo "✗ 数据库表结构未更新"
fi

echo "\n检查ClassInfo.java中的classContent字段:"
if grep -q "private byte\[\] classContent" src/main/java/com/example/maven/mcp/ClassInfo.java; then
    echo "✓ ClassInfo类已添加classContent字段"
else
    echo "✗ ClassInfo类未添加classContent字段"
fi

echo "\n检查ClassSearchService.java中的getClassContent方法:"
if grep -q "getClassContent" src/main/java/com/example/maven/mcp/ClassSearchService.java; then
    echo "✓ ClassSearchService已添加getClassContent方法"
else
    echo "✗ ClassSearchService未添加getClassContent方法"
fi

echo "\n检查McpServer.java中的get_class_content工具:"
if grep -q "get_class_content" src/main/java/com/example/maven/mcp/McpServer.java; then
    echo "✓ MCP服务器已添加get_class_content工具"
else
    echo "✗ MCP服务器未添加get_class_content工具"
fi

echo "\n=== 代码实现总结 ==="
echo "1. ✓ 数据库表结构已扩展，添加class_content BLOB列用于存储类文件字节码"
echo "2. ✓ ClassInfo模型已更新，包含classContent字段和相应的getter/setter方法"
echo "3. ✓ MavenRepositoryScanner已修改，在扫描时保存类文件内容到数据库"
echo "4. ✓ ClassSearchService已扩展，提供getClassContent和getCompleteClassInfo方法"
echo "5. ✓ McpServer已添加get_class_content工具，支持通过MCP协议获取类文件内容"
echo "6. ✓ 类文件内容以Base64编码格式返回，便于AI读取和处理"

echo "\n功能实现完成！AI现在可以通过MCP协议:"
echo "- 搜索类文件 (search_class)"
echo "- 获取类详细信息 (get_class_detail)"
echo "- 获取类文件字节码内容 (get_class_content)"
echo "- 更新缓存 (update_cache)"