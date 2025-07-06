package org.maven.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 类搜索服务
 * 提供模糊搜索、忽略大小写的类查找功能
 */
public class ClassSearchService {
    private static final Logger logger = LoggerFactory.getLogger(ClassSearchService.class);
    
    private final MavenRepositoryScanner scanner;
    
    public ClassSearchService() {
        this.scanner = new MavenRepositoryScanner();
    }
    
    /**
     * 搜索类
     * @param query 搜索关键词
     * @param limit 返回结果数量限制
     * @return 匹配的类信息列表，按相关度排序
     */
    public List<ClassInfo> searchClasses(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        logger.debug("搜索类: query='{}', limit={}", query, limit);
        
        try {
            // 首先尝试初始化缓存（如果缓存为空）
            initializeCacheIfEmpty();
            
            List<ClassInfo> results = new ArrayList<>();
            
            // 搜索类名
            results.addAll(searchByClassName(query, limit * 2));
            
            // 搜索包名
            results.addAll(searchByPackageName(query, limit));
            
            // 搜索方法名
            results.addAll(searchByMethodName(query, limit));
            
            // 去重并按相关度排序
            Map<String, ClassInfo> uniqueResults = new LinkedHashMap<>();
            for (ClassInfo classInfo : results) {
                String key = classInfo.getClassName() + "@" + classInfo.getJarPath();
                if (!uniqueResults.containsKey(key)) {
                    uniqueResults.put(key, classInfo);
                }
            }
            
            List<ClassInfo> finalResults = new ArrayList<>(uniqueResults.values());
            
            // 按匹配分数排序
            finalResults.sort((a, b) -> {
                int scoreA = a.calculateMatchScore(query);
                int scoreB = b.calculateMatchScore(query);
                if (scoreA != scoreB) {
                    return Integer.compare(scoreB, scoreA); // 降序
                }
                // 如果分数相同，按类名排序
                return a.getClassName().compareToIgnoreCase(b.getClassName());
            });
            
            // 限制返回结果数量
            if (finalResults.size() > limit) {
                finalResults = finalResults.subList(0, limit);
            }
            
            logger.debug("搜索完成，找到 {} 个结果", finalResults.size());
            return finalResults;
            
        } catch (SQLException e) {
            logger.error("搜索类时出错", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取类的详细信息
     * @param className 完整类名
     * @return 类信息，如果未找到返回null
     */
    public ClassInfo getClassDetail(String className) {
        if (className == null || className.trim().isEmpty()) {
            return null;
        }
        
        logger.debug("获取类详情: {}", className);
        
        try {
            String sql = "SELECT c.class_name, c.package_name, c.jar_path, c.jar_last_modified " +
                    "FROM classes c " +
                    "WHERE c.class_name = ? " +
                    "LIMIT 1";
            
            try (PreparedStatement stmt = scanner.getConnection().prepareStatement(sql)) {
                stmt.setString(1, className);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ClassInfo classInfo = new ClassInfo();
                        classInfo.setClassName(rs.getString("class_name"));
                        classInfo.setPackageName(rs.getString("package_name"));
                        classInfo.setJarPath(rs.getString("jar_path"));
                        classInfo.setLastModified(rs.getLong("jar_last_modified"));
                        
                        // 加载方法和字段信息
                        loadMethodsAndFields(classInfo);
                        
                        return classInfo;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("获取类详情时出错: " + className, e);
        }
        
        return null;
    }
    
    /**
     * 按类名搜索
     */
    private List<ClassInfo> searchByClassName(String query, int limit) throws SQLException {
        String sql = "SELECT c.class_name, c.package_name, c.jar_path, c.jar_last_modified " +
                "FROM classes c " +
                "WHERE c.class_name LIKE ? COLLATE NOCASE " +
                "ORDER BY " +
                "    CASE " +
                "        WHEN c.class_name LIKE ? COLLATE NOCASE THEN 1 " +
                "        WHEN c.class_name LIKE ? COLLATE NOCASE THEN 2 " +
                "        ELSE 3 " +
                "    END, " +
                "    c.class_name COLLATE NOCASE " +
                "LIMIT ?";
        
        List<ClassInfo> results = new ArrayList<>();
        
        try (PreparedStatement stmt = scanner.getConnection().prepareStatement(sql)) {
            String likeQuery = "%" + query + "%";
            String startsWithQuery = query + "%";
            String endsWithQuery = "%" + query;
            
            stmt.setString(1, likeQuery);
            stmt.setString(2, startsWithQuery);
            stmt.setString(3, endsWithQuery);
            stmt.setInt(4, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ClassInfo classInfo = createClassInfoFromResultSet(rs);
                    results.add(classInfo);
                }
            }
        }
        
        return results;
    }
    
    /**
     * 按包名搜索
     */
    private List<ClassInfo> searchByPackageName(String query, int limit) throws SQLException {
        String sql = "SELECT c.class_name, c.package_name, c.jar_path, c.jar_last_modified " +
                "FROM classes c " +
                "WHERE c.package_name LIKE ? COLLATE NOCASE " +
                "ORDER BY c.package_name COLLATE NOCASE, c.class_name COLLATE NOCASE " +
                "LIMIT ?";
        
        List<ClassInfo> results = new ArrayList<>();
        
        try (PreparedStatement stmt = scanner.getConnection().prepareStatement(sql)) {
            stmt.setString(1, "%" + query + "%");
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ClassInfo classInfo = createClassInfoFromResultSet(rs);
                    results.add(classInfo);
                }
            }
        }
        
        return results;
    }
    
    /**
     * 按方法名搜索
     */
    private List<ClassInfo> searchByMethodName(String query, int limit) throws SQLException {
        String sql = "SELECT DISTINCT c.class_name, c.package_name, c.jar_path, c.jar_last_modified " +
                "FROM classes c " +
                "INNER JOIN methods m ON c.id = m.class_id " +
                "WHERE m.method_name LIKE ? COLLATE NOCASE " +
                "   OR m.method_signature LIKE ? COLLATE NOCASE " +
                "ORDER BY c.class_name COLLATE NOCASE " +
                "LIMIT ?";
        
        List<ClassInfo> results = new ArrayList<>();
        
        try (PreparedStatement stmt = scanner.getConnection().prepareStatement(sql)) {
            String likeQuery = "%" + query + "%";
            stmt.setString(1, likeQuery);
            stmt.setString(2, likeQuery);
            stmt.setInt(3, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ClassInfo classInfo = createClassInfoFromResultSet(rs);
                    results.add(classInfo);
                }
            }
        }
        
        return results;
    }
    
