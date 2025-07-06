package org.maven.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * MCP服务器主类，处理与AI的通信协议
 */
public class McpServer {
    private static final Logger logger = LoggerFactory.getLogger(McpServer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MavenRepositoryScanner scanner;
    private final ClassSearchService searchService;
    
    public McpServer() {
        this.scanner = new MavenRepositoryScanner();
        this.searchService = new ClassSearchService();
    }
    
    public static void main(String[] args) {
        McpServer server = new McpServer();
        server.start();
    }
    
    public void start() {
        logger.info("启动Maven Context MCP服务器...");
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
             PrintWriter writer = new PrintWriter(System.out, true)) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    // 解析从标准输入读取的JSON-RPC请求
                    // 每行包含一个完整的JSON-RPC请求消息
                    JsonNode request = objectMapper.readTree(line);
                    JsonNode response = handleRequest(request);
                    writer.println(objectMapper.writeValueAsString(response));
                } catch (Exception e) {
                    logger.error("处理请求时出错", e);
                    JsonNode errorResponse = createErrorResponse("internal_error", e.getMessage());
                    writer.println(objectMapper.writeValueAsString(errorResponse));
                }
            }
        } catch (IOException e) {
            logger.error("IO错误", e);
        }
    }
    
    private JsonNode handleRequest(JsonNode request) {
        String method = request.path("method").asText();
        JsonNode params = request.path("params");
        JsonNode id = request.path("id");
        
        logger.debug("处理请求: {}", method);
        
        switch (method) {
            case "initialize":
                return handleInitialize(id);
            case "tools/list":
                return handleToolsList(id);
            case "tools/call":
                return handleToolsCall(params, id);
            default:
                return createErrorResponse("method_not_found", "未知方法: " + method, id);
        }
    }
    
    private JsonNode handleInitialize(JsonNode id) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        
        ObjectNode capabilities = objectMapper.createObjectNode();
        ObjectNode tools = objectMapper.createObjectNode();
        tools.put("listChanged", false);
        capabilities.set("tools", tools);
        result.set("capabilities", capabilities);
        
        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "maven-context-mcp");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);
        
        response.set("result", result);
        return response;
    }
    
    private JsonNode handleToolsList(JsonNode id) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        
        ArrayNode tools = objectMapper.createArrayNode();
        
        // 搜索类工具
        ObjectNode searchClassTool = objectMapper.createObjectNode();
        searchClassTool.put("name", "search_class");
        searchClassTool.put("description", "在Maven本地仓库中搜索类，支持模糊搜索和忽略大小写");
        
        ObjectNode searchClassSchema = objectMapper.createObjectNode();
        searchClassSchema.put("type", "object");
        ObjectNode searchClassProps = objectMapper.createObjectNode();
        
        ObjectNode queryProp = objectMapper.createObjectNode();
        queryProp.put("type", "string");
        queryProp.put("description", "搜索关键词，可以是类名、包名或方法名");
        searchClassProps.set("query", queryProp);
        
        ObjectNode limitProp = objectMapper.createObjectNode();
        limitProp.put("type", "integer");
        limitProp.put("description", "返回结果数量限制，默认50");
        limitProp.put("default", 50);
        searchClassProps.set("limit", limitProp);
        
        searchClassSchema.set("properties", searchClassProps);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("query");
        searchClassSchema.set("required", required);
        searchClassTool.set("inputSchema", searchClassSchema);
        tools.add(searchClassTool);
        
        // 获取类详情工具
        ObjectNode getClassDetailTool = objectMapper.createObjectNode();
        getClassDetailTool.put("name", "get_class_detail");
        getClassDetailTool.put("description", "获取指定类的详细信息，包括所有方法和字段");
        
        ObjectNode detailSchema = objectMapper.createObjectNode();
        detailSchema.put("type", "object");
        ObjectNode detailProps = objectMapper.createObjectNode();
        
        ObjectNode classNameProp = objectMapper.createObjectNode();
        classNameProp.put("type", "string");
        classNameProp.put("description", "完整的类名（包含包名）");
        detailProps.set("className", classNameProp);
        
        detailSchema.set("properties", detailProps);
        ArrayNode detailRequired = objectMapper.createArrayNode();
        detailRequired.add("className");
        detailSchema.set("required", detailRequired);
        getClassDetailTool.set("inputSchema", detailSchema);
        tools.add(getClassDetailTool);
        
        // 更新缓存工具
        ObjectNode updateCacheTool = objectMapper.createObjectNode();
        updateCacheTool.put("name", "update_cache");
        updateCacheTool.put("description", "手动更新Maven仓库缓存，扫描新的JAR文件");
        
        ObjectNode updateSchema = objectMapper.createObjectNode();
        updateSchema.put("type", "object");
        ObjectNode updateProps = objectMapper.createObjectNode();
        
        ObjectNode forceProp = objectMapper.createObjectNode();
        forceProp.put("type", "boolean");
        forceProp.put("description", "是否强制重新扫描所有JAR文件，默认false");
        forceProp.put("default", false);
        updateProps.set("force", forceProp);
        
        updateSchema.set("properties", updateProps);
        updateCacheTool.set("inputSchema", updateSchema);
        tools.add(updateCacheTool);
        
        // 获取类文件内容工具
        ObjectNode getClassContentTool = objectMapper.createObjectNode();
        getClassContentTool.put("name", "get_class_content");
        getClassContentTool.put("description", "获取指定类的字节码文件内容，返回Base64编码的类文件数据");
        
        ObjectNode contentSchema = objectMapper.createObjectNode();
        contentSchema.put("type", "object");
        ObjectNode contentProps = objectMapper.createObjectNode();
        
        ObjectNode classNameContentProp = objectMapper.createObjectNode();
        classNameContentProp.put("type", "string");
        classNameContentProp.put("description", "完整的类名（包含包名）");
        contentProps.set("className", classNameContentProp);
        
        ObjectNode jarPathProp = objectMapper.createObjectNode();
        jarPathProp.put("type", "string");
        jarPathProp.put("description", "JAR文件路径（可选，如果不提供则使用搜索到的第一个匹配项）");
        contentProps.set("jarPath", jarPathProp);
        
        contentSchema.set("properties", contentProps);
        ArrayNode contentRequired = objectMapper.createArrayNode();
        contentRequired.add("className");
        contentSchema.set("required", contentRequired);
        getClassContentTool.set("inputSchema", contentSchema);
        tools.add(getClassContentTool);
        
        ObjectNode result = objectMapper.createObjectNode();
        result.set("tools", tools);
        response.set("result", result);
        
        return response;
    }
    
    private JsonNode handleToolsCall(JsonNode params, JsonNode id) {
        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");
        
        try {
            switch (toolName) {
                case "search_class":
                    return handleSearchClass(arguments, id);
                case "get_class_detail":
                    return handleGetClassDetail(arguments, id);
                case "get_class_content":
                    return handleGetClassContent(arguments, id);
                case "update_cache":
                    return handleUpdateCache(arguments, id);
                default:
                    return createErrorResponse("invalid_params", "未知工具: " + toolName, id);
            }
        } catch (Exception e) {
            logger.error("执行工具时出错: " + toolName, e);
            return createErrorResponse("internal_error", "执行工具时出错: " + e.getMessage(), id);
        }
    }
    
    private JsonNode handleSearchClass(JsonNode arguments, JsonNode id) {
        String query = arguments.path("query").asText();
        int limit = arguments.path("limit").asInt(50);
        
        List<ClassInfo> results = searchService.searchClasses(query, limit);
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        
        StringBuilder text = new StringBuilder();
        text.append(String.format("找到 %d 个匹配的类:\n\n", results.size()));
        
        for (ClassInfo classInfo : results) {
            text.append(String.format("**%s**\n", classInfo.getClassName()));
            text.append(String.format("- JAR文件: %s\n", classInfo.getJarPath()));
            text.append(String.format("- 包名: %s\n", classInfo.getPackageName()));
            if (!classInfo.getMethods().isEmpty()) {
                text.append("- 主要方法: ");
                text.append(String.join(", ", classInfo.getMethods().subList(0, Math.min(3, classInfo.getMethods().size()))));
                if (classInfo.getMethods().size() > 3) {
                    text.append("...");
                }
                text.append("\n");
            }
            text.append("\n");
        }
        
        textContent.put("text", text.toString());
        content.add(textContent);
        
        ObjectNode result = objectMapper.createObjectNode();
        result.set("content", content);
        response.set("result", result);
        
        return response;
    }
    
    private JsonNode handleGetClassDetail(JsonNode arguments, JsonNode id) {
        String className = arguments.path("className").asText();
        
        ClassInfo classInfo = searchService.getClassDetail(className);
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        
        if (classInfo != null) {
            StringBuilder text = new StringBuilder();
            text.append(String.format("# %s\n\n", classInfo.getClassName()));
            text.append(String.format("**JAR文件**: %s\n\n", classInfo.getJarPath()));
            text.append(String.format("**包名**: %s\n\n", classInfo.getPackageName()));
            
            if (!classInfo.getMethods().isEmpty()) {
                text.append("## 方法列表\n\n");
                for (String method : classInfo.getMethods()) {
                    text.append(String.format("- `%s`\n", method));
                }
                text.append("\n");
            }
            
            if (!classInfo.getFields().isEmpty()) {
                text.append("## 字段列表\n\n");
                for (String field : classInfo.getFields()) {
                    text.append(String.format("- `%s`\n", field));
                }
            }
            
            textContent.put("text", text.toString());
        } else {
            textContent.put("text", "未找到指定的类: " + className);
        }
        
        content.add(textContent);
        
        ObjectNode result = objectMapper.createObjectNode();
        result.set("content", content);
        response.set("result", result);
        
        return response;
    }
    
    private JsonNode handleGetClassContent(JsonNode arguments, JsonNode id) {
        String className = arguments.path("className").asText();
        String jarPath = arguments.path("jarPath").asText("");
        
        try {
            byte[] classContent = null;
            ClassInfo classInfo = null;
            
            if (!jarPath.isEmpty()) {
                // 如果提供了JAR路径，直接获取
                classContent = searchService.getClassContent(className, jarPath);
                classInfo = searchService.getCompleteClassInfo(className, jarPath);
            } else {
                // 如果没有提供JAR路径，先搜索类，然后获取第一个匹配项的内容
                List<ClassInfo> searchResults = searchService.searchClasses(className, 1);
                if (!searchResults.isEmpty()) {
                    ClassInfo firstResult = searchResults.get(0);
                    classContent = searchService.getClassContent(firstResult.getClassName(), firstResult.getJarPath());
                    classInfo = searchService.getCompleteClassInfo(firstResult.getClassName(), firstResult.getJarPath());
                }
            }
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            
            ArrayNode content = objectMapper.createArrayNode();
            ObjectNode textContent = objectMapper.createObjectNode();
            textContent.put("type", "text");
            
            if (classContent != null && classInfo != null) {
                // 将字节码内容编码为Base64
                String base64Content = Base64.getEncoder().encodeToString(classContent);
                
                StringBuilder text = new StringBuilder();
                text.append(String.format("# 类文件内容: %s\n\n", className));
                text.append(String.format("**JAR文件**: %s\n\n", classInfo.getJarPath()));
                text.append(String.format("**包名**: %s\n\n", classInfo.getPackageName()));
                text.append(String.format("**文件大小**: %d 字节\n\n", classContent.length));
                text.append("**Base64编码的类文件内容**:\n\n");
                text.append("```\n");
                text.append(base64Content);
                text.append("\n```\n\n");
                text.append("*注意: 这是编译后的Java字节码文件，可以使用反编译工具查看源代码*");
                
                textContent.put("text", text.toString());
            } else {
                textContent.put("text", "未找到指定的类文件: " + className);
            }
            
            content.add(textContent);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.set("content", content);
            response.set("result", result);
            
            return response;
        } catch (Exception e) {
            logger.error("获取类文件内容时出错: " + className, e);
            return createErrorResponse("internal_error", "获取类文件内容时出错: " + e.getMessage(), id);
        }
    }
    
    private JsonNode handleUpdateCache(JsonNode arguments, JsonNode id) {
        boolean force = arguments.path("force").asBoolean(false);
        
        try {
            int scannedJars = scanner.scanRepository(force);
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            
            ArrayNode content = objectMapper.createArrayNode();
            ObjectNode textContent = objectMapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", String.format("缓存更新完成！扫描了 %d 个JAR文件。", scannedJars));
            content.add(textContent);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.set("content", content);
            response.set("result", result);
            
            return response;
        } catch (Exception e) {
            return createErrorResponse("internal_error", "更新缓存时出错: " + e.getMessage(), id);
        }
    }
    
    private JsonNode createErrorResponse(String code, String message) {
        return createErrorResponse(code, message, null);
    }
    
    private JsonNode createErrorResponse(String code, String message, JsonNode id) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) {
            response.set("id", id);
        }
        
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        
        return response;
    }
}