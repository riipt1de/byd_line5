package com.znh.siemens.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 拧紧控制器，提供HTTP接口供外部调用
 */
/**
 * 拧紧控制器，提供HTTP接口供外部调用
 */
@Slf4j
public class TighteningController {
    private static volatile TighteningController instance;
    private HttpServer server;
    private  static int PORT;
    private String receivedString = "";
    private Map<String, Object> receivedData = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TighteningController(int port) {
        this.PORT = PORT;
    }
    public static TighteningController getInstance(int port){
        if (instance==null){
            synchronized (TighteningController.class){
                if (instance==null){
                    PORT = port;
                    instance = new TighteningController(port);
                }
            }
        }
        return instance;
    }


    /**
     * 启动HTTP服务器
     */
    public void startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/TighteningResults", new TighteningDataHandler());
            server.createContext("/TighteningCurves", new TighteningDataHandler());
            server.setExecutor(null); // 使用默认执行器
            server.start();
            log.info("http service start, port：" + PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 停止HTTP服务器
     */
    public void stopServer() {
        if (server != null) {
            server.stop(0);
            log.info("http service stop");
        }
    }
    
    /**
     * 获取接收到的字符串
     * @return 接收到的字符串
     */
    public String getReceivedString() {
        return receivedString;
    }
    
    /**
     * 获取接收到的数据
     * @return 接收到的数据Map
     */
    public Map<String, Object> getReceivedData() {
        return receivedData;
    }


    public void setReceivedString(String receivedString) {
        this.receivedString = receivedString;
    }

    public void setReceivedData(Map<String, Object> receivedData) {
        this.receivedData = receivedData;
    }

    /**
     * 处理拧紧数据的HTTP请求
     */
    private class TighteningDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();
                String id = extractIdFromPath(path);
                if (path.contains("TighteningCurves")){
                    sendResponse(exchange,200, "");
                    return;
                }
                if ("POST".equals(method)) {
                    // 处理POST请求
                    handlePostRequest(exchange, id);
                } else if ("GET".equals(method)) {
                    // 为了兼容性，保留GET请求处理
                    handleGetRequest(exchange, id);
                } else if ("PUT".equals(method)) {
                    // 处理PUT请求
                    handlePutRequest(exchange, id);
                } else {
                    sendResponse(exchange, 405, "Method Not Allowed");
                }
            
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "服务器内部错误: " + e.getMessage());
            }
        }
        
        /**
         * 从路径中提取ID
         * @param path 请求路径
         * @return 提取的ID，如果没有则返回null
         */
        private String extractIdFromPath(String path) {
            // 路径格式: /TighteningResults/{id}
            if (path == null || path.isEmpty()) {
                return null;
            }
            
            String[] segments = path.split("/");
            if (segments.length > 2) {
                return segments[2]; // 返回ID部分
            }
            
            return null;
        }
        
        /**
         * 处理POST请求
         * @param exchange HTTP交换对象
         * @param id 从URL路径中提取的ID
         * @throws IOException 如果发生I/O错误
         */
        private void handlePostRequest(HttpExchange exchange, String id) throws IOException {
            try {
                // 读取请求体
                String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));
                
                if (requestBody == null || requestBody.isEmpty()) {
                    sendResponse(exchange, 400, "请求体为空");
                    return;
                }
                
                // 解析JSON数据
                receivedData = objectMapper.readValue(requestBody, Map.class);
                receivedString = requestBody; // 同时保存原始字符串
                
                // 打印接收到的数据
                log.info("接收到POST数据: ID=" + id + ", 数据=" + receivedString);
                
                // 如果有ID，将其添加到接收的数据中
                if (id != null && !id.isEmpty()) {
                    receivedData.put("id", id);
                }
                
                // 返回成功状态码
                sendResponse(exchange, 200, "");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 400, "解析JSON数据失败: " + e.getMessage());
            }
        }
        
        /**
         * 处理GET请求（保留兼容性）
         * @param exchange HTTP交换对象
         * @param id 从URL路径中提取的ID
         * @throws IOException 如果发生I/O错误
         */
        private void handleGetRequest(HttpExchange exchange, String id) throws IOException {
            // 解析请求参数
            URI requestURI = exchange.getRequestURI();
            String query = requestURI.getQuery();
            Map<String, String> params = parseQueryParams(query);
            
            // 如果有ID，记录并返回成功
            if (id != null && !id.isEmpty()) {
                receivedString = "ID: " + id;
                log.info("接收到GET请求: ID=" + id);
                sendResponse(exchange, 200, "");
            }
            // 否则检查查询参数
            else if (params.containsKey("msg")) {
                receivedString = params.get("msg");
                log.info("接收到字符串: " + receivedString);
                sendResponse(exchange, 200, "");
            } else {
                sendResponse(exchange, 400, "缺少必要参数");
            }
        }
        
        /**
         * 解析查询参数
         * @param query 查询字符串
         * @return 参数映射
         */
        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> params = new HashMap<>();
            if (query != null && !query.isEmpty()) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    int idx = pair.indexOf("=");
                    if (idx > 0) {
                        String key = pair.substring(0, idx);
                        String value = pair.substring(idx + 1);
                        params.put(key, value);
                    }
                }
            }
            return params;
        }
        
        /**
         * 发送HTTP响应
         * @param exchange HTTP交换对象
         * @param statusCode 状态码
         * @param response 响应内容
         * @throws IOException 如果发生I/O错误
         */
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
        
        /**
         * 处理PUT请求
         * @param exchange HTTP交换对象
         * @param id 从URL路径中提取的ID
         * @throws IOException 如果发生I/O错误
         */
        private void handlePutRequest(HttpExchange exchange, String id) throws IOException {
            try {
                // 读取请求体
                String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));
                
                if (requestBody == null || requestBody.isEmpty()) {
                    sendResponse(exchange, 400, "请求体为空");
                    return;
                }
                
                // 解析JSON数据
                receivedData = objectMapper.readValue(requestBody, Map.class);
                receivedString = requestBody; // 同时保存原始字符串
                
                // 打印接收到的数据
                log.info("PUT Data: ID=" + id + ", data=" + receivedString);

                
                // 返回成功状态码
                sendResponse(exchange, 200, "");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 400, "解析JSON数据失败: " + e.getMessage());
            }
        }
    }
}
