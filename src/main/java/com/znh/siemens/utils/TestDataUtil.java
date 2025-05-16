package com.znh.siemens.utils;

import com.alibaba.fastjson2.util.DateUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znh.siemens.model.TestDataModel;

import java.util.Date;

/**
 * 测试数据工具类
 * 提供创建和处理测试数据的便捷方法
 */
public class TestDataUtil {
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * 创建一个基本的测试数据模型
     * @param sn 序列号
     * @param lineCode 线体代码
     * @param nodeCode 节点代码
     * @param stationCode 工位代码
     * @param testResult 测试结果
     * @return 测试数据模型对象
     */
    public static TestDataModel createTestData(String sn, String lineCode, String nodeCode, String stationCode, String testResult) {
        TestDataModel model = new TestDataModel();
        model.setSn(sn);
        model.setLineCode(lineCode);
        model.setNodeCode(nodeCode);
        model.setStationCode(stationCode);
        model.setTestResult(testResult);
        model.setTestTime(DateUtils.format(new Date(),"yyyy-MM-dd HH:mm:ss")); // 设置为当前时间
        return model;
    }
    
    /**
     * 将测试数据模型转换为JSON字符串
     * @param model 测试数据模型
     * @return JSON字符串
     * @throws JsonProcessingException 如果转换失败
     */
    public static String toJsonString(TestDataModel model) throws JsonProcessingException {
        return mapper.writeValueAsString(model);
    }
    
    /**
     * 创建测试数据并发送到接口
     * @param model 测试数据模型
     * @return 是否发送成功
     */
    public static boolean sendTestData(TestDataModel model) {
        try {
            String jsonData = toJsonString(model);
            return ApiService.sendTestData(jsonData);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            System.err.println("测试数据转换为JSON失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 创建一个示例测试数据模型
     * @return 示例测试数据模型
     */
    public static TestDataModel createExampleTestData() {
        TestDataModel model = createTestData("SN0000001", "Line004", "OP11", "OP11-1", "OK");
        
        // 添加测试项目
        model.addTestItem("上油路板", "上油路板轴承端面间隙1", "10", "mm", "OK");
        model.addTestItem("上油路板", "上油路板轴承端面间隙2", "11", "mm", "OK");
        
        return model;
    }
}