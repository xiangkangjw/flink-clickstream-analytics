# Real-Time Clickstream Analytics - Technical Requirements Document (TRD)

## 1. System Architecture Overview

### 1.1 High-Level Architecture

```
[Event Generator] → [Kafka Cluster (KRaft)] → [Flink Processing] → [Output Sinks]
                                ↓
                          [Monitoring & Logs]
```

### 1.2 Core Components

- **Event Generator**: Java application simulating user clickstreams
- **Kafka Cluster**: Message broker with KRaft consensus (no ZooKeeper)
- **Apache Flink**: Stream processing engine for real-time analytics
- **Output Sinks**: Console, Kafka topics, optional REST API
- **Monitoring Stack**: Logging and metrics collection

## 2. Technology Stack

### 2.1 Required Technologies

| Component         | Technology                | Version  | Justification                                   |
| ----------------- | ------------------------- | -------- | ----------------------------------------------- |
| Message Broker    | Apache Kafka              | 3.5+     | Industry standard, KRaft mode support           |
| Stream Processing | Apache Flink              | 1.17+    | Advanced windowing, exactly-once semantics      |
| Event Generation  | Java                      | 11+      | Consistent with Flink stack, enterprise support |
| Data Format       | Apache Avro               | 1.11+    | Schema evolution, compact serialization         |
| Container Runtime | Docker                    | 20.10+   | Easy deployment and environment consistency     |
| Build Tool        | Docker Compose            | 2.0+     | Multi-container orchestration                   |
| Monitoring        | Kafka JMX + Flink Metrics | Built-in | Native performance monitoring                   |

### 2.2 Development Environment

```bash
# Required Software
- Docker & Docker Compose
- Java 11+ (for Flink development and event generation)
- Git (version control)
- IDE (IntelliJ IDEA/VS Code recommended)

# Hardware Requirements
- CPU: 4+ cores
- RAM: 8GB minimum, 16GB recommended
- Storage: 10GB available space
- Network: High-speed internet for initial downloads
```

## 3. Detailed Component Specifications

### 3.1 Kafka Cluster Configuration

#### 3.1.1 KRaft Mode Setup

```properties
# server.properties (KRaft Controller + Broker)
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@localhost:9093
listeners=PLAINTEXT://localhost:9092,CONTROLLER://localhost:9093
controller.listener.names=CONTROLLER
inter.broker.listener.name=PLAINTEXT
log.dirs=/tmp/kraft-combined-logs
num.network.threads=8
num.io.threads=8
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600
```

#### 3.1.2 Topic Configuration

```bash
# Primary Topics
clickstream-events:
  - Partitions: 6
  - Replication Factor: 1 (single node)
  - Retention: 2 hours
  - Compression: snappy

analytics-results:
  - Partitions: 3
  - Replication Factor: 1
  - Retention: 24 hours
  - Compression: gzip

# Topic Creation Commands
kafka-topics.sh --create --topic clickstream-events \
  --partitions 6 --replication-factor 1 \
  --config retention.ms=7200000 \
  --config compression.type=snappy

kafka-topics.sh --create --topic analytics-results \
  --partitions 3 --replication-factor 1 \
  --config retention.ms=86400000 \
  --config compression.type=gzip
```

### 3.2 Event Generator Specifications

#### 3.2.1 Event Schema (Avro)

```json
{
  "type": "record",
  "name": "ClickstreamEvent",
  "namespace": "analytics.events",
  "fields": [
    { "name": "user_id", "type": "string" },
    { "name": "session_id", "type": "string" },
    {
      "name": "event_type",
      "type": {
        "type": "enum",
        "name": "EventType",
        "symbols": ["VIEW", "CLICK", "ADD_TO_CART", "PURCHASE", "SEARCH"]
      }
    },
    { "name": "page_url", "type": "string" },
    { "name": "product_id", "type": ["null", "string"], "default": null },
    {
      "name": "timestamp",
      "type": { "type": "long", "logicalType": "timestamp-millis" }
    },
    { "name": "user_agent", "type": "string" },
    { "name": "ip_address", "type": "string" },
    { "name": "referrer", "type": ["null", "string"], "default": null },
    { "name": "session_duration", "type": ["null", "int"], "default": null }
  ]
}
```

#### 3.2.2 Event Generation Logic

