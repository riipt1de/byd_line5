package com.znh.siemens.model;

import com.alibaba.fastjson2.util.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 测试数据模型类
 * 用于构建符合SendTestData接口要求的数据格式
 */
public class TestDataModel {
    @JsonProperty("SN")
    private String sn;
    
    @JsonProperty("LineCode")
    private String lineCode;
    
    @JsonProperty("NodeCode")
    private String nodeCode;
    
    @JsonProperty("StationCode")
    private String stationCode;
    
    @JsonProperty("TestResult")
    private String testResult;
    
    @JsonProperty("TestTime")
    private String testTime;
    
    @JsonProperty("TestData")
    private List<TestItem> testData = new ArrayList<>();
    public void clearData(){
        testData = new ArrayList<>();
    }
    
    /**
     * 测试项目模型类
     * 用于构建测试数据中的测试项目列表
     */
    public static class TestItem {
        @JsonProperty("TestObject")
        private String testObject;
        
        @JsonProperty("TestItem")
        private String testItem;
        
        @JsonProperty("TestValue")
        private String testValue;
        
        @JsonProperty("TestUnits")
        private String testUnits;
        
        @JsonProperty("TestResult")
        private String testResult;
        
        // 构造函数
        public TestItem() {
        }
        
        public TestItem(String testObject, String testItem, String testValue, String testUnits, String testResult) {
            this.testObject = testObject;
            this.testItem = testItem;
            this.testValue = testValue;
            this.testUnits = testUnits;
            this.testResult = testResult;
        }
        
        // Getter和Setter方法
        public String getTestObject() {
            return testObject;
        }
        
        public void setTestObject(String testObject) {
            this.testObject = testObject;
        }
        
        public String getTestItem() {
            return testItem;
        }
        
        public void setTestItem(String testItem) {
            this.testItem = testItem;
        }
        
        public String getTestValue() {
            return testValue;
        }
        
        public void setTestValue(String testValue) {
            this.testValue = testValue;
        }
        
        public String getTestUnits() {
            return testUnits;
        }
        
        public void setTestUnits(String testUnits) {
            this.testUnits = testUnits;
        }
        
        public String getTestResult() {
            return testResult;
        }
        
        public void setTestResult(String testResult) {
            this.testResult = testResult;
        }
    }
    
    // 构造函数
    public TestDataModel() {
        this.testTime = DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss"); // 默认设置为当前时间
    }
    
    // Getter和Setter方法
    public String getSn() {
        return sn;
    }
    
    public void setSn(String sn) {
        this.sn = sn;
    }
    
    public String getLineCode() {
        return lineCode;
    }
    
    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }
    
    public String getNodeCode() {
        return nodeCode;
    }
    
    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }
    
    public String getStationCode() {
        return stationCode;
    }
    
    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }
    
    public String getTestResult() {
        return testResult;
    }
    
    public void setTestResult(String testResult) {
        this.testResult = testResult;
    }
    
    public String getTestTime() {
        return testTime;
    }
    
    public void setTestTime(String testTime) {
        this.testTime = testTime;
    }
    
    public List<TestItem> getTestData() {
        return testData;
    }
    
    public void setTestData(List<TestItem> testData) {
        this.testData = testData;
    }
    
    /**
     * 添加测试项目到测试数据列表
     * @param testItem 测试项目
     */
    public void addTestItem(TestItem testItem) {
        this.testData.add(testItem);
    }
    
    /**
     * 添加测试项目到测试数据列表
     * @param testObject 测试对象
     * @param testItem 测试项目
     * @param testValue 测试值
     * @param testUnits 测试单位
     * @param testResult 测试结果
     */
    public void addTestItem(String testObject, String testItem, String testValue, String testUnits, String testResult) {
        TestItem item = new TestItem(testObject, testItem, testValue, testUnits, testResult);
        this.testData.add(item);
    }
}