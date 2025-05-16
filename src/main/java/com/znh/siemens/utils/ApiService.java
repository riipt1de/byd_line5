package com.znh.siemens.utils;

import com.alibaba.fastjson2.util.DateUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znh.siemens.model.CheckDataModel;
import com.znh.siemens.model.PlcAccount;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * API服务工具类
 * 负责处理所有与外部API的HTTP通信，包括登录认证、数据上报等功能
 */
@Slf4j
public class ApiService {
    private static final String BASE_URL = "http://110.9.5.3:5004/openapi/OpenApi";
    private static final String LOGIN_URL = BASE_URL + "/Login";
    private static final String TEST_DATA_URL = BASE_URL + "/SendTestData";
    private static final String Pass_DATA_URL = BASE_URL + "/SendPassData";
    private static final String BIND_SN_URL = BASE_URL + "/BindScheduleSN";
    private static final String FUNCTION_DATA_URL = BASE_URL + "/FunctionData";
    private static final String CHECK_BEFORE_PASSIN = BASE_URL + "/SendAssemblyData";
    
    private static String token = "";
    private static final ReentrantLock tokenLock = new ReentrantLock();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String PLC_ACCOUNTS_FILE = "data/mes_accounts.json";

    // 当前使用的PLC ID
    private static String currentPlcId = "";

    // 存储所有PLC账号的列表
    private static List<PlcAccount> plcAccounts = new ArrayList<>();
    // 默认的用户名和密码，实际应用中应从配置文件或安全存储中获取
    private static final String DEFAULT_USERNAME = "wutao";
    private static final String DEFAULT_PASSWORD = "123456";
    // 静态代码块，程序启动时加载PLC账号配置
    static {
        loadPlcAccounts();
    }

    /**
     * 加载PLC账号配置
     */
    public static void loadPlcAccounts() {
        try {
            File file = new File(PLC_ACCOUNTS_FILE);
            if (file.exists()) {
                plcAccounts = mapper.readValue(file, new TypeReference<List<PlcAccount>>() {});
                log.info("成功加载 {} 个PLC账号配置", plcAccounts.size());
            } else {
                log.warn("PLC账号配置文件不存在: {}", PLC_ACCOUNTS_FILE);
                // 添加默认账号
                PlcAccount defaultAccount = new PlcAccount("plc1", DEFAULT_USERNAME, DEFAULT_PASSWORD, true, "默认PLC账号");
                plcAccounts.add(defaultAccount);
            }
        } catch (Exception e) {
            log.error("加载PLC账号配置失败: {}", e.getMessage());
            // 添加默认账号
            PlcAccount defaultAccount = new PlcAccount("plc1", DEFAULT_USERNAME, DEFAULT_PASSWORD, true, "默认PLC账号");
            plcAccounts.add(defaultAccount);
        }
    }

    /**
     * 获取所有PLC账号
     * @return PLC账号列表
     */
    public static List<PlcAccount> getAllPlcAccounts() {
        if (plcAccounts.isEmpty()) {
            loadPlcAccounts();
        }
        return plcAccounts;
    }

    /**
     * 设置当前使用的PLC ID
     * @param plcId PLC标识符
     */
    public static void setCurrentPlcId(String plcId) {
        currentPlcId = plcId;
    }

    /**
     * 获取当前PLC ID
     * @return 当前使用的PLC ID
     */
    public static String getCurrentPlcId() {
        return currentPlcId;
    }

