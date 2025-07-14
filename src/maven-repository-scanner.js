import { readdir, stat } from 'fs/promises';
import { join, extname } from 'path';
import Database from 'sqlite3';
import yauzl from 'yauzl';
import { promisify } from 'util';

/**
 * Maven仓库扫描器
 */
export class MavenRepositoryScanner {
  constructor(config, logger) {
    this.config = config;
    this.logger = logger;
    this.db = null;
  }

  /**
   * 初始化数据库
   */
  async initializeDatabase() {
    return new Promise((resolve, reject) => {
      this.db = new Database.Database(this.config.getCacheDbPath(), (err) => {
        if (err) {
          reject(err);
          return;
        }
        
        // 创建表
        this.db.serialize(() => {
          this.db.run(`
            CREATE TABLE IF NOT EXISTS classes (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              class_name TEXT NOT NULL,
              package_name TEXT,
              jar_path TEXT NOT NULL,
              methods TEXT,
              fields TEXT,
              content BLOB,
              last_modified INTEGER,
              created_at INTEGER DEFAULT (strftime('%s', 'now')),
              UNIQUE(class_name, jar_path)
            )
          `);
          
          this.db.run(`
            CREATE INDEX IF NOT EXISTS idx_class_name ON classes(class_name)
          `);
          
          this.db.run(`
            CREATE INDEX IF NOT EXISTS idx_package_name ON classes(package_name)
          `);
          
          this.db.run(`
            CREATE INDEX IF NOT EXISTS idx_jar_path ON classes(jar_path)
          `);
          
          resolve();
        });
      });
    });
  }

  /**
   * 更新缓存
   */
  async updateCache(force = false) {
    if (!this.db) {
      await this.initializeDatabase();
    }
    
    this.logger.info('开始扫描Maven仓库...');
    
    const stats = {
      scannedJars: 0,
      newClasses: 0,
      updatedClasses: 0
    };
    
    const jarFiles = await this.findJarFiles(this.config.getMavenRepoPath());
    
    for (const jarFile of jarFiles) {
      try {
        const jarStat = await stat(jarFile);
        const lastModified = Math.floor(jarStat.mtime.getTime() / 1000);
        
        // 检查是否需要扫描
        if (!force && await this.isJarUpToDate(jarFile, lastModified)) {
          continue;
        }
        
        this.logger.debug(`扫描JAR文件: ${jarFile}`);
        const classes = await this.scanJarFile(jarFile);
        
        for (const classInfo of classes) {
          const result = await this.saveClassInfo(classInfo, lastModified);
          if (result.isNew) {
            stats.newClasses++;
          } else {
            stats.updatedClasses++;
          }
        }
        
        stats.scannedJars++;
        
      } catch (error) {
        this.logger.warn(`扫描JAR文件失败: ${jarFile}`, error);
      }
    }
    
    this.logger.info(`扫描完成: ${stats.scannedJars} 个JAR文件, ${stats.newClasses} 个新类, ${stats.updatedClasses} 个更新类`);
    
    return stats;
  }

  /**
   * 查找所有JAR文件
   */
  async findJarFiles(dir) {
    const jarFiles = [];
    
    try {
      const entries = await readdir(dir, { withFileTypes: true });
      
      for (const entry of entries) {
        const fullPath = join(dir, entry.name);
        
        if (entry.isDirectory()) {
          const subJars = await this.findJarFiles(fullPath);
          jarFiles.push(...subJars);
        } else if (entry.isFile() && extname(entry.name) === '.jar') {
          jarFiles.push(fullPath);
        }
      }
    } catch (error) {
      this.logger.warn(`读取目录失败: ${dir}`, error);
    }
    
    return jarFiles;
  }

  /**
   * 检查JAR文件是否是最新的
   */
  async isJarUpToDate(jarPath, lastModified) {
    return new Promise((resolve) => {
      this.db.get(
        'SELECT last_modified FROM classes WHERE jar_path = ? LIMIT 1',
        [jarPath],
        (err, row) => {
          if (err || !row) {
            resolve(false);
          } else {
            resolve(row.last_modified >= lastModified);
          }
        }
      );
    });
  }

