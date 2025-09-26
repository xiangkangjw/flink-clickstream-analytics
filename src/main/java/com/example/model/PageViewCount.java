package com.example.model;

public class PageViewCount {
    
    private String page;
    private long count;
    private long windowStart;
    private long windowEnd;
    
    public PageViewCount() {}
    
    public PageViewCount(String page, long count) {
        this.page = page;
        this.count = count;
    }
    
    public PageViewCount(String page, long count, long windowStart, long windowEnd) {
        this.page = page;
        this.count = count;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }
    
    public String getPage() {
        return page;
    }
    
    public void setPage(String page) {
        this.page = page;
    }
    
    public long getCount() {
        return count;
    }
    
    public void setCount(long count) {
        this.count = count;
    }
    
    public long getWindowStart() {
        return windowStart;
    }
    
    public void setWindowStart(long windowStart) {
        this.windowStart = windowStart;
    }
    
    public long getWindowEnd() {
        return windowEnd;
    }
    
    public void setWindowEnd(long windowEnd) {
        this.windowEnd = windowEnd;
    }
    
    public PageViewCount add(PageViewCount other) {
        if (this.page.equals(other.page)) {
            return new PageViewCount(this.page, this.count + other.count, this.windowStart, this.windowEnd);
        }
        throw new IllegalArgumentException("Cannot add PageViewCount for different pages");
    }
    
    @Override
    public String toString() {
        return String.format("PageViewCount{page='%s', count=%d, window=[%d-%d]}", 
                           page, count, windowStart, windowEnd);
    }
}