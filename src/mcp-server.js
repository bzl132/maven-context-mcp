import winston from 'winston';
import { MavenRepositoryScanner } from './maven-repository-scanner.js';
import { ClassSearchService } from './class-search-service.js';

/**
 * MCP服务器主类
 */
export class McpServer {
  constructor(config, logger) {
    this.config = config;
    this.logger = logger;
    this.scanner = new MavenRepositoryScanner(config, logger);
    this.searchService = new ClassSearchService(config, logger);
  }

  /**
   * 处理MCP请求
   */
  async handleRequest(request) {
    const { method, params, id } = request;
    
    this.logger.debug(`处理请求: ${method}`);
    
    try {
      switch (method) {
        case 'initialize':
          return this.handleInitialize(id);
        case 'tools/list':
          return this.handleToolsList(id);
        case 'tools/call':
          return await this.handleToolsCall(params, id);
        default:
          return this.createErrorResponse('method_not_found', `未知方法: ${method}`, id);
      }
    } catch (error) {
      this.logger.error(`处理请求失败: ${method}`, error);
      return this.createErrorResponse('internal_error', error.message, id);
    }
  }

  /**
   * 处理初始化请求
   */
  handleInitialize(id) {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        protocolVersion: '2024-11-05',
        capabilities: {
          tools: {
            listChanged: false
          }
        },
        serverInfo: {
          name: 'maven-context-mcp',
          version: '1.0.0'
        }
      }
    };
  }

  /**
   * 处理工具列表请求
   */
  handleToolsList(id) {
    const tools = [
      {
        name: 'search_class',
        description: '在Maven本地仓库中搜索类，支持模糊搜索和忽略大小写',
        inputSchema: {
          type: 'object',
          properties: {
            query: {
              type: 'string',
              description: '搜索关键词，可以是类名、包名或方法名'
            },
            limit: {
              type: 'integer',
              description: '返回结果数量限制，默认50',
              default: 50
            }
          },
          required: ['query']
        }
      },
      {
        name: 'get_class_detail',
        description: '获取指定类的详细信息，包括所有方法和字段',
        inputSchema: {
          type: 'object',
          properties: {
            className: {
              type: 'string',
              description: '完整的类名（包含包名）'
            }
          },
          required: ['className']
        }
      },
      {
        name: 'get_class_content',
        description: '获取指定类的字节码文件内容，返回Base64编码的类文件数据',
        inputSchema: {
          type: 'object',
          properties: {
            className: {
              type: 'string',
              description: '完整的类名（包含包名）'
            },
            jarPath: {
              type: 'string',
              description: 'JAR文件路径（可选，如果不提供则使用搜索到的第一个匹配项）'
            }
          },
          required: ['className']
        }
      },
      {
        name: 'update_cache',
        description: '手动更新Maven仓库缓存，扫描新的JAR文件',
        inputSchema: {
          type: 'object',
          properties: {
            force: {
              type: 'boolean',
              description: '是否强制重新扫描所有JAR文件，默认false',
              default: false
            }
          }
        }
      }
    ];

    return {
      jsonrpc: '2.0',
      id,
      result: {
        tools
      }
    };
  }

  /**
   * 处理工具调用请求
   */
  async handleToolsCall(params, id) {
    const { name: toolName, arguments: args } = params;
    
    try {
      switch (toolName) {
        case 'search_class':
          return await this.handleSearchClass(args, id);
        case 'get_class_detail':
          return await this.handleGetClassDetail(args, id);
        case 'get_class_content':
          return await this.handleGetClassContent(args, id);
        case 'update_cache':
          return await this.handleUpdateCache(args, id);
        default:
          return this.createErrorResponse('invalid_params', `未知工具: ${toolName}`, id);
      }
    } catch (error) {
      this.logger.error(`执行工具失败: ${toolName}`, error);
      return this.createErrorResponse('internal_error', `执行工具时出错: ${error.message}`, id);
    }
  }

  /**
   * 处理搜索类请求
   */
  async handleSearchClass(args, id) {
    const { query, limit = 50 } = args;
    
    if (!query || query.trim() === '') {
      return this.createErrorResponse('invalid_params', '搜索关键词不能为空', id);
    }
    
    const results = await this.searchService.searchClasses(query.trim(), limit);
    
    return {
      jsonrpc: '2.0',
      id,
      result: {
        content: [
          {
            type: 'text',
            text: `找到 ${results.length} 个匹配的类:\n\n${this.formatSearchResults(results)}`
          }
        ]
      }
    };
  }

  /**
   * 处理获取类详情请求
   */
  async handleGetClassDetail(args, id) {
    const { className } = args;
    
    if (!className || className.trim() === '') {
      return this.createErrorResponse('invalid_params', '类名不能为空', id);
    }
    
    const classInfo = await this.searchService.getClassDetail(className.trim());
    
    if (!classInfo) {
      return this.createErrorResponse('not_found', `未找到类: ${className}`, id);
    }
    
    return {
      jsonrpc: '2.0',
      id,
      result: {
        content: [
          {
            type: 'text',
            text: this.formatClassDetail(classInfo)
          }
        ]
      }
    };
  }

  /**
   * 处理获取类内容请求
   */
  async handleGetClassContent(args, id) {
    const { className, jarPath } = args;
    
    if (!className || className.trim() === '') {
      return this.createErrorResponse('invalid_params', '类名不能为空', id);
    }
    
    const content = await this.searchService.getClassContent(className.trim(), jarPath);
    
    if (!content) {
      return this.createErrorResponse('not_found', `未找到类文件: ${className}`, id);
    }
    
    return {
      jsonrpc: '2.0',
      id,
      result: {
        content: [
          {
            type: 'text',
            text: `类文件内容 (Base64编码):\n${content}`
          }
        ]
      }
    };
  }

  /**
   * 处理更新缓存请求
   */
  async handleUpdateCache(args, id) {
    const { force = false } = args;
    
    const result = await this.scanner.updateCache(force);
    
    return {
      jsonrpc: '2.0',
      id,
      result: {
        content: [
          {
            type: 'text',
            text: `缓存更新完成:\n扫描的JAR文件: ${result.scannedJars}\n新增类: ${result.newClasses}\n更新类: ${result.updatedClasses}`
          }
        ]
      }
    };
  }

  /**
   * 格式化搜索结果
   */
  formatSearchResults(results) {
    return results.map((result, index) => {
      return `${index + 1}. **${result.className}**\n` +
             `   包名: ${result.packageName}\n` +
             `   JAR文件: ${result.jarPath}\n` +
             `   匹配分数: ${result.score}`;
    }).join('\n\n');
  }

  /**
   * 格式化类详情
   */
  formatClassDetail(classInfo) {
    let result = `# 类详情: ${classInfo.className}\n\n`;
    result += `**包名**: ${classInfo.packageName}\n`;
    result += `**JAR文件**: ${classInfo.jarPath}\n\n`;
    
    if (classInfo.methods && classInfo.methods.length > 0) {
      result += `## 方法 (${classInfo.methods.length})\n\n`;
      classInfo.methods.forEach((method, index) => {
        result += `${index + 1}. ${method}\n`;
      });
      result += '\n';
    }
    
    if (classInfo.fields && classInfo.fields.length > 0) {
      result += `## 字段 (${classInfo.fields.length})\n\n`;
      classInfo.fields.forEach((field, index) => {
        result += `${index + 1}. ${field}\n`;
      });
    }
    
    return result;
  }

  /**
   * 创建错误响应
   */
  createErrorResponse(code, message, id = null) {
    return {
      jsonrpc: '2.0',
      error: {
        code: this.getErrorCode(code),
        message,
        data: code
      },
      id
    };
  }

  /**
   * 获取错误代码
   */
  getErrorCode(code) {
    const errorCodes = {
      'method_not_found': -32601,
      'invalid_params': -32602,
      'internal_error': -32603,
      'not_found': -32001
    };
    return errorCodes[code] || -32603;
  }

  /**
   * 调用工具
   */
  async callTool(name, arguments_) {
    try {
      switch (name) {
        case 'search_class':
          return await this.searchClass(arguments_.query, arguments_.limit);
        case 'get_class_detail':
          return await this.getClassDetail(arguments_.className);
        case 'get_class_content':
          return await this.getClassContent(arguments_.className);
        case 'update_cache':
          return await this.updateCache(arguments_.force);
        default:
          throw new Error(`未知工具: ${name}`);
      }
    } catch (error) {
      this.logger.error(`工具调用失败: ${name}`, error);
      throw error;
    }
  }

  /**
   * 搜索类
   */
  async searchClass(query, limit = 50) {
    try {
      const results = await this.searchService.searchClasses(query, limit);
      return {
        results,
        total: results.length,
        query
      };
    } catch (error) {
      this.logger.error('搜索类失败:', error);
      throw error;
    }
  }

  /**
   * 获取类详细信息
   */
  async getClassDetail(className) {
    try {
      const detail = await this.searchService.getClassDetail(className);
      if (!detail) {
        throw new Error(`类不存在: ${className}`);
      }
      return detail;
    } catch (error) {
      this.logger.error('获取类详细信息失败:', error);
      throw error;
    }
  }

  /**
   * 获取类内容
   */
  async getClassContent(className) {
    try {
      const content = await this.searchService.getClassContent(className);
      if (!content) {
        throw new Error(`类内容不存在: ${className}`);
      }
      return content;
    } catch (error) {
      this.logger.error('获取类内容失败:', error);
      throw error;
    }
  }

  /**
   * 更新缓存
   */
  async updateCache(force = false) {
    try {
      const stats = await this.scanner.updateCache(force);
      return {
        success: true,
        message: '缓存更新完成',
        stats
      };
    } catch (error) {
      this.logger.error('更新缓存失败:', error);
      throw error;
    }
  }

  /**
   * 格式化响应
   */
  formatResponse(id, result, error = null) {
    const response = {
      jsonrpc: '2.0',
      id
    };

    if (error) {
      response.error = {
        code: -32000,
        message: error.message || '内部错误'
      };
    } else {
      response.result = result;
    }

    return response;
  }

  /**
   * 关闭服务器
   */
  async close() {
    try {
      await this.scanner.close();
      await this.searchService.close();
      this.logger.info('MCP服务器已关闭');
    } catch (error) {
      this.logger.error('关闭服务器失败:', error);
    }
  }
}