  /**
   * 扫描JAR文件
   */
  async scanJarFile(jarPath) {
    return new Promise((resolve, reject) => {
      const classes = [];
      
      yauzl.open(jarPath, { lazyEntries: true }, (err, zipfile) => {
        if (err) {
          reject(err);
          return;
        }
        
        zipfile.readEntry();
        
        zipfile.on('entry', (entry) => {
          if (entry.fileName.endsWith('.class') && !entry.fileName.includes('$')) {
            zipfile.openReadStream(entry, (err, readStream) => {
              if (err) {
                zipfile.readEntry();
                return;
              }
              
              const chunks = [];
              readStream.on('data', (chunk) => chunks.push(chunk));
              readStream.on('end', () => {
                try {
                  const classData = Buffer.concat(chunks);
                  const classInfo = this.parseClassFile(entry.fileName, classData, jarPath);
                  if (classInfo) {
                    classes.push(classInfo);
                  }
                } catch (error) {
                  this.logger.warn(`解析类文件失败: ${entry.fileName}`, error);
                }
                zipfile.readEntry();
              });
            });
          } else {
            zipfile.readEntry();
          }
        });
        
        zipfile.on('end', () => {
          resolve(classes);
        });
        
        zipfile.on('error', (err) => {
          reject(err);
        });
      });
    });
  }

  /**
   * 解析类文件
   */
  parseClassFile(fileName, classData, jarPath) {
    try {
      // 简化的类文件解析，实际应该使用专门的Java字节码解析库
      const className = fileName.replace(/\.class$/, '').replace(/\//g, '.');
      const packageName = className.includes('.') ? 
        className.substring(0, className.lastIndexOf('.')) : '';
      const simpleName = className.includes('.') ? 
        className.substring(className.lastIndexOf('.') + 1) : className;
      
      return {
        className,
        packageName,
        simpleName,
        jarPath,
        methods: [], // 需要实际的字节码解析来获取方法信息
        fields: [],  // 需要实际的字节码解析来获取字段信息
        content: classData.toString('base64')
      };
    } catch (error) {
      this.logger.warn(`解析类文件失败: ${fileName}`, error);
      return null;
    }
  }

  /**
   * 保存类信息
   */
  async saveClassInfo(classInfo, lastModified) {
    return new Promise((resolve, reject) => {
      const { className, packageName, jarPath, methods, fields, content } = classInfo;
      
      // 先尝试更新
      this.db.run(
        `UPDATE classes SET 
         package_name = ?, methods = ?, fields = ?, content = ?, last_modified = ?
         WHERE class_name = ? AND jar_path = ?`,
        [
          packageName,
          JSON.stringify(methods),
          JSON.stringify(fields),
          content,
          lastModified,
          className,
          jarPath
        ],
        function(err) {
          if (err) {
            reject(err);
            return;
          }
          
          if (this.changes > 0) {
            resolve({ isNew: false });
          } else {
            // 如果没有更新任何行，则插入新记录
            this.db.run(
              `INSERT INTO classes 
               (class_name, package_name, jar_path, methods, fields, content, last_modified)
               VALUES (?, ?, ?, ?, ?, ?, ?)`,
              [
                className,
                packageName,
                jarPath,
                JSON.stringify(methods),
                JSON.stringify(fields),
                content,
                lastModified
              ],
              function(err) {
                if (err) {
                  reject(err);
                } else {
                  resolve({ isNew: true });
                }
              }
            );
          }
        }.bind(this)
      );
    });
  }

  /**
   * 关闭数据库连接
   */
  async close() {
    if (this.db) {
      return new Promise((resolve) => {
        this.db.close((err) => {
          if (err) {
            this.logger.error('关闭数据库失败:', err);
          }
          resolve();
        });
      });
    }
  }
}