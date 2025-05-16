package com.znh.siemens.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 测试数据模型类
 * 用于构建符合SendTestData接口要求的数据格式
 */
public class CheckDataModel {
    @JsonProperty("SN")
    private String sn;

    @JsonProperty("LineCode")
    private String lineCode;

    @JsonProperty("NodeCode")
    private String nodeCode;

    @JsonProperty("StationCode")
    private String stationCode;


    @JsonProperty("AssemblyTime")
    private String assemblyTime;

    @JsonProperty("AssemblyCode")
    private String assemblyCode;


    // 构造函数
    public CheckDataModel() {
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


    public String getAssemblyTime() {
        return assemblyTime;
    }

    public void setAssemblyTime(String assemblyTime) {
        this.assemblyTime = assemblyTime;
    }

    public String getAssemblyCode() {
        return assemblyCode;
    }

    public void setAssemblyCode(String assemblyCode) {
        this.assemblyCode = assemblyCode;
    }

}