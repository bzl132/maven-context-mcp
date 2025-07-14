#!/usr/bin/env node

/**
 * Maven Context MCP - NPX 可执行文件
 * 通过 npx 运行 Maven Context MCP 服务器
 */

import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { spawn } from 'child_process';
import { existsSync, readFileSync } from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const projectRoot = join(__dirname, '..');
const mainScript = join(projectRoot, 'src', 'index.js');

// 检查主脚本是否存在
if (!existsSync(mainScript)) {
  console.error('错误: 找不到主脚本文件:', mainScript);
  process.exit(1);
}

// 解析命令行参数
const args = process.argv.slice(2);
const options = {
  stdio: 'inherit',
  cwd: projectRoot
};

// 设置环境变量
const env = { ...process.env };

// 从命令行参数中提取配置
for (let i = 0; i < args.length; i++) {
  const arg = args[i];
  
  if (arg === '--maven-repo' && i + 1 < args.length) {
    env.MAVEN_REPO_PATH = args[i + 1];
    args.splice(i, 2);
    i--;
  } else if (arg === '--cache-db' && i + 1 < args.length) {
    env.CACHE_DB_PATH = args[i + 1];
    args.splice(i, 2);
    i--;
  } else if (arg === '--log-level' && i + 1 < args.length) {
    env.LOG_LEVEL = args[i + 1];
    args.splice(i, 2);
    i--;
  } else if (arg === '--log-dir' && i + 1 < args.length) {
    env.LOG_DIR = args[i + 1];
    args.splice(i, 2);
    i--;
  } else if (arg === '--help' || arg === '-h') {
    showHelp();
    process.exit(0);
  } else if (arg === '--version' || arg === '-v') {
    showVersion();
    process.exit(0);
  }
}

options.env = env;

// 启动主进程
const child = spawn('node', [mainScript, ...args], options);

// 处理进程信号
process.on('SIGINT', () => {
  child.kill('SIGINT');
});

process.on('SIGTERM', () => {
  child.kill('SIGTERM');
});

// 处理子进程退出
child.on('exit', (code) => {
  process.exit(code);
});

function showHelp() {
  console.log(`
Maven Context MCP - Node.js 实现

用法:
  npx maven-context-mcp [选项]

选项:
  --maven-repo <path>    Maven 仓库路径 (默认: ~/.m2/repository)
  --cache-db <path>      缓存数据库路径 (默认: ./cache/maven-classes.db)
  --log-level <level>    日志级别 (默认: info)
  --log-dir <path>       日志目录 (默认: ./logs)
  -h, --help            显示帮助信息
  -v, --version         显示版本信息

示例:
  npx maven-context-mcp
  npx maven-context-mcp --maven-repo /path/to/repo --log-level debug
  npx maven-context-mcp --cache-db ./my-cache.db

环境变量:
  MAVEN_REPO_PATH       Maven 仓库路径
  CACHE_DB_PATH         缓存数据库路径
  LOG_LEVEL             日志级别
  LOG_DIR               日志目录
`);
}

function showVersion() {
  try {
    const packageJson = JSON.parse(
      readFileSync(join(projectRoot, 'package.json'), 'utf8')
    );
    console.log(`Maven Context MCP v${packageJson.version}`);
  } catch (error) {
    console.log('Maven Context MCP (版本信息不可用)');
  }
}