    /**
     * 根据PLC ID获取账号信息
     * @param plcId PLC标识符
     * @return 对应的账号信息，如果未找到则返回默认账号
     */
    public static PlcAccount getPlcAccount(String plcId) {
        if (plcAccounts.isEmpty()) {
            loadPlcAccounts();
        }

        // 查找指定ID的账号
        for (PlcAccount account : plcAccounts) {
            if (account.getPlcId().equals(plcId)) {
                return account;
            }
        }

        // 如果找不到指定ID的账号，尝试返回默认账号
        for (PlcAccount account : plcAccounts) {
            if (account.isDefault()) {
                return account;
            }
        }

        // 如果没有默认账号，返回第一个账号或创建一个新的默认账号
        if (!plcAccounts.isEmpty()) {
            return plcAccounts.get(0);
        } else {
            return new PlcAccount("plc1", DEFAULT_USERNAME, DEFAULT_PASSWORD, true, "默认PLC账号");
        }
    }


    /**
     * 登录并获取认证token
     * @return 是否登录成功
     */
    public static boolean login() {
        PlcAccount account;
        if (currentPlcId != null && !currentPlcId.isEmpty()) {
            account = getPlcAccount(currentPlcId);
        } else {
            // 如果没有设置当前PLC ID，寻找默认账号
            account = getPlcAccount("plc1"); // 尝试使用plc1作为默认值
        }
        return login(account.getUsername(), account.getPassword());
    }
    
