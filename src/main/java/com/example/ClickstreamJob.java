package com.example;

import com.example.config.JobConfig;
import com.example.functions.ClickEventDeserializer;
import com.example.functions.PageViewAggregator;
import com.example.functions.WindowResultProcessor;
import com.example.model.ClickEvent;
import com.example.model.PageViewCount;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClickstreamJob {

    private static final Logger LOG = LoggerFactory.getLogger(ClickstreamJob.class);

    public static void main(String[] args) throws Exception {

        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(JobConfig.PARALLELISM);

        // Enable checkpointing
        env.enableCheckpointing(JobConfig.CHECKPOINT_INTERVAL_MS);

        LOG.info("Starting Clickstream Analytics Job");

        // Create Kafka source
        KafkaSource<ClickEvent> kafkaSource = KafkaSource.<ClickEvent>builder()
                .setBootstrapServers(JobConfig.KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(JobConfig.KAFKA_TOPIC)
                .setGroupId(JobConfig.KAFKA_GROUP_ID)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new ClickEventDeserializer())
                .build();

        // Create the data stream from Kafka
        DataStream<ClickEvent> clickEvents = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
                .name("Click Events");

        // Process the stream: convert to PageViewCount and aggregate
        DataStream<PageViewCount> pageViews = clickEvents
                .filter(event -> event.getPage() != null && !event.getPage().equals("invalid"))
                .map(event -> new PageViewCount(event.getPage(), 1))
                .name("Convert to PageView")
                .keyBy(new KeySelector<PageViewCount, String>() {
                    @Override
                    public String getKey(PageViewCount pageView) {
                        return pageView.getPage();
                    }
                })
                .window(SlidingProcessingTimeWindows.of(
                        Time.minutes(JobConfig.WINDOW_SIZE_MINUTES),
                        Time.seconds(JobConfig.WINDOW_SLIDE_SECONDS)))
                .aggregate(new PageViewAggregator(), new WindowResultProcessor())
                .name("Aggregate Page Views");

        // Print results
        pageViews.print("Page View Counts").name("Print Results");

        // Execute the job
        LOG.info("Executing job: {}", JobConfig.JOB_NAME);
        env.execute(JobConfig.JOB_NAME);
    }
}