```java
// Key Requirements for Event Generator
public class ClickstreamGenerator {
    private List<String> users;
    private List<String> pages;
    private List<String> products;

    public ClickstreamGenerator() {
        this.users = generateUserPool(1000);     // 1K concurrent users
        this.pages = loadPageCatalog(100);       // 100 different pages
        this.products = loadProductCatalog(500); // 500 products
    }

    public void generateEventStream(int eventsPerSecond) {
        // Generate realistic user behavior patterns
        // 70% page views, 20% clicks, 7% add-to-cart, 3% purchases
        // Follow realistic user journey patterns
        // Maintain session consistency (15-45 min sessions)
        // Implement realistic peak/off-peak traffic patterns
    }
}
```

#### 3.2.3 Partitioning Strategy

```java
// Partition by user_id for session consistency
public String getPartitionKey(ClickEvent event) {
    return event.getUserId();
}

// Alternative: Partition by page_url for processing locality
public String getPartitionKeyByPage(ClickEvent event) {
    return event.getPageUrl();
}
```

### 3.3 Apache Flink Job Specifications

#### 3.3.1 Flink Job Architecture

```java
// Main Processing Topology
DataStream<ClickstreamEvent> source = env
    .addSource(new FlinkKafkaConsumer<>("clickstream-events",
                                        new AvroDeserializationSchema<>(),
                                        kafkaProps));

// Key by page URL for analytics
KeyedStream<ClickstreamEvent, String> keyedStream = source
    .keyBy(event -> event.getPageUrl());

// Sliding Window Analytics
DataStream<PageAnalytics> analytics = keyedStream
    .window(SlidingEventTimeWindows.of(
        Time.minutes(5),    // Window size
        Time.seconds(10)    // Slide interval
    ))
    .aggregate(new PageAnalyticsAggregator());

// Output to multiple sinks
analytics.addSink(new FlinkKafkaProducer<>("analytics-results",
                                           new AvroSerializationSchema<>(),
                                           kafkaProps));
analytics.print("ANALYTICS");
```

#### 3.3.2 Window Configuration

```java
// Sliding Window Specifications
SlidingEventTimeWindows slidingWindow = SlidingEventTimeWindows.of(
    Time.minutes(5),     // Window size: 5 minutes of data
    Time.seconds(10)     // Slide interval: Update every 10 seconds
);

// Watermark Strategy for handling late events
WatermarkStrategy<ClickstreamEvent> watermarkStrategy =
    WatermarkStrategy
        .<ClickstreamEvent>forBoundedOutOfOrderness(Duration.ofSeconds(30))
        .withTimestampAssigner((event, timestamp) -> event.getTimestamp());
```

#### 3.3.3 Analytics Aggregation Logic

```java
public class PageAnalyticsAggregator
    implements AggregateFunction<ClickstreamEvent, PageAnalyticsAccumulator, PageAnalytics> {

    @Override
    public PageAnalyticsAccumulator createAccumulator() {
        return new PageAnalyticsAccumulator();
    }

    @Override
    public PageAnalyticsAccumulator add(ClickstreamEvent event,
                                       PageAnalyticsAccumulator acc) {
        // Increment counters based on event type
        // Track unique users
        // Calculate engagement metrics
        // Update timestamp bounds
        return acc;
    }

    @Override
    public PageAnalytics getResult(PageAnalyticsAccumulator acc) {
        return new PageAnalytics(
            acc.pageUrl,
            acc.viewCount,
            acc.uniqueUsers.size(),
            acc.clickCount,
            acc.conversionCount,
            acc.windowStart,
            acc.windowEnd
        );
    }
}
```

### 3.4 Output Schema

#### 3.4.1 Analytics Result Schema (Avro)

```json
{
  "type": "record",
  "name": "PageAnalytics",
  "namespace": "analytics.results",
  "fields": [
    { "name": "page_url", "type": "string" },
    {
      "name": "window_start",
      "type": { "type": "long", "logicalType": "timestamp-millis" }
    },
    {
      "name": "window_end",
      "type": { "type": "long", "logicalType": "timestamp-millis" }
    },
    { "name": "view_count", "type": "long" },
    { "name": "unique_users", "type": "int" },
    { "name": "click_count", "type": "long" },
    { "name": "conversion_count", "type": "long" },
    { "name": "engagement_rate", "type": "double" },
    { "name": "bounce_rate", "type": "double" },
    { "name": "avg_session_duration", "type": "double" }
  ]
}
```

## 4. Infrastructure Requirements

