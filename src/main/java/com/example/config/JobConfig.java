package com.example.config;

public class JobConfig {

    // Kafka configuration
    public static final String KAFKA_BOOTSTRAP_SERVERS = "kafka:9092";
    public static final String KAFKA_TOPIC = "click-stream";
    public static final String KAFKA_GROUP_ID = "flink-consumer";

    // Job configuration
    public static final String JOB_NAME = "Clickstream Analytics";
    public static final int PARALLELISM = 6;

    // Window configuration
    public static final long WINDOW_SIZE_MINUTES = 5;
    public static final long WINDOW_SLIDE_SECONDS = 10;

    // Checkpointing
    public static final long CHECKPOINT_INTERVAL_MS = 10000;

    private JobConfig() {
        // Utility class
    }
}