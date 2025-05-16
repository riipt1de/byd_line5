package com.znh.siemens.model;

import java.util.ArrayList;
import java.util.List;

public class Station {
    private String name;
    private String process;
    private List<StationItem> items;
    
    public Station() {
        // 无参构造函数，用于Jackson反序列化
        this.items = new ArrayList<>();
    }
    
    public Station(String name,String process) {
        this.name = name;
        this.process = process;
        this.items = new ArrayList<>();
    }
    
    public void addItem(String name,String db, double offset, String dataType, int length) {
        items.add(new StationItem(name,db, offset, dataType, length));
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProcess() {
        return process;
    }

    public void setProcess(String process) {
        this.process = process;
    }

    public void setItems(List<StationItem> items) {
        this.items = items;
    }

    public String getName() {
        return name;
    }
    
    public List<StationItem> getItems() {
        return items;
    }
    
    @Override
    public String toString() {
        return name+" | "+process;
    }
}