    /**
     * 从ResultSet创建ClassInfo对象
     */
    private ClassInfo createClassInfoFromResultSet(ResultSet rs) throws SQLException {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName(rs.getString("class_name"));
        classInfo.setPackageName(rs.getString("package_name"));
        classInfo.setJarPath(rs.getString("jar_path"));
        classInfo.setLastModified(rs.getLong("jar_last_modified"));
        
        // 延迟加载方法和字段信息（仅在需要时加载）
        return classInfo;
    }
    
    /**
     * 获取类文件内容
     * @param className 完整类名
     * @param jarPath JAR文件路径
     * @return 类文件字节码内容，如果未找到则返回null
     */
    public byte[] getClassContent(String className, String jarPath) throws SQLException {
        String sql = "SELECT class_content FROM classes WHERE class_name = ? AND jar_path = ?";
        
        try (PreparedStatement stmt = scanner.getConnection().prepareStatement(sql)) {
            stmt.setString(1, className);
            stmt.setString(2, jarPath);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("class_content");
                }
            }
        }
        
        return null;
    }
    
    /**
     * 获取完整的类信息（包含类文件内容）
     * @param className 完整类名
     * @param jarPath JAR文件路径
     * @return 完整的ClassInfo对象，包含类文件内容
     */
    public ClassInfo getCompleteClassInfo(String className, String jarPath) throws SQLException {
        String sql = "SELECT class_name, package_name, jar_path, jar_last_modified, class_content " +
                "FROM classes WHERE class_name = ? AND jar_path = ?";
        
        try (PreparedStatement stmt = scanner.getConnection().prepareStatement(sql)) {
            stmt.setString(1, className);
            stmt.setString(2, jarPath);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ClassInfo classInfo = new ClassInfo();
                    classInfo.setClassName(rs.getString("class_name"));
                    classInfo.setPackageName(rs.getString("package_name"));
                    classInfo.setJarPath(rs.getString("jar_path"));
                    classInfo.setLastModified(rs.getLong("jar_last_modified"));
                    classInfo.setClassContent(rs.getBytes("class_content"));
                    
                    // 加载方法和字段信息
                    loadMethodsAndFields(classInfo);
                    
                    return classInfo;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 加载类的方法和字段信息
     */
    private void loadMethodsAndFields(ClassInfo classInfo) throws SQLException {
        // 加载方法
        String methodSql = "SELECT m.method_signature " +
                "FROM classes c " +
                "INNER JOIN methods m ON c.id = m.class_id " +
                "WHERE c.class_name = ? AND c.jar_path = ? " +
                "ORDER BY m.method_name";
        
        try (PreparedStatement stmt = scanner.getConnection().prepareStatement(methodSql)) {
            stmt.setString(1, classInfo.getClassName());
            stmt.setString(2, classInfo.getJarPath());
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> methods = new ArrayList<>();
                while (rs.next()) {
                    methods.add(rs.getString("method_signature"));
                }
                classInfo.setMethods(methods);
            }
        }
        
        // 加载字段
        String fieldSql = "SELECT f.field_type, f.field_name " +
                "FROM classes c " +
                "INNER JOIN fields f ON c.id = f.class_id " +
                "WHERE c.class_name = ? AND c.jar_path = ? " +
                "ORDER BY f.field_name";
        
        try (PreparedStatement stmt = scanner.getConnection().prepareStatement(fieldSql)) {
            stmt.setString(1, classInfo.getClassName());
            stmt.setString(2, classInfo.getJarPath());
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> fields = new ArrayList<>();
                while (rs.next()) {
                    String fieldType = rs.getString("field_type");
                    String fieldName = rs.getString("field_name");
                    fields.add(fieldType + " " + fieldName);
                }
                classInfo.setFields(fields);
            }
        }
    }
    
    /**
     * 如果缓存为空，则初始化缓存
     */
    private void initializeCacheIfEmpty() throws SQLException {
        String countSql = "SELECT COUNT(*) as count FROM classes";
        
        try (PreparedStatement stmt = scanner.getConnection().prepareStatement(countSql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next() && rs.getInt("count") == 0) {
                logger.info("缓存为空，开始初始化扫描...");
                try {
                    scanner.scanRepository(false);
                    logger.info("初始化扫描完成");
                } catch (Exception e) {
                    logger.error("初始化扫描失败", e);
                }
            }
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 类的数量
            String classSql = "SELECT COUNT(*) as count FROM classes";
            try (PreparedStatement stmt = scanner.getConnection().prepareStatement(classSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalClasses", rs.getInt("count"));
                }
            }
            
            // JAR文件数量
            String jarSql = "SELECT COUNT(DISTINCT jar_path) as count FROM classes";
            try (PreparedStatement stmt = scanner.getConnection().prepareStatement(jarSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalJars", rs.getInt("count"));
                }
            }
            
            // 方法数量
            String methodSql = "SELECT COUNT(*) as count FROM methods";
            try (PreparedStatement stmt = scanner.getConnection().prepareStatement(methodSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalMethods", rs.getInt("count"));
                }
            }
            
            // 字段数量
            String fieldSql = "SELECT COUNT(*) as count FROM fields";
            try (PreparedStatement stmt = scanner.getConnection().prepareStatement(fieldSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalFields", rs.getInt("count"));
                }
            }
            
        } catch (SQLException e) {
            logger.error("获取缓存统计信息失败", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * 清空缓存
     */
    public void clearCache() throws SQLException {
        String[] tables = {"fields", "methods", "classes"};
        
        for (String table : tables) {
            String sql = "DELETE FROM " + table;
            try (PreparedStatement stmt = scanner.getConnection().prepareStatement(sql)) {
                stmt.executeUpdate();
            }
        }
        
        logger.info("缓存已清空");
    }
}