package com.example.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClickEvent {
    
    @JsonProperty("page")
    private String page;
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("ip")
    private String ip;
    
    public ClickEvent() {}
    
    public ClickEvent(String page, String userId, long timestamp, String ip) {
        this.page = page;
        this.userId = userId;
        this.timestamp = timestamp;
        this.ip = ip;
    }
    
    public String getPage() {
        return page;
    }
    
    public void setPage(String page) {
        this.page = page;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getIp() {
        return ip;
    }
    
    public void setIp(String ip) {
        this.ip = ip;
    }
    
    @Override
    public String toString() {
        return String.format("ClickEvent{page='%s', userId='%s', timestamp=%d, ip='%s'}", 
                           page, userId, timestamp, ip);
    }
}