### 4.1 Docker Compose Configuration

```yaml
version: '3.8'
services:
  kafka:
    image: confluentinc/cp-kafka:7.4.0
    ports:
      - "9092:9092"
      - "9093:9093"
    environment:
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_NODE_ID: 1
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka:9093'
      KAFKA_LISTENERS: 'PLAINTEXT://kafka:29092,CONTROLLER://kafka:9093,PLAINTEXT_HOST://0.0.0.0:9092'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_LOG_DIRS: '/tmp/kraft-combined-logs'
      KAFKA_HEAP_OPTS: '-Xmx1G -Xms1G'
    volumes:
      - kafka_data:/tmp/kraft-combined-logs

  flink-jobmanager:
    image: flink:1.18.1
    ports:
      - "8081:8081"
    command: jobmanager
    environment:
      - FLINK_PROPERTIES=jobmanager.rpc.address: flink-jobmanager
    volumes:
      - ./flink-jobs:/opt/flink/usrlib

  flink-taskmanager:
    image: flink:1.18.1
    depends_on:
      - flink-jobmanager
    command: taskmanager
    environment:
      - FLINK_PROPERTIES=jobmanager.rpc.address: flink-jobmanager
                         taskmanager.memory.process.size: 2g
                         taskmanager.numberOfTaskSlots: 4
    volumes:
      - ./flink-jobs:/opt/flink/usrlib

  event-generator:
    image: flink:1.18.1
    depends_on:
      - kafka
    environment:
      - KAFKA_BROKERS=kafka:29092
      - EVENTS_PER_SECOND=100
      - EVENT_DURATION_HOURS=24
    volumes:
      - ./flink-jobs:/opt/flink/usrlib
    command: >
      bash -c "java -cp /opt/flink/usrlib/flink-clickstream-1.0-SNAPSHOT.jar
      com.example.EventProducer kafka:29092 click-stream 10 60"

volumes:
  kafka_data:
```

### 4.2 Resource Allocation

```yaml
# Resource Limits per Service
kafka:
  memory: 2GB
  cpu: 1.5 cores
  disk: 5GB

flink-jobmanager:
  memory: 1GB
  cpu: 0.5 cores
  disk: 1GB

flink-taskmanager:
  memory: 2GB
  cpu: 2 cores
  disk: 1GB

event-generator:
  memory: 512MB
  cpu: 0.5 cores
  disk: 100MB

# Total System Requirements
total_memory: 6GB
total_cpu: 4.5 cores
total_disk: 10GB
```

## 5. Performance Requirements

### 5.1 Throughput Specifications

```
Event Generation:
- Target: 1000 events/second
- Burst: 5000 events/second (5 minutes)
- Sustainable: 500 events/second (continuous)

Kafka Processing:
- Message throughput: 10MB/second
- Consumer lag: < 1000 messages
- Topic partition load balancing: ±10% variance

Flink Processing:
- Window processing latency: < 200ms
- Checkpoint frequency: 30 seconds
- Parallelism: 4-8 operators
```

### 5.2 Latency Requirements

```
End-to-End Latency Targets:
- Event generation → Kafka: < 10ms (99th percentile)
- Kafka → Flink consumption: < 50ms (95th percentile)
- Flink window processing: < 200ms (95th percentile)
- Result output: < 100ms (99th percentile)
- Total end-to-end: < 500ms (95th percentile)
```

## 6. Monitoring & Observability

### 6.1 Key Metrics to Track

```
Kafka Metrics:
- kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec
- kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec
- kafka.controller:type=KafkaController,name=OfflinePartitionsCount
- kafka.server:type=ReplicaManager,name=LeaderCount

Flink Metrics:
- jobmanager.numRunningJobs
- taskmanager.Status.JVM.Memory.Heap.Used
- <job_name>.numRecordsInPerSec
- <job_name>.latency
- <job_name>.numRecordsOutPerSec
- <job_name>.checkpoint.duration

Application Metrics:
- events_generated_per_second
- processing_errors_count
- window_calculations_completed
- output_sink_success_rate
```

### 6.2 Logging Configuration

```yaml
# Structured Logging Format
logging:
  level:
    org.apache.kafka: INFO
    org.apache.flink: INFO
    analytics.clickstream: DEBUG

  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg %X{correlationId}%n"

  appenders:
    - type: console
    - type: file
      currentLogFilename: ./logs/clickstream-analytics.log
      archivedLogFilenamePattern: ./logs/clickstream-analytics-%d{yyyy-MM-dd}.log.gz
      maxFileSize: 100MB
      maxHistory: 7
```