    /**
     * 使用指定的用户名和密码登录并获取认证token
     * @param username 用户名
     * @param password 密码
     * @return 是否登录成功
     */
    public static boolean login(String username, String password) {
        try {
            tokenLock.lock();
            URL url = new URL(LOGIN_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            
            // 使用form-data格式
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setDoOutput(true);
            
            // 构建form-data请求体
            try(OutputStream os = conn.getOutputStream()) {
                // 添加userId参数
                String userIdPart = "--" + boundary + "\r\n" +
                                   "Content-Disposition: form-data; name=\"userId\"\r\n\r\n" +
                                   username + "\r\n";
                os.write(userIdPart.getBytes("utf-8"));
                
                // 添加password参数
                String passwordPart = "--" + boundary + "\r\n" +
                                     "Content-Disposition: form-data; name=\"password\"\r\n\r\n" +
                                     password + "\r\n";
                os.write(passwordPart.getBytes("utf-8"));
                
                // 添加结束标记
                String endBoundary = "--" + boundary + "--\r\n";
                os.write(endBoundary.getBytes("utf-8"));
            }
            
            // 获取响应
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    // 解析响应JSON，获取token
                    JsonNode root = mapper.readTree(response.toString());
                    int success = root.path("Res").asInt();
                    if (success == 0) {
                        token = root.path("Token").asText();
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            tokenLock.unlock();
        }
    }
    
    /**
     * 发送测试数据到接口
     * @param testData 测试数据JSON字符串
     * @return 是否发送成功
     */
    public static boolean sendTestData(String testData) {
        try {
            log.info("[start] Upload to Mes at-->  "+ DateUtils.format(new Date(),"yyyy-MM-dd HH:mm:ss"));
            log.info("data:{}",testData);
            // 确保有有效的token
            ensureValidToken();
            
            URL url = new URL(TEST_DATA_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            
            // 使用form-data格式
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setDoOutput(true);
            
            // 构建form-data请求体
            try(OutputStream os = conn.getOutputStream()) {
                // 添加token参数
                String tokenPart = "--" + boundary + "\r\n" +
                                 "Content-Disposition: form-data; name=\"token\"\r\n\r\n" +
                                 token + "\r\n";
                os.write(tokenPart.getBytes("utf-8"));
                
                // 添加data参数
                String dataPart = "--" + boundary + "\r\n" +
                                 "Content-Disposition: form-data; name=\"data\"\r\n\r\n" +
                                 testData + "\r\n";
                os.write(dataPart.getBytes("utf-8"));
                
                // 添加结束标记
                String endBoundary = "--" + boundary + "--\r\n";
                os.write(endBoundary.getBytes("utf-8"));
            }

            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                // 解析响应JSON
                JsonNode root = mapper.readTree(response.toString());
                boolean success = root.path("Success").asBoolean();
                String Data = root.path("Data").asText();
                if ("401".equals(Data)){
                    if (refreshToken()) {
                        return sendTestData(testData); // 使用新token重试
                    }
                    return false;
                }
                if (!success) {
                    System.err.println("测试数据上报失败: " + response.toString());
                }
                log.info("[end] Upload to Mes at-->  "+ DateUtils.format(new Date(),"yyyy-MM-dd HH:mm:ss"));
                return success;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("测试数据上报异常: " + e.getMessage());
            return false;
        }
    }
    public static boolean sendBindSnData(String sn) {
        try {
            log.info("mes bind sn--------------start--------------------- execute time-->  "+ DateUtils.format(new Date(),"yyyy-MM-dd HH:mm:ss"));
            // 确保有有效的token
            ensureValidToken();

            URL url = new URL(BIND_SN_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");

            // 使用form-data格式
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setDoOutput(true);

            // 构建form-data请求体
            try(OutputStream os = conn.getOutputStream()) {
                // 添加token参数
                String tokenPart = "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"token\"\r\n\r\n" +
                        token + "\r\n";
                os.write(tokenPart.getBytes("utf-8"));

                // 添加data参数
                String snPart = "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"SN\"\r\n\r\n" +
                        sn + "\r\n";
                os.write(snPart.getBytes("utf-8"));

                // 添加结束标记
                String endBoundary = "--" + boundary + "--\r\n";
                os.write(endBoundary.getBytes("utf-8"));
            }
            log.info("上传到mes的数据为---->"+ sn);
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                // 解析响应JSON
                JsonNode root = mapper.readTree(response.toString());
                boolean success = root.path("Success").asBoolean();
                String Data = root.path("Data").asText();
                if ("401".equals(Data)){
                    if (refreshToken()) {
                        return sendBindSnData(sn); // 使用新token重试
                    }
                    return false;
                }
                if (!success) {
                    System.err.println("调用MES绑定SN失败: " + response.toString());
                }
                log.info("mes bind sn--------------end--------------------- execute time-->  "+ DateUtils.format(new Date(),"yyyy-MM-dd HH:mm:ss"));
                return success;
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("bind error"+e.getMessage());
            return false;
        }
    }
    public static boolean sendCheckData(String checkData) {
        try {
            log.info("sendCheckData--------------start--------------------- excute time-->  "+ DateUtils.format(new Date(),"yyyy-MM-dd HH:mm:ss"));
            // 确保有有效的token
            ensureValidToken();

            URL url = new URL(CHECK_BEFORE_PASSIN);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");

            // 使用form-data格式
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setDoOutput(true);

            // 构建form-data请求体
            try(OutputStream os = conn.getOutputStream()) {
                // 添加token参数
                String tokenPart = "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"token\"\r\n\r\n" +
                        token + "\r\n";
                os.write(tokenPart.getBytes("utf-8"));

                // 添加data参数
                String dataPart = "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"data\"\r\n\r\n" +
                        checkData + "\r\n";
                os.write(dataPart.getBytes("utf-8"));

                // 添加结束标记
                String endBoundary = "--" + boundary + "--\r\n";
                os.write(endBoundary.getBytes("utf-8"));
            }
            log.info("upload to mes data---->"+ checkData);
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                // 解析响应JSON
                JsonNode root = mapper.readTree(response.toString());
                boolean success = root.path("Success").asBoolean();
                String Data = root.path("Data").asText();
                if ("401".equals(Data)){
                    if (refreshToken()) {
                        return sendCheckData(checkData); // 使用新token重试
                    }
                    return false;
                }
                if (!success) {
                    System.err.println("sendCheckData error: " + response.toString());
                }
                log.info("sendCheckData--------------end---------------------  excute time-->  "+ DateUtils.format(new Date(),"yyyy-MM-dd HH:mm:ss"));
                return success;
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("sendCheckData error"+e.getMessage());
            return false;
        }
    }



    /**
     * 发送函数数据到接口（x和y坐标）
     * @param xData x坐标数据
     * @param yData y坐标数据
     * @return 上报是否成功
     */
    public static boolean sendFunctionData(String xData, String yData) {
        try {
            // 确保有有效的token
            ensureValidToken();
            
            URL url = new URL(FUNCTION_DATA_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (!token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setDoOutput(true);
            
            // 构建请求体，包含x和y两个参数
            String jsonInputString = String.format("{\"xData\": \"%s\", \"yData\": \"%s\"}", xData, yData);
            
            try(OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            // 获取响应
            int responseCode = conn.getResponseCode();
            if (responseCode == 401) { // 未授权，token可能已失效
                if (refreshToken()) {
                    return sendFunctionData(xData, yData); // 使用新token重试
                }
                return false;
            }
            
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                // 解析响应JSON
                JsonNode root = mapper.readTree(response.toString());
                boolean success = root.path("Success").asBoolean();
                if (!success) {
                    System.err.println("函数数据上报失败: " + response.toString());
                }
                return success;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("函数数据上报异常: " + e.getMessage());
            return false;
        }
    }
    public static boolean sendPassData(String passData) {
        try {
            // 确保有有效的token
            ensureValidToken();
            
            URL url = new URL(Pass_DATA_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            
            // 使用form-data格式
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setDoOutput(true);
            
            // 构建form-data请求体
            try(OutputStream os = conn.getOutputStream()) {
                // 添加token参数
                String tokenPart = "--" + boundary + "\r\n" +
                                 "Content-Disposition: form-data; name=\"token\"\r\n\r\n" +
                                 token + "\r\n";
                os.write(tokenPart.getBytes("utf-8"));
                
                // 添加data参数
                String dataPart = "--" + boundary + "\r\n" +
                                 "Content-Disposition: form-data; name=\"data\"\r\n\r\n" +
                                 passData + "\r\n";
                os.write(dataPart.getBytes("utf-8"));
                
                // 添加结束标记
                String endBoundary = "--" + boundary + "--\r\n";
                os.write(endBoundary.getBytes("utf-8"));
            }

            
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                // 解析响应JSON
                JsonNode root = mapper.readTree(response.toString());
                boolean success = root.path("Success").asBoolean();
                String Data = root.path("Data").asText();
                if ("401".equals(Data)){
                    if (refreshToken()) {
                        return sendPassData(passData); // 使用新token重试
                    }
                    return false;
                }
                if (!success) {
                    System.err.println("出入站数据上报失败: " + response.toString());
                }
                return success;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("出入站数据上报异常: " + e.getMessage());
            return false;
        }
    }
    /**
     * 通用的请求发送方法，用于单参数请求
     * @param urlString 请求URL
     * @param paramName 参数名
     * @param paramValue 参数值
     * @return 请求是否成功
     */
    private static boolean sendRequest(String urlString, String paramName, String paramValue) {
        try {
            // 确保有有效的token
            ensureValidToken();
            
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (!token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setDoOutput(true);
            
            // 构建请求体
            String jsonInputString = String.format("{\"" + paramName + "\": \"%s\"}", paramValue);
            
            try(OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            // 获取响应
            int responseCode = conn.getResponseCode();
            if (responseCode == 401) { // 未授权，token可能已失效
                if (refreshToken()) {
                    return sendRequest(urlString, paramName, paramValue); // 使用新token重试
                }
                return false;
            }
            
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                // 解析响应JSON
                JsonNode root = mapper.readTree(response.toString());
                boolean success = root.path("Success").asBoolean();
                if (!success) {
                    System.err.println(paramName + "上报失败: " + response.toString());
                }
                return success;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(paramName + "上报异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 确保有有效的token，如果没有则尝试登录获取
     */
    private static void ensureValidToken() {
        if (token.isEmpty()) {
            login();
        }
    }
    
    /**
     * 刷新token
     * @return 是否成功刷新token
     */
    private static boolean refreshToken() {
        return login();
    }
}