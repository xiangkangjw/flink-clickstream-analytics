package com.example.functions;

import com.example.model.PageViewCount;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public class WindowResultProcessor extends ProcessWindowFunction<PageViewCount, PageViewCount, String, TimeWindow> {
    
    @Override
    public void process(String key, Context context, 
                       Iterable<PageViewCount> elements, 
                       Collector<PageViewCount> out) {
        
        PageViewCount result = elements.iterator().next();
        
        // Set window information
        result.setWindowStart(context.window().getStart());
        result.setWindowEnd(context.window().getEnd());
        
        out.collect(result);
    }
}