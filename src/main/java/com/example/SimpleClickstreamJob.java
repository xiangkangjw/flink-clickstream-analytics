package com.example;

import com.example.config.JobConfig;
import com.example.functions.ClickEventDeserializer;
import com.example.model.ClickEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleClickstreamJob {
    
    private static final Logger LOG = LoggerFactory.getLogger(SimpleClickstreamJob.class);
    
    public static void main(String[] args) throws Exception {
        
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(JobConfig.PARALLELISM);
        
        LOG.info("Starting Simple Clickstream Job");
        
        // Create Kafka source
        KafkaSource<ClickEvent> kafkaSource = KafkaSource.<ClickEvent>builder()
                .setBootstrapServers(JobConfig.KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(JobConfig.KAFKA_TOPIC)
                .setGroupId(JobConfig.KAFKA_GROUP_ID)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new ClickEventDeserializer())
                .build();
        
        // Create the data stream from Kafka and just print each event
        DataStream<ClickEvent> clickEvents = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
                .name("Click Events");
        
        // Simply print each event
        clickEvents.print("RECEIVED EVENT").name("Print Events");
        
        // Execute the job
        LOG.info("Executing simple job");
        env.execute("Simple Clickstream Job");
    }
}