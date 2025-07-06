package org.maven.mcp;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maven本地仓库扫描器
 * 负责扫描~/.m2/repository目录下的JAR文件，解析类信息并存储到SQLite缓存中
 */
public class MavenRepositoryScanner {
    private static final Logger logger = LoggerFactory.getLogger(MavenRepositoryScanner.class);
    
    private final String mavenRepoPath;
    private final String cacheDbPath;
    private Connection connection;
    
    public MavenRepositoryScanner() {
        ConfigManager config = ConfigManager.getInstance();
        try {
            config.validateConfiguration();
        } catch (ConfigurationException e) {
            logger.error("配置验证失败: {}", e.getMessage());
            throw new RuntimeException("配置验证失败", e);
        }
        
        this.mavenRepoPath = config.getMavenRepoPath();
        this.cacheDbPath = config.getCacheDbPath();
        logger.info("初始化Maven仓库扫描器 - 仓库路径: {}, 缓存路径: {}", mavenRepoPath, cacheDbPath);
        initializeDatabase();
    }
    
    /**
     * 初始化SQLite数据库
     */
    private void initializeDatabase() {
        try {
            // 确保缓存目录存在
            File cacheDir = new File(cacheDbPath).getParentFile();
            if (cacheDir != null && !cacheDir.exists()) {
                boolean created = cacheDir.mkdirs();
                if (!created && !cacheDir.exists()) {
                    throw new RuntimeException("无法创建缓存目录: " + cacheDir.getAbsolutePath());
                }
                logger.info("创建缓存目录: {}", cacheDir.getAbsolutePath());
            }
            
            // 连接到SQLite数据库
            String url = "jdbc:sqlite:" + cacheDbPath;
            connection = DriverManager.getConnection(url);
            
            // 设置数据库连接属性
            connection.setAutoCommit(false);
            
            // 创建表
            createTables();
            
            logger.info("数据库初始化完成: {}", cacheDbPath);
        } catch (SQLException e) {
            logger.error("数据库初始化失败: {}", e.getMessage(), e);
            closeConnection();
            throw new RuntimeException("数据库初始化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建数据库表结构
     */
    private void createTables() throws SQLException {
        String createClassesTable = "CREATE TABLE IF NOT EXISTS classes (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    class_name TEXT NOT NULL," +
                "    package_name TEXT," +
                "    jar_path TEXT NOT NULL," +
                "    jar_last_modified INTEGER," +
                "    class_content BLOB," +
                "    created_at INTEGER DEFAULT (strftime('%s', 'now'))," +
                "    UNIQUE(class_name, jar_path)" +
                ")";
        
        String createMethodsTable = "CREATE TABLE IF NOT EXISTS methods (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    class_id INTEGER," +
                "    method_name TEXT NOT NULL," +
                "    method_signature TEXT NOT NULL," +
                "    FOREIGN KEY(class_id) REFERENCES classes(id) ON DELETE CASCADE" +
                ")";
        
        String createFieldsTable = "CREATE TABLE IF NOT EXISTS fields (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    class_id INTEGER," +
                "    field_name TEXT NOT NULL," +
                "    field_type TEXT," +
                "    FOREIGN KEY(class_id) REFERENCES classes(id) ON DELETE CASCADE" +
                ")";
        
        // 创建索引
        String createClassNameIndex = "CREATE INDEX IF NOT EXISTS idx_class_name ON classes(class_name)";
        String createPackageNameIndex = "CREATE INDEX IF NOT EXISTS idx_package_name ON classes(package_name)";
        String createMethodNameIndex = "CREATE INDEX IF NOT EXISTS idx_method_name ON methods(method_name)";
        String createFieldNameIndex = "CREATE INDEX IF NOT EXISTS idx_field_name ON fields(field_name)";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createClassesTable);
            stmt.execute(createMethodsTable);
            stmt.execute(createFieldsTable);
            stmt.execute(createClassNameIndex);
            stmt.execute(createPackageNameIndex);
            stmt.execute(createMethodNameIndex);
            stmt.execute(createFieldNameIndex);
            connection.commit();
            logger.debug("数据库表结构创建完成");
        }
    }
    
    /**
     * 关闭数据库连接
     */
    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                logger.debug("数据库连接已关闭");
            } catch (SQLException e) {
                logger.warn("关闭数据库连接时发生错误: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 扫描Maven仓库
     * @param force 是否强制重新扫描所有JAR文件
     * @return 扫描的JAR文件数量
     */
    public int scanRepository(boolean force) throws SQLException, IOException {
        logger.info("开始扫描Maven仓库: {}", mavenRepoPath);
        
        Path repoPath = Paths.get(mavenRepoPath);
        if (!Files.exists(repoPath)) {
            logger.warn("Maven仓库目录不存在: {}", mavenRepoPath);
            return 0;
        }
        
        Set<String> processedJars = new HashSet<>();
        int scannedCount = 0;
        
        try (Stream<Path> paths = Files.walk(repoPath)) {
            List<Path> jarFiles = paths
                .filter(path -> path.toString().endsWith(".jar"))
                .filter(path -> !path.toString().contains("-sources.jar"))
                .filter(path -> !path.toString().contains("-javadoc.jar"))
                .collect(Collectors.toList());
            
            logger.info("找到 {} 个JAR文件", jarFiles.size());
            
            for (Path jarPath : jarFiles) {
                try {
                    if (shouldScanJar(jarPath, force)) {
                        scanJarFile(jarPath);
                        scannedCount++;
                        
                        if (scannedCount % 100 == 0) {
                            logger.info("已扫描 {} 个JAR文件...", scannedCount);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("扫描JAR文件失败: {}", jarPath, e);
                }
            }
        }
        
        logger.info("扫描完成，共处理 {} 个JAR文件", scannedCount);
        return scannedCount;
    }
    
    /**
     * 判断是否需要扫描JAR文件
     */
    private boolean shouldScanJar(Path jarPath, boolean force) throws SQLException {
        if (force) {
            return true;
        }
        
        try {
            long lastModified = Files.getLastModifiedTime(jarPath).toMillis();
            
            String sql = "SELECT jar_last_modified FROM classes WHERE jar_path = ? LIMIT 1";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, jarPath.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        long cachedModified = rs.getLong("jar_last_modified");
                        return lastModified > cachedModified;
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("无法获取文件修改时间: {}", jarPath, e);
        }
        
        return true; // 如果缓存中没有记录，则需要扫描
    }
    
    /**
     * 扫描单个JAR文件
     */
    private void scanJarFile(Path jarPath) throws IOException, SQLException {
        logger.debug("扫描JAR文件: {}", jarPath);
        
        // 先删除该JAR的旧记录
        deleteJarRecords(jarPath.toString());
        
        long lastModified = Files.getLastModifiedTime(jarPath).toMillis();
        
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                
                if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
                    try (InputStream inputStream = jarFile.getInputStream(entry)) {
                        ClassInfo classInfo = parseClassFile(inputStream, jarPath.toString(), lastModified);
                        if (classInfo != null) {
                            saveClassInfo(classInfo);
                        }
                    } catch (Exception e) {
                        logger.debug("解析类文件失败: {}", entry.getName(), e);
                    }
                }
            }
        }
    }
    
    /**
     * 删除JAR文件的旧记录
     */
    private void deleteJarRecords(String jarPath) throws SQLException {
        String sql = "DELETE FROM classes WHERE jar_path = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, jarPath);
            stmt.executeUpdate();
        }
    }
    
