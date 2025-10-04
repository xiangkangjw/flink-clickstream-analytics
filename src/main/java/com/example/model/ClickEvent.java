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

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("user_agent")
    private String userAgent;

    @JsonProperty("referrer")
    private String referrer;

    @JsonProperty("session_duration")
    private Integer sessionDuration;

    public ClickEvent() {}
    
    public ClickEvent(String page, String userId, long timestamp, String ip) {
        this.page = page;
        this.userId = userId;
        this.timestamp = timestamp;
        this.ip = ip;
    }

    public ClickEvent(String page, String userId, long timestamp, String ip, String sessionId,
                     String eventType, String productId, String userAgent, String referrer, Integer sessionDuration) {
        this.page = page;
        this.userId = userId;
        this.timestamp = timestamp;
        this.ip = ip;
        this.sessionId = sessionId;
        this.eventType = eventType;
        this.productId = productId;
        this.userAgent = userAgent;
        this.referrer = referrer;
        this.sessionDuration = sessionDuration;
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public Integer getSessionDuration() {
        return sessionDuration;
    }

    public void setSessionDuration(Integer sessionDuration) {
        this.sessionDuration = sessionDuration;
    }

    @Override
    public String toString() {
        return String.format("ClickEvent{page='%s', userId='%s', timestamp=%d, ip='%s', sessionId='%s', eventType='%s', productId='%s', userAgent='%s', referrer='%s', sessionDuration=%d}",
                           page, userId, timestamp, ip, sessionId, eventType, productId, userAgent, referrer, sessionDuration);
    }
}