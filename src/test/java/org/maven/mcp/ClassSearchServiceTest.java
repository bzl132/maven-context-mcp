package org.maven.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClassSearchService的单元测试
 */
class ClassSearchServiceTest {
    
    private ClassSearchService searchService;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        searchService = new ClassSearchService();
    }
    
    @Test
    void testSearchClasses_EmptyQuery() {
        List<ClassInfo> results = searchService.searchClasses("", 10);
        assertTrue(results.isEmpty(), "空查询应该返回空结果");
        
        results = searchService.searchClasses(null, 10);
        assertTrue(results.isEmpty(), "null查询应该返回空结果");
    }
    
    @Test
    void testSearchClasses_WithLimit() {
        List<ClassInfo> results = searchService.searchClasses("test", 5);
        assertTrue(results.size() <= 5, "结果数量不应超过限制");
    }
    
    @Test
    void testGetClassDetail_NullClassName() {
        ClassInfo result = searchService.getClassDetail(null);
        assertNull(result, "null类名应该返回null");
        
        result = searchService.getClassDetail("");
        assertNull(result, "空类名应该返回null");
    }
    
    @Test
    void testGetCacheStats() {
        Map<String, Object> stats = searchService.getCacheStats();
        assertNotNull(stats, "缓存统计信息不应为null");
        
        // 检查基本的统计字段
        assertTrue(stats.containsKey("totalClasses") || stats.containsKey("error"), 
                  "应该包含totalClasses字段或error字段");
    }
    
    @Test
    void testClassInfo_BasicFunctionality() {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName("com.example.TestClass");
        classInfo.setPackageName("com.example");
        classInfo.setJarPath("/path/to/test.jar");
        
        assertEquals("com.example.TestClass", classInfo.getClassName());
        assertEquals("com.example", classInfo.getPackageName());
        assertEquals("TestClass", classInfo.getSimpleClassName());
        assertEquals("test.jar", classInfo.getJarFileName());
    }
    
    @Test
    void testClassInfo_MatchScore() {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName("com.example.ArrayList");
        classInfo.setPackageName("com.example");
        
        // 完全匹配简单类名应该得分最高
        int exactScore = classInfo.calculateMatchScore("ArrayList");
        int partialScore = classInfo.calculateMatchScore("Array");
        int packageScore = classInfo.calculateMatchScore("example");
        
        assertTrue(exactScore > partialScore, "完全匹配应该比部分匹配得分高");
        assertTrue(partialScore > packageScore, "类名匹配应该比包名匹配得分高");
    }
    
    @Test
    void testClassInfo_CaseInsensitiveMatching() {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName("com.example.ArrayList");
        classInfo.setPackageName("com.example");
        
        assertTrue(classInfo.matchesClassName("arraylist"), "应该忽略大小写匹配类名");
        assertTrue(classInfo.matchesClassName("ARRAYLIST"), "应该忽略大小写匹配类名");
        assertTrue(classInfo.matchesPackageName("EXAMPLE"), "应该忽略大小写匹配包名");
    }
    
    @Test
    void testClassInfo_MethodAndFieldOperations() {
        ClassInfo classInfo = new ClassInfo();
        
        classInfo.addMethod("public void testMethod()");
        classInfo.addMethod("private int getValue()");
        classInfo.addField("String name");
        classInfo.addField("int value");
        
        assertEquals(2, classInfo.getMethods().size());
        assertEquals(2, classInfo.getFields().size());
        
        assertTrue(classInfo.containsMethod("testMethod"));
        assertTrue(classInfo.containsMethod("getValue"));
        assertTrue(classInfo.containsField("name"));
        assertTrue(classInfo.containsField("value"));
        
        // 测试大小写不敏感
        assertTrue(classInfo.containsMethod("TESTMETHOD"));
        assertTrue(classInfo.containsField("NAME"));
    }
    
    @Test
    void testClassInfo_EdgeCases() {
        ClassInfo classInfo = new ClassInfo();
        
        // 测试空值处理
        classInfo.addMethod(null);
        classInfo.addMethod("");
        classInfo.addMethod("   ");
        classInfo.addField(null);
        classInfo.addField("");
        
        assertTrue(classInfo.getMethods().isEmpty(), "空方法不应被添加");
        assertTrue(classInfo.getFields().isEmpty(), "空字段不应被添加");
        
        // 测试无包名的类
        classInfo.setClassName("SimpleClass");
        assertEquals("SimpleClass", classInfo.getSimpleClassName());
        
        // 测试无路径的JAR
        classInfo.setJarPath("simple.jar");
        assertEquals("simple.jar", classInfo.getJarFileName());
    }
}