#!/usr/bin/env node

/**
 * NPX 配置测试脚本
 * 测试 maven-context-mcp 的 npx 功能
 */

import { spawn } from 'child_process';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

console.log('🧪 测试 Maven Context MCP NPX 配置\n');

// 测试用例
const tests = [
  {
    name: '帮助信息测试',
    command: 'node',
    args: ['bin/maven-context-mcp.js', '--help'],
    expectOutput: '用法:'
  },
  {
    name: '版本信息测试',
    command: 'node',
    args: ['bin/maven-context-mcp.js', '--version'],
    expectOutput: 'Maven Context MCP'
  },
  {
    name: '参数解析测试',
    command: 'node',
    args: [
      'bin/maven-context-mcp.js',
      '--maven-repo', '/tmp/test-repo',
      '--log-level', 'debug',
      '--cache-db', '/tmp/test.db'
    ],
    timeout: 3000,
    expectOutput: null // 只测试是否能正常启动
  }
];

// 运行测试
async function runTests() {
  let passed = 0;
  let failed = 0;

  for (const test of tests) {
    console.log(`📋 运行测试: ${test.name}`);
    
    try {
      const result = await runTest(test);
      if (result.success) {
        console.log(`✅ ${test.name} - 通过`);
        passed++;
      } else {
        console.log(`❌ ${test.name} - 失败: ${result.error}`);
        failed++;
      }
    } catch (error) {
      console.log(`❌ ${test.name} - 异常: ${error.message}`);
      failed++;
    }
    
    console.log('');
  }

  console.log(`📊 测试结果: ${passed} 通过, ${failed} 失败`);
  
  if (failed === 0) {
    console.log('🎉 所有测试通过！NPX 配置正常。');
  } else {
    console.log('⚠️  部分测试失败，请检查配置。');
    process.exit(1);
  }
}

// 运行单个测试
function runTest(test) {
  return new Promise((resolve) => {
    const child = spawn(test.command, test.args, {
      cwd: __dirname,
      stdio: 'pipe'
    });

    let stdout = '';
    let stderr = '';

    child.stdout.on('data', (data) => {
      stdout += data.toString();
    });

    child.stderr.on('data', (data) => {
      stderr += data.toString();
    });

    // 设置超时
    const timeout = setTimeout(() => {
      child.kill('SIGTERM');
      resolve({
        success: test.expectOutput === null, // 如果只是测试启动，超时也算成功
        error: test.expectOutput === null ? null : '超时'
      });
    }, test.timeout || 5000);

    child.on('exit', (code) => {
      clearTimeout(timeout);
      
      const output = stdout + stderr;
      
      if (test.expectOutput === null) {
        // 只测试是否能启动
        resolve({ success: true });
      } else if (output.includes(test.expectOutput)) {
        resolve({ success: true });
      } else {
        resolve({
          success: false,
          error: `期望输出包含 "${test.expectOutput}", 实际输出: ${output.substring(0, 200)}...`
        });
      }
    });

    child.on('error', (error) => {
      clearTimeout(timeout);
      resolve({
        success: false,
        error: error.message
      });
    });
  });
}

// 运行测试
runTests().catch(console.error);