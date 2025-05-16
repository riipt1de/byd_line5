package com.znh.siemens.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * PLC账号配置类
 * 用于存储不同PLC的登录账号信息
 */
@Data
public class PlcAccount {
    /**
     * PLC标识符，例如plc1、plc2等
     */
    private String plcId;
    
    /**
     * PLC登录用户名
     */
    private String username;
    
    /**
     * PLC登录密码
     */
    private String password;
    
    /**
     * 是否为默认账号
     */
    @JsonProperty("isDefault")
    private boolean isDefault;
    
    /**
     * 描述信息
     */
    private String description;
    
    public PlcAccount() {
    }
    
    public PlcAccount(String plcId, String username, String password) {
        this.plcId = plcId;
        this.username = username;
        this.password = password;
    }
    
    public PlcAccount(String plcId, String username, String password, boolean isDefault, String description) {
        this.plcId = plcId;
        this.username = username;
        this.password = password;
        this.isDefault = isDefault;
        this.description = description;
    }
} 