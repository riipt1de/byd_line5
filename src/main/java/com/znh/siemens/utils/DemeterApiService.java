package com.znh.siemens.utils;

import com.alibaba.fastjson2.util.DateUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znh.siemens.config.DemeterApiConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 * API服务工具类
 * 负责处理所有与外部API的HTTP通信，包括登录认证、数据上报等功能
 */
@Slf4j
public class DemeterApiService {
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * 初始化马头API配置
     * 在应用启动时调用此方法
     */
    public static void initApiConfigs() {
        DemeterApiConfig.initDefaultConfigs();
    }

    /**
     * 使用默认配置发送测试数据
     * @return 是否发送成功
     */
    public static boolean getDemeterData() {
        return getDemeterData("default");
    }
    
    /**
     * 使用指定配置发送测试数据
     * @param configName 配置名称
     * @return 是否发送成功
     */
    public static boolean getDemeterData(String configName) {
        try {
            log.info("[start] Get Data from Demeter at -->  " + DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"));

            // 获取指定的API配置
            DemeterApiConfig config = DemeterApiConfig.getApiConfig(configName);
            if (config == null) {
                log.error("can't find Demeter config: " + configName);
                return false;
            }
            
            // 构建带参数的URL
            String urlWithId = config.getUrlWithId();
            URL url = new URL(urlWithId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            
            // 建立连接并获取响应
            int responseCode = conn.getResponseCode();
            log.info("responseCode: " + responseCode);
            
            // 只有当响应码为200时才返回true
            if (responseCode != 200) {
                log.error("failed!!! responseCode: " + responseCode);
                return false;
            }
            
            // 读取并打印响应内容
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder responseContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseContent.append(line);
            }
            reader.close();
            log.info("Demeter data: " + responseContent.toString());
            
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            log.error("failed!!! " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 添加新的马头API配置
     * @param name 配置名称
     * @param ip IP地址
     * @param port 端口
     * @return 新创建的配置对象
     */
    public static DemeterApiConfig addApiConfig(String name, String ip, String port,String id) {
        DemeterApiConfig config = new DemeterApiConfig(name, ip, port, id);
        DemeterApiConfig.addApiConfig(config);
        return config;
    }

}