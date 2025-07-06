package org.maven.mcp;

import java.util.ArrayList;
import java.util.List;

/**
 * 类信息数据模型
 * 存储从JAR文件中解析出的类的详细信息
 */
public class ClassInfo {
    private String className;        // 完整类名（包含包名）
    private String packageName;      // 包名
    private String jarPath;          // JAR文件路径
    private long lastModified;       // JAR文件最后修改时间
    private List<String> methods;    // 方法列表
    private List<String> fields;     // 字段列表
    private byte[] classContent;     // 类文件字节码内容
    
    public ClassInfo() {
        this.methods = new ArrayList<>();
        this.fields = new ArrayList<>();
    }
    
    public ClassInfo(String className, String packageName, String jarPath) {
        this();
        this.className = className;
        this.packageName = packageName;
        this.jarPath = jarPath;
    }
    
    // Getters and Setters
    
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public String getPackageName() {
        return packageName;
    }
    
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
    
    public String getJarPath() {
        return jarPath;
    }
    
    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
    
    public List<String> getMethods() {
        return methods;
    }
    
    public void setMethods(List<String> methods) {
        this.methods = methods != null ? methods : new ArrayList<>();
    }
    
    public List<String> getFields() {
        return fields;
    }
    
    public void setFields(List<String> fields) {
        this.fields = fields != null ? fields : new ArrayList<>();
    }
    
    public byte[] getClassContent() {
        return classContent;
    }
    
    public void setClassContent(byte[] classContent) {
        this.classContent = classContent;
    }
    
    /**
     * 获取简单类名（不包含包名）
     */
    public String getSimpleClassName() {
        if (className == null) {
            return null;
        }
        int lastDotIndex = className.lastIndexOf('.');
        return lastDotIndex >= 0 ? className.substring(lastDotIndex + 1) : className;
    }
    
    /**
     * 获取JAR文件名
     */
    public String getJarFileName() {
        if (jarPath == null) {
            return null;
        }
        int lastSlashIndex = Math.max(jarPath.lastIndexOf('/'), jarPath.lastIndexOf('\\'));
        return lastSlashIndex >= 0 ? jarPath.substring(lastSlashIndex + 1) : jarPath;
    }
    
    /**
     * 添加方法
     */
    public void addMethod(String method) {
        if (method != null && !method.trim().isEmpty()) {
            this.methods.add(method);
        }
    }
    
    /**
     * 添加字段
     */
    public void addField(String field) {
        if (field != null && !field.trim().isEmpty()) {
            this.fields.add(field);
        }
    }
    
    /**
     * 检查是否包含指定的方法名
     */
    public boolean containsMethod(String methodName) {
        if (methodName == null || methods.isEmpty()) {
            return false;
        }
        
        String lowerMethodName = methodName.toLowerCase();
        return methods.stream()
                .anyMatch(method -> method.toLowerCase().contains(lowerMethodName));
    }
    
    /**
     * 检查是否包含指定的字段名
     */
    public boolean containsField(String fieldName) {
        if (fieldName == null || fields.isEmpty()) {
            return false;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        return fields.stream()
                .anyMatch(field -> field.toLowerCase().contains(lowerFieldName));
    }
    
    /**
     * 检查类名是否匹配查询字符串（忽略大小写）
     */
    public boolean matchesClassName(String query) {
        if (query == null || className == null) {
            return false;
        }
        
        String lowerQuery = query.toLowerCase();
        String lowerClassName = className.toLowerCase();
        String lowerSimpleClassName = getSimpleClassName().toLowerCase();
        
        return lowerClassName.contains(lowerQuery) || lowerSimpleClassName.contains(lowerQuery);
    }
    
    /**
     * 检查包名是否匹配查询字符串（忽略大小写）
     */
    public boolean matchesPackageName(String query) {
        if (query == null || packageName == null) {
            return false;
        }
        
        return packageName.toLowerCase().contains(query.toLowerCase());
    }
    
    /**
     * 计算与查询字符串的匹配分数（用于排序）
     * 分数越高表示匹配度越好
     */
    public int calculateMatchScore(String query) {
        if (query == null) {
            return 0;
        }
        
        String lowerQuery = query.toLowerCase();
        int score = 0;
        
        // 完全匹配简单类名得分最高
        if (getSimpleClassName().toLowerCase().equals(lowerQuery)) {
            score += 1000;
        }
        // 简单类名开头匹配
        else if (getSimpleClassName().toLowerCase().startsWith(lowerQuery)) {
            score += 500;
        }
        // 简单类名包含
        else if (getSimpleClassName().toLowerCase().contains(lowerQuery)) {
            score += 200;
        }
        
        // 完整类名匹配
        if (className != null && className.toLowerCase().contains(lowerQuery)) {
            score += 100;
        }
        
        // 包名匹配
        if (packageName != null && packageName.toLowerCase().contains(lowerQuery)) {
            score += 50;
        }
        
        // 方法名匹配
        if (containsMethod(query)) {
            score += 30;
        }
        
        // 字段名匹配
        if (containsField(query)) {
            score += 20;
        }
        
        return score;
    }
    
    @Override
    public String toString() {
        return String.format("ClassInfo{className='%s', packageName='%s', jarPath='%s', methods=%d, fields=%d}",
                className, packageName, getJarFileName(), methods.size(), fields.size());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ClassInfo classInfo = (ClassInfo) obj;
        return className != null ? className.equals(classInfo.className) : classInfo.className == null;
    }
    
    @Override
    public int hashCode() {
        return className != null ? className.hashCode() : 0;
    }
}