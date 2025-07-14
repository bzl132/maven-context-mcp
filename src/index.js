#!/usr/bin/env node

import { createReadStream, createWriteStream } from 'fs';
import { createInterface } from 'readline';
import winston from 'winston';
import { McpServer } from './mcp-server.js';
import { ConfigManager } from './config-manager.js';

// 配置日志
const logger = winston.createLogger({
  level: process.env.LOG_LEVEL || 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.printf(({ timestamp, level, message, ...meta }) => {
      return `${timestamp} [${level.toUpperCase()}] ${message} ${Object.keys(meta).length ? JSON.stringify(meta) : ''}`;
    })
  ),
  transports: [
    new winston.transports.Console({
      silent: process.env.NODE_ENV === 'production' // 生产环境不输出到控制台
    }),
    new winston.transports.File({
      filename: `${process.env.HOME}/.maven-context-mcp/logs/mcp-server.log`,
      maxsize: 10 * 1024 * 1024, // 10MB
      maxFiles: 30
    })
  ]
});

/**
 * 主函数
 */
async function main() {
  try {
    logger.info('启动Maven Context MCP服务器...');
    
    // 初始化配置
    const config = new ConfigManager();
    await config.initialize();
    
    // 创建MCP服务器实例
    const mcpServer = new McpServer(config, logger);
    
    // 设置标准输入输出处理
    const rl = createInterface({
      input: process.stdin,
      output: process.stdout,
      crlfDelay: Infinity
    });
    
    // 处理每一行输入
    rl.on('line', async (line) => {
      try {
        if (line.trim()) {
          const request = JSON.parse(line);
          const response = await mcpServer.handleRequest(request);
          console.log(JSON.stringify(response));
        }
      } catch (error) {
        logger.error('处理请求时出错:', error);
        const errorResponse = {
          jsonrpc: '2.0',
          error: {
            code: -32603,
            message: 'Internal error',
            data: error.message
          },
          id: null
        };
        console.log(JSON.stringify(errorResponse));
      }
    });
    
    // 处理进程退出
    process.on('SIGINT', async () => {
      logger.info('收到SIGINT信号，正在关闭服务器...');
      rl.close();
      await mcpServer.close();
      process.exit(0);
    });
    
    process.on('SIGTERM', async () => {
      logger.info('收到SIGTERM信号，正在关闭服务器...');
      rl.close();
      await mcpServer.close();
      process.exit(0);
    });
    
    logger.info('MCP服务器已启动，等待请求...');
    
  } catch (error) {
    logger.error('启动服务器失败:', error);
    process.exit(1);
  }
}

// 启动应用
main().catch((error) => {
  console.error('启动失败:', error);
  process.exit(1);
});