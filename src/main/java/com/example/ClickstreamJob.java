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
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import java.time.Duration;
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

                // Create watermark strategy with bounded out-of-orderness
                WatermarkStrategy<ClickEvent> watermarkStrategy = WatermarkStrategy
                                .<ClickEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                                .withTimestampAssigner((event, timestamp) -> event.getTimestamp());

                // Create the data stream from Kafka
                DataStream<ClickEvent> clickEvents = env
                                .fromSource(kafkaSource, watermarkStrategy, "Kafka Source")
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
                                // Using event-time windows
                                .window(SlidingEventTimeWindows.of(
                                                Time.minutes(JobConfig.WINDOW_SIZE_MINUTES),
                                                Time.seconds(JobConfig.WINDOW_SLIDE_SECONDS)))
                                .aggregate(new PageViewAggregator(), new WindowResultProcessor())
                                .name("Aggregate Page Views");

                // Log results with enhanced output
                pageViews.map(pv -> {
                    LOG.info("==> WINDOW RESULT: {} | Page: {} | Count: {} | Window: {}-{}",
                        pv, pv.getPage(), pv.getCount(),
                        new java.util.Date(pv.getWindowStart()),
                        new java.util.Date(pv.getWindowEnd()));
                    return pv;
                }).name("Log Window Results");

                // Write results to file
                FileSink<String> fileSink = FileSink
                                .forRowFormat(new Path("/tmp/flink-output"), new SimpleStringEncoder<String>("UTF-8"))
                                .build();

                pageViews
                                .map(pv -> pv.toString())
                                .sinkTo(fileSink)
                                .name("File Sink");

                // Print results to stdout
                pageViews.print("Page View Counts").name("Print Results");

                // Execute the job
                LOG.info("Executing job: {}", JobConfig.JOB_NAME);
                env.execute(JobConfig.JOB_NAME);
        }
}