import Database from 'sqlite3';

/**
 * 类搜索服务
 */
export class ClassSearchService {
  constructor(config, logger) {
    this.config = config;
    this.logger = logger;
    this.db = null;
  }

  /**
   * 初始化数据库连接
   */
  async initializeDatabase() {
    return new Promise((resolve, reject) => {
      this.db = new Database.Database(this.config.getCacheDbPath(), Database.OPEN_READONLY, (err) => {
        if (err) {
          reject(err);
        } else {
          resolve();
        }
      });
    });
  }

  /**
   * 搜索类
   */
  async searchClasses(query, limit = 50) {
    if (!this.db) {
      await this.initializeDatabase();
    }

    return new Promise((resolve, reject) => {
      const searchQuery = `%${query}%`;
      
      const sql = `
        SELECT 
          class_name,
          package_name,
          jar_path,
          methods,
          fields
        FROM classes 
        WHERE 
          class_name LIKE ? OR 
          package_name LIKE ?
        ORDER BY 
          CASE 
            WHEN class_name = ? THEN 1
            WHEN class_name LIKE ? THEN 2
            WHEN package_name LIKE ? THEN 3
            ELSE 4
          END,
          class_name
        LIMIT ?
      `;
      
      this.db.all(
        sql,
        [
          searchQuery,    // class_name LIKE
          searchQuery,    // package_name LIKE
          query,          // exact class_name match
          `${query}%`,    // class_name starts with
          `${query}%`,    // package_name starts with
          limit
        ],
        (err, rows) => {
          if (err) {
            reject(err);
          } else {
            const results = rows.map(row => ({
              className: row.class_name,
              packageName: row.package_name,
              jarPath: row.jar_path,
              methods: this.parseJsonSafely(row.methods, []),
              fields: this.parseJsonSafely(row.fields, [])
            }));
            resolve(results);
          }
        }
      );
    });
  }

  /**
   * 获取类详细信息
   */
  async getClassDetail(className) {
    if (!this.db) {
      await this.initializeDatabase();
    }

    return new Promise((resolve, reject) => {
      this.db.get(
        `SELECT 
           class_name,
           package_name,
           jar_path,
           methods,
           fields,
           last_modified,
           created_at
         FROM classes 
         WHERE class_name = ?
         LIMIT 1`,
        [className],
        (err, row) => {
          if (err) {
            reject(err);
          } else if (!row) {
            resolve(null);
          } else {
            resolve({
              className: row.class_name,
              packageName: row.package_name,
              jarPath: row.jar_path,
              methods: this.parseJsonSafely(row.methods, []),
              fields: this.parseJsonSafely(row.fields, []),
              lastModified: new Date(row.last_modified * 1000),
              createdAt: new Date(row.created_at * 1000)
            });
          }
        }
      );
    });
  }

  /**
   * 获取类内容（字节码）
   */
  async getClassContent(className) {
    if (!this.db) {
      await this.initializeDatabase();
    }

    return new Promise((resolve, reject) => {
      this.db.get(
        'SELECT content FROM classes WHERE class_name = ? LIMIT 1',
        [className],
        (err, row) => {
          if (err) {
            reject(err);
          } else if (!row) {
            resolve(null);
          } else {
            resolve({
              className,
              content: row.content,
              contentType: 'base64'
            });
          }
        }
      );
    });
  }

  /**
   * 按包名搜索类
   */
  async searchByPackage(packageName, limit = 50) {
    if (!this.db) {
      await this.initializeDatabase();
    }

    return new Promise((resolve, reject) => {
      const searchQuery = `${packageName}%`;
      
      this.db.all(
        `SELECT 
           class_name,
           package_name,
           jar_path
         FROM classes 
         WHERE package_name LIKE ?
         ORDER BY class_name
         LIMIT ?`,
        [searchQuery, limit],
        (err, rows) => {
          if (err) {
            reject(err);
          } else {
            const results = rows.map(row => ({
              className: row.class_name,
              packageName: row.package_name,
              jarPath: row.jar_path
            }));
            resolve(results);
          }
        }
      );
    });
  }

  /**
   * 获取统计信息
   */
  async getStatistics() {
    if (!this.db) {
      await this.initializeDatabase();
    }

    return new Promise((resolve, reject) => {
      this.db.all(
        `SELECT 
           COUNT(*) as total_classes,
           COUNT(DISTINCT package_name) as total_packages,
           COUNT(DISTINCT jar_path) as total_jars
         FROM classes`,
        [],
        (err, rows) => {
          if (err) {
            reject(err);
          } else {
            resolve(rows[0] || {
              total_classes: 0,
              total_packages: 0,
              total_jars: 0
            });
          }
        }
      );
    });
  }

  /**
   * 安全解析JSON
   */
  parseJsonSafely(jsonString, defaultValue = null) {
    try {
      return jsonString ? JSON.parse(jsonString) : defaultValue;
    } catch (error) {
      this.logger.warn('JSON解析失败:', error);
      return defaultValue;
    }
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