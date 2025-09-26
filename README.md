# Flink Clickstream Analytics Example

A real-time clickstream analytics demo using Apache Flink and Kafka. This project demonstrates how to process streaming data with windowed aggregations to analyze website page views.

## Overview

This project implements a complete streaming analytics pipeline:

1. **Event Producer**: Generates synthetic clickstream events (user ID, page URL, timestamp)
2. **Kafka**: Acts as the message broker for streaming data
3. **Flink Job**: Processes events in real-time using sliding windows to count page views
4. **Monitoring**: Web UIs for observing Kafka topics and Flink job execution

## Architecture

```
EventProducer → Kafka Topic → Flink Job (Windowed Aggregation) → Console Output
```

- **5-minute sliding windows** with 10-second slide intervals
- **Page view counting** grouped by page URL
- **Real-time processing** with sub-second latency

## Quick Start

1. **Start the infrastructure**:
   ```bash
   docker-compose up -d
   ```

2. **Build and deploy the Flink job**:
   ```bash
   mvn clean package
   docker exec jobmanager ./bin/flink run /opt/flink/usrlib/flink-clickstream-1.0-SNAPSHOT.jar
   ```

3. **Generate test data**:
   ```bash
   docker exec jobmanager java -cp /opt/flink/usrlib/flink-clickstream-1.0-SNAPSHOT.jar com.example.EventProducer kafka:9092 click-stream 10 60
   ```

4. **Monitor the results**:
   ```bash
   docker logs taskmanager --tail 20 | grep -E "(RECEIVED EVENT|PageViewCount|\>)"
   ```

## Monitoring

- **Flink Web UI**: http://localhost:8081 - Monitor job status, metrics, and performance
- **Kafka UI**: http://localhost:8080 - View topics, messages, and consumer groups

## Project Structure

- `src/main/java/com/example/ClickstreamJob.java` - Main Flink streaming application
- `src/main/java/com/example/EventProducer.java` - Kafka event producer for test data
- `src/main/java/com/example/model/` - Data models (ClickEvent, PageViewCount)
- `src/main/java/com/example/functions/` - Custom Flink functions and deserializers
- `docker-compose.yml` - Infrastructure setup (Kafka, Flink, monitoring)
- `jars/` - Kafka connector dependencies

## Technology Stack

- **Apache Flink 1.17** - Stream processing framework
- **Apache Kafka** - Message broker
- **Docker & Docker Compose** - Containerized deployment
- **Maven** - Build management
- **Java 11+** - Programming language

## Use Cases

This example demonstrates patterns for:
- Real-time analytics dashboards
- Website traffic monitoring
- User behavior analysis
- E-commerce clickstream processing
- IoT sensor data aggregation