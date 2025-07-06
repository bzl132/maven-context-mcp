package org.maven.mcp;

/**
 * 配置异常类
 * 用于处理配置相关的错误和异常情况
 */
public class ConfigurationException extends Exception {
    
    /**
     * 构造函数
     * @param message 异常消息
     */
    public ConfigurationException(String message) {
        super(message);
    }
    
    /**
     * 构造函数
     * @param message 异常消息
     * @param cause 原因异常
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 构造函数
     * @param cause 原因异常
     */
    public ConfigurationException(Throwable cause) {
        super(cause);
    }
}