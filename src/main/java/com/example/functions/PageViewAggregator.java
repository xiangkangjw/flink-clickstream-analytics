package com.example.functions;

import com.example.model.PageViewCount;
import org.apache.flink.api.common.functions.AggregateFunction;

public class PageViewAggregator implements AggregateFunction<PageViewCount, PageViewCount, PageViewCount> {
    
    @Override
    public PageViewCount createAccumulator() {
        return new PageViewCount("", 0);
    }
    
    @Override
    public PageViewCount add(PageViewCount value, PageViewCount accumulator) {
        if (accumulator.getPage().isEmpty()) {
            // First element
            return new PageViewCount(value.getPage(), value.getCount(), 
                                   value.getWindowStart(), value.getWindowEnd());
        }
        return accumulator.add(value);
    }
    
    @Override
    public PageViewCount getResult(PageViewCount accumulator) {
        return accumulator;
    }
    
    @Override
    public PageViewCount merge(PageViewCount a, PageViewCount b) {
        if (a.getPage().isEmpty()) return b;
        if (b.getPage().isEmpty()) return a;
        return a.add(b);
    }
}