package com.znh.siemens.model;

import com.alibaba.fastjson2.util.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

/**
 * 过站数据模型类
 * 用于构建符合SendPassData接口要求的数据格式
 * PassType: 1-入站, 2-出站
 */
public class PassData {
    @JsonProperty("SN")
    private String sn;
    
    @JsonProperty("LineCode")
    private String lineCode;
    
    @JsonProperty("NodeCode")
    private String nodeCode;
    
    @JsonProperty("StationCode")
    private String stationCode;
    
    @JsonProperty("PassType")
    private int passType; // 1-入站, 2-出站
    
    @JsonProperty("PassTime")
    private String passTime; // 格式: "yyyy-MM-dd HH:mm:ss"
    
    /**
     * 默认构造函数
     */
    public PassData() {
    }
    
    /**
     * 全参数构造函数
     * 
     * @param sn 序列号
     * @param lineCode 产线代码
     * @param nodeCode 节点代码
     * @param stationCode 工站代码
     * @param passType 过站类型(1-入站, 2-出站)
     * @param passTime 过站时间(格式: "yyyy-MM-dd HH:mm:ss")
     */
    public PassData(String sn, String lineCode, String nodeCode, String stationCode, int passType, String passTime) {
        this.sn = sn;
        this.lineCode = lineCode;
        this.nodeCode = nodeCode;
        this.stationCode = stationCode;
        this.passType = passType;
        this.passTime = passTime;
    }
    
    /**
     * 创建入站数据对象
     * 
     * @param sn 序列号
     * @param lineCode 产线代码
     * @param nodeCode 节点代码
     * @param stationCode 工站代码
     * @return 入站数据对象
     */
    public static PassData createInPassData(String sn, String lineCode, String nodeCode, String stationCode) {
        String passTime = DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss");
        return new PassData(sn, lineCode, nodeCode, stationCode, 1, passTime);
    }
    
    /**
     * 创建出站数据对象
     * 
     * @param sn 序列号
     * @param lineCode 产线代码
     * @param nodeCode 节点代码
     * @param stationCode 工站代码
     * @return 出站数据对象
     */
    public static PassData createOutPassData(String sn, String lineCode, String nodeCode, String stationCode) {
        String passTime = DateUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss");
        return new PassData(sn, lineCode, nodeCode, stationCode, 2, passTime);
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
    
    public int getPassType() {
        return passType;
    }
    
    public void setPassType(int passType) {
        this.passType = passType;
    }
    
    public String getPassTime() {
        return passTime;
    }
    
    public void setPassTime(String passTime) {
        this.passTime = passTime;
    }
    
    /**
     * 将对象转换为JSON字符串，用于API调用
     * 
     * @return JSON字符串
     */
    public String toJsonString() {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(this);
        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
    }
}