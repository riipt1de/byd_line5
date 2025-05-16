package com.znh.siemens.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public class StationItem {
    private String name;
    private String db;
    private double offset;
    private String dataType;
    private String itemType; //1.监听项  2.读取项   3.写入项  4.普通项
    private int length;
    private String result;
    @JsonIgnore
    private TextArea resultLabel;

    public StationItem() {
        // 无参构造函数，用于Jackson反序列化
    }

    public StationItem(String name,String db, double offset, String dataType, int length) {
        this.name = name;
        this.db = db;
        this.offset = offset;
        this.dataType = dataType;
        this.length = length;
        this.result = "";
        this.itemType = "4"; // 默认为普通项
    }

    public String getDb() {
        return db;
    }

    public void setDb(String db) {
        this.db = db;
    }

    // Getters
    public String getName() { return name; }
    public double getOffset() { return offset; }
    public String getDataType() { return dataType; }
    public String getItemType() { return itemType; }
    public int getLength() { return length; }
    public String getResult() { return result; }

    public TextArea getResultLabel() {
        return resultLabel;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setOffset(double offset) {
        this.offset = offset;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public void setResultLabel(TextArea resultLabel) {
        this.resultLabel = resultLabel;
    }

    // Setter for result
    public void setResult(String result) { this.result = result; }
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

}