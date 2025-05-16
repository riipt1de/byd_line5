package com.znh.siemens.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 马头API配置加载器
 * 用于从配置文件加载马头API的配置信息
 */
@Slf4j
public class DemeterApiConfigLoader {
    private static final String CONFIG_FILE_PATH = "config/demeter_api_config.json";
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * 从配置文件加载马头API配置
     * 配置文件格式示例：
     * {
     *   "apis": [
     *     {
     *       "name": "马头1",
     *       "ip": "192.168.1.100",
     *       "port": "8080",
     *       "params": {
     *         "vin": "123456",
     *         "includeCurve": "false",
     *         "includeSteps": "false"
     *       }
     *     },
     *     {
     *       "name": "马头2",
     *       "ip": "192.168.1.101",
     *       "port": "8081",
     *       "params": {
     *         "vin": "654321",
     *         "includeCurve": "true",
     *         "includeSteps": "true"
     *       }
     *     }
     *   ]
     * }
     */
    public static void loadConfig() {
        try {
            // 首先尝试从文件系统加载配置文件
            File configFile = new File(CONFIG_FILE_PATH);
            if (configFile.exists()) {
                loadConfigFromFile(configFile);
                return;
            }
            
            // 如果文件系统中不存在，则尝试从类路径加载
            try (InputStream is = DemeterApiConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE_PATH)) {
                if (is != null) {
                    loadConfigFromStream(is);
                    return;
                }
            }
            
            // 如果配置文件不存在，则使用默认配置
            log.info("未找到马头API配置文件，使用默认配置");
            DemeterApiConfig.initDefaultConfigs();
        } catch (Exception e) {
            log.error("加载马头API配置失败: " + e.getMessage(), e);
            // 使用默认配置
            DemeterApiConfig.initDefaultConfigs();
        }
    }
    
    /**
     * 从文件加载配置
     * @param file 配置文件
     * @throws IOException 如果读取文件失败
     */
    private static void loadConfigFromFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            loadConfigFromStream(fis);
        }
    }
    
    /**
     * 从输入流加载配置
     * @param is 输入流
     * @throws IOException 如果读取流失败
     */
    private static void loadConfigFromStream(InputStream is) throws IOException {
        JsonNode root = mapper.readTree(is);
        JsonNode apis = root.path("apis");
        
        if (apis.isArray()) {
            for (JsonNode apiNode : apis) {
                String name = apiNode.path("name").asText();
                String ip = apiNode.path("ip").asText();
                String port = apiNode.path("port").asText();
                String id = apiNode.path("id").asText();

                DemeterApiConfig config = new DemeterApiConfig(name, ip, port,id);
                
                // 加载参数
                JsonNode paramsNode = apiNode.path("params");
                if (paramsNode.isObject()) {
                    Iterator<String> fieldNames = paramsNode.fieldNames();
                    while (fieldNames.hasNext()) {
                        String key = fieldNames.next();
                        String value = paramsNode.path(key).asText();
                        config.addParam(key, value);
                    }
                }
                
                DemeterApiConfig.addApiConfig(config);
                log.info("已加载马头API配置: " + name);
            }
        }
    }
}