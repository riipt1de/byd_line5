package com.znh.siemens.config;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 马头API配置类
 * 用于管理多个马头API的配置信息，包括IP地址、端口和参数
 */
@Data
public class DemeterApiConfig {
    private String ip;
    private String port;
    private Map<String, String> params;
    private String name;
    private String id;

    /**
     * 存储所有马头API配置的集合
     */
    private static final Map<String, DemeterApiConfig> apiConfigs = new HashMap<>();
    
    /**
     * 默认构造函数
     */
    public DemeterApiConfig() {
        this.params = new HashMap<>();
    }
    
    /**
     * 带参数的构造函数
     * @param name API名称
     * @param ip IP地址
     * @param port 端口
     */
    public DemeterApiConfig(String name, String ip, String port,String id) {
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.id = id;
        this.params = new HashMap<>();
    }
    
    /**
     * 添加参数
     * @param key 参数名
     * @param value 参数值
     * @return 当前配置对象，用于链式调用
     */
    public DemeterApiConfig addParam(String key, String value) {
        this.params.put(key, value);
        return this;
    }
    
    /**
     * 获取完整的URL
     * @return 完整的URL，包含IP和端口
     */
    public String getFullUrl() {
        return "http://" + ip + ":" + port + "/TighteningResults";
    }
    
    /**
     * 构建带参数的URL
     * @return 带参数的完整URL
     */
    public String getUrlWithParams() {
        StringBuilder urlBuilder = new StringBuilder(getFullUrl());
        if (!params.isEmpty()) {
            urlBuilder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) {
                    urlBuilder.append("&");
                }
                urlBuilder.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }
        return urlBuilder.toString();
    }
    public String getUrlWithId() {
        return getFullUrl()+"/"+id;
    }
    
    /**
     * 添加一个API配置到集合中
     * @param config API配置
     */
    public static void addApiConfig(DemeterApiConfig config) {
        apiConfigs.put(config.getName(), config);
    }
    
    /**
     * 根据名称获取API配置
     * @param name API名称
     * @return API配置对象，如果不存在则返回null
     */
    public static DemeterApiConfig getApiConfig(String name) {
        return apiConfigs.get(name);
    }
    
    /**
     * 获取所有API配置
     * @return API配置集合
     */
    public static Map<String, DemeterApiConfig> getAllApiConfigs() {
        return apiConfigs;
    }
    
    /**
     * 初始化默认配置
     * 在应用启动时调用此方法来设置默认的马头API配置
     */
    public static void initDefaultConfigs() {
        // 添加默认的马头API配置
        DemeterApiConfig defaultConfig = new DemeterApiConfig("default", "localhost", "8080","");
        defaultConfig.addParam("vin", "123456")
                    .addParam("includeCurve", "false")
                    .addParam("includeSteps", "false");
        addApiConfig(defaultConfig);
        
        // 可以在这里添加更多的默认配置
    }
}