package com.tmobile.pacbot.azure.inventory.vo;

import java.util.Map;

public class MySQLServerVH extends AzureVH {
    private String name;
    private String type;
    private String location;
    private Map<String, Object> propertiesMap;
    private Map<String, Object> skuMap;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Map<String, Object> getPropertiesMap() {
        return propertiesMap;
    }

    public void setPropertiesMap(Map<String, Object> propertiesMap) {
        this.propertiesMap = propertiesMap;
    }

    public Map<String, Object> getSkuMap() {
        return skuMap;
    }

    public void setSkuMap(Map<String, Object> skuMap) {
        this.skuMap = skuMap;
    }

}