## 7. Data Quality & Validation

### 7.1 Schema Registry Integration

```yaml
schema-registry:
  image: confluentinc/cp-schema-registry:7.4.0
  depends_on:
    - kafka
  ports:
    - "8082:8082"
  environment:
    SCHEMA_REGISTRY_HOST_NAME: schema-registry
    SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: "kafka:29092"
    SCHEMA_REGISTRY_LISTENERS: http://0.0.0.0:8082
```

### 7.2 Data Validation Rules

```java
// Event Validation Rules
public class EventValidator {
    public boolean isValid(ClickstreamEvent event) {
        return event.getUserId() != null &&
               event.getSessionId() != null &&
               event.getPageUrl() != null &&
               event.getTimestamp() > 0 &&
               event.getEventType() != null &&
               isValidUrl(event.getPageUrl()) &&
               isValidTimestamp(event.getTimestamp());
    }

    // Additional validation methods...
}
```

## 8. Security Considerations

### 8.1 Basic Security Setup

```properties
# Kafka Security Configuration (Development)
security.protocol=PLAINTEXT
# In production, use SASL_SSL with proper certificates

# Flink Security
security.ssl.enabled=false
# In production, enable SSL and authentication
```

### 8.2 Data Privacy

```java
// PII Handling
public class EventSanitizer {
    public ClickstreamEvent sanitize(ClickstreamEvent event) {
        // Hash user_id for privacy
        // Remove or hash IP addresses
        // Sanitize user agent strings
        return sanitizedEvent;
    }
}
```

## 9. Testing Strategy

### 9.1 Unit Testing Requirements

```
Component Coverage Requirements:
- Event Generator: 90%+ line coverage
- Flink Aggregators: 95%+ line coverage
- Kafka Producers/Consumers: 85%+ line coverage
- Validation Logic: 100% line coverage

Test Categories:
- Unit tests for core business logic
- Integration tests for Kafka-Flink pipeline
- Performance tests for throughput/latency
- End-to-end scenario tests
```

### 9.2 Integration Testing

```bash
# Test Scenarios
1. Normal load testing (100-1000 events/sec)
2. Burst load testing (5000 events/sec for 5 minutes)
3. Network partition simulation
4. Kafka broker restart scenarios
5. Flink job restart and recovery
6. Schema evolution testing
7. Window boundary correctness testing
```

## 10. Deployment & Operations

### 10.1 Startup Sequence

```bash
# Deployment Steps
1. Start Kafka cluster with KRaft
2. Wait for Kafka readiness (health check)
3. Create required topics with configurations
4. Start Schema Registry (optional)
5. Deploy Flink job (submit JAR)
6. Start event generator
7. Verify end-to-end data flow
8. Enable monitoring dashboards
```

### 10.2 Health Checks

```yaml
# Health Check Endpoints
kafka_health:
  endpoint: /health
  timeout: 5s
  interval: 30s

flink_health:
  endpoint: http://flink-jobmanager:8081/overview
  timeout: 10s
  interval: 15s

event_generator_health:
  endpoint: /metrics/health
  timeout: 3s
  interval: 30s
```

## 11. Development Guidelines

### 11.1 Code Organization

```
project-root/
├── docker-compose.yml
├── README.md
├── event-generator/
│   ├── src/main/java/
│   ├── pom.xml
├── flink-jobs/
│   ├── src/main/java/
│   ├── pom.xml
│   └── target/
├── kafka-config/
│   ├── server.properties
│   ├── topics.sh
│   └── schemas/
├── monitoring/
│   ├── prometheus.yml
│   └── grafana/
└── docs/
    ├── setup.md
    ├── troubleshooting.md
    └── architecture.md
```

### 11.2 Configuration Management

```yaml
# Environment-specific configurations
development:
  kafka:
    partitions: 3
    replication_factor: 1
  flink:
    parallelism: 2
  event_generator:
    events_per_second: 100

production:
  kafka:
    partitions: 12
    replication_factor: 3
  flink:
    parallelism: 8
  event_generator:
    events_per_second: 1000
```

---

_This Technical Requirements Document provides the detailed specifications needed to implement a production-ready real-time clickstream analytics system using modern stream processing technologies._