    /**
     * 读取InputStream的所有字节 (Java 8兼容)
     */
    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
    
    /**
     * 解析类文件
     */
    private ClassInfo parseClassFile(InputStream inputStream, String jarPath, long lastModified) throws IOException {
        byte[] classBytes = readAllBytes(inputStream);
        ClassReader classReader = new ClassReader(classBytes);
        
        ClassInfoVisitor visitor = new ClassInfoVisitor();
        classReader.accept(visitor, ClassReader.SKIP_DEBUG);
        
        if (visitor.className != null) {
            ClassInfo classInfo = new ClassInfo();
            classInfo.setClassName(visitor.className.replace('/', '.'));
            classInfo.setPackageName(visitor.packageName);
            classInfo.setJarPath(jarPath);
            classInfo.setLastModified(lastModified);
            classInfo.setMethods(visitor.methods);
            classInfo.setFields(visitor.fields);
            classInfo.setClassContent(classBytes); // 保存类文件字节码内容
            
            return classInfo;
        }
        
        return null;
    }
    
    /**
     * 保存类信息到数据库
     */
    private void saveClassInfo(ClassInfo classInfo) throws SQLException {
        String insertClassSql = "INSERT OR REPLACE INTO classes (class_name, package_name, jar_path, jar_last_modified, class_content) " +
                "VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(insertClassSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, classInfo.getClassName());
            stmt.setString(2, classInfo.getPackageName());
            stmt.setString(3, classInfo.getJarPath());
            stmt.setLong(4, classInfo.getLastModified());
            stmt.setBytes(5, classInfo.getClassContent());
            
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long classId = rs.getLong(1);
                    
                    // 保存方法信息
                    saveMethodsInfo(classId, classInfo.getMethods());
                    
                    // 保存字段信息
                    saveFieldsInfo(classId, classInfo.getFields());
                }
            }
        }
    }
    
    /**
     * 保存方法信息
     */
    private void saveMethodsInfo(long classId, List<String> methods) throws SQLException {
        if (methods.isEmpty()) {
            return;
        }
        
        String sql = "INSERT INTO methods (class_id, method_name, method_signature) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (String method : methods) {
                String methodName = extractMethodName(method);
                stmt.setLong(1, classId);
                stmt.setString(2, methodName);
                stmt.setString(3, method);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
    
    /**
     * 保存字段信息
     */
    private void saveFieldsInfo(long classId, List<String> fields) throws SQLException {
        if (fields.isEmpty()) {
            return;
        }
        
        String sql = "INSERT INTO fields (class_id, field_name, field_type) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (String field : fields) {
                String[] parts = field.split(" ", 2);
                String fieldType = parts.length > 1 ? parts[0] : "";
                String fieldName = parts.length > 1 ? parts[1] : field;
                
                stmt.setLong(1, classId);
                stmt.setString(2, fieldName);
                stmt.setString(3, fieldType);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
    
    /**
     * 从方法签名中提取方法名
     */
    private String extractMethodName(String methodSignature) {
        int parenIndex = methodSignature.indexOf('(');
        if (parenIndex > 0) {
            String beforeParen = methodSignature.substring(0, parenIndex);
            int spaceIndex = beforeParen.lastIndexOf(' ');
            return spaceIndex >= 0 ? beforeParen.substring(spaceIndex + 1) : beforeParen;
        }
        return methodSignature;
    }
    
    /**
     * 获取数据库连接
     */
    public Connection getConnection() {
        return connection;
    }
    
    /**
     * ASM类访问器，用于解析类信息
     */
    private static class ClassInfoVisitor extends ClassVisitor {
        String className;
        String packageName;
        List<String> methods = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        
        public ClassInfoVisitor() {
            super(Opcodes.ASM9);
        }
        
        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            if (name.contains("/")) {
                this.packageName = name.substring(0, name.lastIndexOf('/')).replace('/', '.');
            } else {
                this.packageName = "";
            }
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (!name.equals("<init>") && !name.equals("<clinit>")) {
                String methodSignature = formatMethodSignature(access, name, descriptor);
                methods.add(methodSignature);
            }
            return null;
        }
        
        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            String fieldType = formatType(descriptor);
            fields.add(fieldType + " " + name);
            return null;
        }
        
        private String formatMethodSignature(int access, String name, String descriptor) {
            StringBuilder sb = new StringBuilder();
            
            // 访问修饰符
            if ((access & Opcodes.ACC_PUBLIC) != 0) sb.append("public ");
            else if ((access & Opcodes.ACC_PROTECTED) != 0) sb.append("protected ");
            else if ((access & Opcodes.ACC_PRIVATE) != 0) sb.append("private ");
            
            if ((access & Opcodes.ACC_STATIC) != 0) sb.append("static ");
            if ((access & Opcodes.ACC_FINAL) != 0) sb.append("final ");
            if ((access & Opcodes.ACC_ABSTRACT) != 0) sb.append("abstract ");
            
            // 返回类型和方法名
            String returnType = getReturnType(descriptor);
            sb.append(returnType).append(" ").append(name);
            
            // 参数
            String params = getParameters(descriptor);
            sb.append("(").append(params).append(")");
            
            return sb.toString();
        }
        
        private String getReturnType(String descriptor) {
            int parenIndex = descriptor.lastIndexOf(')');
            if (parenIndex >= 0 && parenIndex < descriptor.length() - 1) {
                return formatType(descriptor.substring(parenIndex + 1));
            }
            return "void";
        }
        
        private String getParameters(String descriptor) {
            int startIndex = descriptor.indexOf('(');
            int endIndex = descriptor.lastIndexOf(')');
            if (startIndex >= 0 && endIndex > startIndex) {
                String paramDesc = descriptor.substring(startIndex + 1, endIndex);
                return parseParameters(paramDesc);
            }
            return "";
        }
        
        private String parseParameters(String paramDesc) {
            List<String> params = new ArrayList<>();
            int i = 0;
            while (i < paramDesc.length()) {
                int start = i;
                char c = paramDesc.charAt(i);
                
                if (c == '[') {
                    // 数组类型
                    while (i < paramDesc.length() && paramDesc.charAt(i) == '[') {
                        i++;
                    }
                    if (i < paramDesc.length()) {
                        i++; // 跳过数组元素类型
                        if (paramDesc.charAt(i - 1) == 'L') {
                            // 对象数组
                            while (i < paramDesc.length() && paramDesc.charAt(i) != ';') {
                                i++;
                            }
                            i++; // 跳过 ';'
                        }
                    }
                } else if (c == 'L') {
                    // 对象类型
                    i++;
                    while (i < paramDesc.length() && paramDesc.charAt(i) != ';') {
                        i++;
                    }
                    i++; // 跳过 ';'
                } else {
                    // 基本类型
                    i++;
                }
                
                String paramType = formatType(paramDesc.substring(start, i));
                params.add(paramType);
            }
            
            return String.join(", ", params);
        }
        
        private String formatType(String descriptor) {
            if (descriptor.startsWith("[")) {
                // 数组类型
                int dimensions = 0;
                int i = 0;
                while (i < descriptor.length() && descriptor.charAt(i) == '[') {
                    dimensions++;
                    i++;
                }
                String elementType = formatType(descriptor.substring(i));
                StringBuilder sb = new StringBuilder(elementType);
                for (int j = 0; j < dimensions; j++) {
                    sb.append("[]");
                }
                return sb.toString();
            }
            
            switch (descriptor) {
                case "V": return "void";
                case "Z": return "boolean";
                case "B": return "byte";
                case "C": return "char";
                case "S": return "short";
                case "I": return "int";
                case "J": return "long";
                case "F": return "float";
                case "D": return "double";
                default:
                    if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
                    }
                    return descriptor;
            }
        }
    }
}