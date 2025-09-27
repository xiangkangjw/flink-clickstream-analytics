# Apache Flink Tutorial: Real-Time Clickstream Analytics

This tutorial teaches Apache Flink stream processing through building a real-time clickstream analytics system. Each lesson builds upon the previous one, progressing from basic concepts to advanced windowing and analytics.

## Prerequisites

- Java 11+ installed
- Docker and Docker Compose
- Basic understanding of Java programming
- Familiarity with command line

## Learning Path Overview

| Lesson | Topic | Duration | Skills Learned |
|--------|-------|----------|----------------|
| 1 | Setting Up Flink Environment | 30 min | Docker, Flink cluster basics |
| 2 | Basic Data Streams | 45 min | DataStream API, sources, sinks |
| 3 | Event Processing & Serialization | 60 min | Kafka integration, JSON/Avro |
| 4 | Windowing & Aggregations | 75 min | Time windows, aggregation functions |
| 5 | Advanced Analytics | 90 min | Complex event processing, multiple outputs |
| 6 | Production Considerations | 45 min | Monitoring, scaling, error handling |

---

## Lesson 1: Setting Up Your Flink Environment

**Goal**: Understand Flink cluster architecture and get a working environment running.

### 1.1 Understanding Flink Architecture

Flink operates as a distributed system with two main components:

- **JobManager**: Coordinates the cluster, schedules tasks, manages checkpoints
- **TaskManager**: Executes the actual data processing tasks

```
┌─────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ JobManager  │◄──►│ TaskManager 1   │    │ TaskManager 2   │
│             │    │ (Task Slots: 4) │    │ (Task Slots: 4) │
│ - Scheduler │    │ - Operators     │    │ - Operators     │
│ - Coordinator│   │ - State         │    │ - State         │
└─────────────┘    └─────────────────┘    └─────────────────┘
```

### 1.2 Starting Your First Flink Cluster

```bash
# Start the infrastructure
docker-compose up -d

# Verify services are running
docker ps

# Check Flink Web UI
open http://localhost:8081
```

### 1.3 Exploring the Flink Web UI

Navigate to `http://localhost:8081` and explore:

1. **Overview**: Cluster status and resource utilization
2. **Running Jobs**: Currently executing Flink applications
3. **Completed Jobs**: Job history and execution details
4. **Task Managers**: Available processing slots

### 1.4 Your First Flink Job Submission

```bash
# Build the project
mvn clean package

# Submit the clickstream job
docker exec jobmanager ./bin/flink run /opt/flink/usrlib/flink-clickstream-1.0-SNAPSHOT.jar
```

**Exercise 1.1**: Start your environment and submit the job. Observe the job in the Web UI and identify:
- How many task slots are available?
- What is the job's parallelism level?
- Which operators are running?

---

## Lesson 2: Understanding Data Streams

**Goal**: Learn the fundamental DataStream API and basic stream operations.

### 2.1 The DataStream API Basics

In Flink, everything starts with a `DataStream`. Think of it as an unbounded sequence of events flowing through your application.

```java
// Basic DataStream creation
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

// From a collection (for testing)
DataStream<String> stream = env.fromElements("event1", "event2", "event3");

// From a socket (for debugging)
DataStream<String> socketStream = env.socketTextStream("localhost", 9999);
```

### 2.2 Examining Our Event Model

Look at `src/main/java/com/example/model/ClickEvent.java`:

```java
public class ClickEvent {
    private String userId;
    private String pageUrl;
    private String eventType;
    private long timestamp;
    private String sessionId;
    private String ipAddress;
    
    // This represents a single user interaction
    // userId: identifies the user
    // pageUrl: which page they interacted with
    // timestamp: when it happened (crucial for windowing)
}
```

### 2.3 Basic Stream Transformations

```java
DataStream<ClickEvent> events = // ... source
        
// Filter events - only page views
DataStream<ClickEvent> pageViews = events
    .filter(event -> "VIEW".equals(event.getEventType()));

// Transform events - extract page URLs
DataStream<String> pages = events
    .map(event -> event.getPageUrl());

// FlatMap - split session events
DataStream<String> sessionPages = events
    .flatMap((event, out) -> {
        if (event.getSessionId() != null) {
            out.collect(event.getPageUrl());
        }
    });
```

### 2.4 Understanding Parallelism

Flink processes streams in parallel across multiple task slots:

```java
// Set global parallelism
env.setParallelism(4);

// Set operator-specific parallelism
events.map(event -> event.getPageUrl())
      .setParallelism(2);  // This operator runs with parallelism 2
```

**Exercise 2.1**: Create a simple Flink job that:
1. Creates a DataStream from a list of page URLs
2. Filters out URLs containing "/admin"
3. Maps each URL to its length
4. Prints the results

**Exercise 2.2**: Examine `src/main/java/com/example/ClickstreamJob.java` and identify:
- Where is the source DataStream created?
- What transformations are applied?
- How is parallelism configured?

---

## Lesson 3: Event Processing & Kafka Integration

**Goal**: Learn to consume real-time data from Kafka and handle serialization.

### 3.1 Understanding Event Time vs Processing Time

Critical concept in stream processing:

- **Event Time**: When the event actually occurred (timestamp in the event)
- **Processing Time**: When Flink processes the event
- **Ingestion Time**: When the event enters Flink

```java
// Configure event time
env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

// Assign timestamps and watermarks
DataStream<ClickEvent> timestampedStream = sourceStream
    .assignTimestampsAndWatermarks(
        WatermarkStrategy.<ClickEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
            .withTimestampAssigner((event, timestamp) -> event.getTimestamp())
    );
```

### 3.2 Kafka Source Configuration

Examine `src/main/java/com/example/ClickstreamJob.java`:

```java
// Kafka source properties
Properties kafkaProps = new Properties();
kafkaProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
kafkaProps.put(ConsumerConfig.GROUP_ID_CONFIG, "clickstream-analytics");

// Create Kafka source
FlinkKafkaConsumer<ClickEvent> kafkaSource = new FlinkKafkaConsumer<>(
    JobConfig.KAFKA_TOPIC,  // "click-stream"
    new ClickEventDeserializationSchema(),  // Custom deserializer
    kafkaProps
);
```

### 3.3 Understanding Deserialization

Look at `src/main/java/com/example/functions/ClickEventDeserializationSchema.java`:

```java
public class ClickEventDeserializationSchema implements DeserializationSchema<ClickEvent> {
    private ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public ClickEvent deserialize(byte[] message) throws IOException {
        // Convert JSON bytes to ClickEvent object
        return objectMapper.readValue(message, ClickEvent.class);
    }
    
    @Override
    public TypeInformation<ClickEvent> getProducedType() {
        return TypeInformation.of(ClickEvent.class);
    }
}
```

### 3.4 Generating Test Data

Start the event producer to generate realistic clickstream data:

```bash
# Generate 10 events per second for 60 seconds
docker exec jobmanager java -cp /opt/flink/usrlib/flink-clickstream-1.0-SNAPSHOT.jar \
  com.example.EventProducer kafka:9092 click-stream 10 60
```

Monitor the data flow:

```bash
# Watch Kafka UI
open http://localhost:8080

# Check task manager logs for processed events
docker logs taskmanager --tail 20 | grep -E "(RECEIVED EVENT|PageViewCount)"
```

**Exercise 3.1**: Modify the event producer to generate different types of events. Update the generation logic to:
- 60% page views
- 25% clicks  
- 10% add-to-cart
- 5% purchases

**Exercise 3.2**: Create a custom filter that only processes events from specific pages (e.g., "/home", "/products"). Count how many events are processed vs filtered out.

---

## Lesson 4: Windowing & Aggregations

**Goal**: Master time-based windowing and aggregation functions for real-time analytics.

### 4.1 Understanding Windows

Windows group events by time or count for batch processing within a stream:

```
Event Stream: ────●────●───●──●────●───●───●────●────●──→ time

Tumbling Window (5 sec):
[─────●────●───●──] [●────●───●───●────] [●────●──] 
    Window 1         Window 2         Window 3

Sliding Window (5 sec window, 2 sec slide):
[─────●────●───●──]
    [───●────●───●───●────]
          [─●───●───●────●────]
                [●────●────●──]
```

### 4.2 Window Types in Flink

```java
// Tumbling Window - non-overlapping, fixed size
keyedStream.window(TumblingEventTimeWindows.of(Time.minutes(5)))

// Sliding Window - overlapping windows
keyedStream.window(SlidingEventTimeWindows.of(
    Time.minutes(5),    // Window size
    Time.seconds(10)    // Slide interval  
))

// Session Window - based on inactivity gaps
keyedStream.window(EventTimeSessionWindows.withGap(Time.minutes(15)))
```

### 4.3 Our Sliding Window Implementation

Examine the windowing logic in `ClickstreamJob.java`:

```java
// Key events by page URL
KeyedStream<ClickEvent, String> keyedByPage = timestampedEvents
    .keyBy(event -> event.getPageUrl());

// Apply sliding window
DataStream<PageViewCount> pageViewCounts = keyedByPage
    .window(SlidingEventTimeWindows.of(
        Time.minutes(5),    // 5-minute windows
        Time.seconds(10)    // Update every 10 seconds
    ))
    .aggregate(new PageViewCountAggregator());
```

### 4.4 Aggregation Functions

Look at `src/main/java/com/example/functions/PageViewCountAggregator.java`:

```java
public class PageViewCountAggregator 
    implements AggregateFunction<ClickEvent, PageViewCountAggregator.Accumulator, PageViewCount> {
    
    // Accumulator holds the intermediate state
    public static class Accumulator {
        public String pageUrl;
        public long count = 0;
        public Set<String> uniqueUsers = new HashSet<>();
        public long windowStart;
        public long windowEnd;
    }
    
    @Override
    public Accumulator add(ClickEvent event, Accumulator acc) {
        acc.count++;
        acc.uniqueUsers.add(event.getUserId());
        return acc;
    }
    
    @Override
    public PageViewCount getResult(Accumulator acc) {
        return new PageViewCount(
            acc.pageUrl,
            acc.count,
            acc.uniqueUsers.size(),
            acc.windowStart,
            acc.windowEnd
        );
    }
}
```

### 4.5 Window Assignment and Watermarks

Watermarks handle late events and trigger window computations:

```java
WatermarkStrategy<ClickEvent> watermarkStrategy = WatermarkStrategy
    .<ClickEvent>forBoundedOutOfOrderness(Duration.ofSeconds(30))  // Handle 30s late events
    .withTimestampAssigner((event, timestamp) -> event.getTimestamp());
```

**Exercise 4.1**: Experiment with different window sizes:
1. Change to 2-minute windows with 5-second slides
2. Compare results with 10-minute tumbling windows
3. Observe how the output frequency changes

**Exercise 4.2**: Create a new aggregator that calculates:
- Total events per page
- Average events per user
- Most active user in the window
- Time of first and last event

**Exercise 4.3**: Implement session windows to group events by user activity gaps. Set a 10-minute inactivity threshold.

---

## Lesson 5: Advanced Analytics & Multiple Outputs

**Goal**: Build complex analytics with multiple data streams and output channels.

### 5.1 Creating Multiple Analytics Streams

```java
// Base event stream
DataStream<ClickEvent> events = env
    .addSource(kafkaSource)
    .assignTimestampsAndWatermarks(watermarkStrategy);

// Split into multiple analytics streams
// 1. Page popularity analytics
DataStream<PageViewCount> pageStats = events
    .keyBy(ClickEvent::getPageUrl)
    .window(SlidingEventTimeWindows.of(Time.minutes(5), Time.seconds(10)))
    .aggregate(new PageViewCountAggregator());

// 2. User activity analytics  
DataStream<UserActivityCount> userStats = events
    .keyBy(ClickEvent::getUserId)
    .window(TumblingEventTimeWindows.of(Time.minutes(1)))
    .aggregate(new UserActivityAggregator());

// 3. Real-time trending pages
DataStream<TrendingPage> trending = pageStats
    .keyBy(PageViewCount::getPageUrl)
    .window(SlidingEventTimeWindows.of(Time.minutes(10), Time.minutes(1)))
    .process(new TrendingPageDetector());
```

### 5.2 Side Outputs for Event Classification

Use side outputs to split streams based on event characteristics:

```java
// Define side output tags
final OutputTag<ClickEvent> purchaseEvents = new OutputTag<ClickEvent>("purchases"){};
final OutputTag<ClickEvent> highValueEvents = new OutputTag<ClickEvent>("high-value"){};

// Process function with side outputs
SingleOutputStreamOperator<ClickEvent> mainStream = events
    .process(new ProcessFunction<ClickEvent, ClickEvent>() {
        @Override
        public void processElement(ClickEvent event, Context ctx, Collector<ClickEvent> out) {
            // Main output: regular events
            out.collect(event);
            
            // Side output: purchase events
            if ("PURCHASE".equals(event.getEventType())) {
                ctx.output(purchaseEvents, event);
            }
            
            // Side output: high-value users (multiple events in short time)
            // Implementation depends on state management
        }
    });

// Access side outputs
DataStream<ClickEvent> purchases = mainStream.getSideOutput(purchaseEvents);
```

### 5.3 Stateful Processing with ProcessFunction

For complex logic requiring state:

```java
public class UserSessionProcessor extends KeyedProcessFunction<String, ClickEvent, UserSession> {
    
    private ValueState<UserSession> sessionState;
    private ValueState<Long> sessionTimer;
    
    @Override
    public void open(Configuration parameters) {
        sessionState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("session", UserSession.class));
        sessionTimer = getRuntimeContext().getState(
            new ValueStateDescriptor<>("timer", Long.class));
    }
    
    @Override
    public void processElement(ClickEvent event, Context ctx, Collector<UserSession> out) {
        UserSession session = sessionState.value();
        
        if (session == null) {
            // Start new session
            session = new UserSession(event.getUserId(), ctx.timestamp());
        }
        
        session.addEvent(event);
        sessionState.update(session);
        
        // Set/update session timeout timer
        long timerTime = ctx.timestamp() + 15 * 60 * 1000; // 15 minute timeout
        ctx.timerService().registerEventTimeTimer(timerTime);
        sessionTimer.update(timerTime);
    }
    
    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<UserSession> out) {
        // Session timeout - emit completed session
        UserSession session = sessionState.value();
        if (session != null) {
            session.markComplete();
            out.collect(session);
            sessionState.clear();
        }
    }
}
```

### 5.4 Multiple Output Sinks

Configure multiple outputs for different use cases:

```java
// Console output for development
pageStats.print("PAGE-ANALYTICS");

// Kafka output for downstream systems
pageStats.addSink(new FlinkKafkaProducer<>(
    "analytics-results",
    new PageViewCountSerializationSchema(),
    kafkaProps
));

// Custom sink for real-time dashboard
trending.addSink(new DashboardSink());
```

**Exercise 5.1**: Implement a real-time funnel analysis:
1. Track user progression: Page View → Click → Add to Cart → Purchase
2. Calculate conversion rates between each step
3. Identify drop-off points

**Exercise 5.2**: Create an anomaly detection system:
1. Calculate baseline traffic patterns per page
2. Detect when current traffic exceeds 2x the baseline
3. Output alerts with details about the anomaly

**Exercise 5.3**: Build user segmentation analytics:
1. Classify users as: new, returning, power users
2. Track behavior patterns per segment
3. Calculate segment-specific metrics

---

## Lesson 6: Production Considerations

**Goal**: Learn monitoring, scaling, and error handling for production deployments.

### 6.1 Monitoring Your Flink Application

#### Key Metrics to Track

```java
// In your aggregation function, add custom metrics
public class PageViewCountAggregator implements AggregateFunction<...> {
    private transient Counter eventsProcessed;
    private transient Histogram processingLatency;
    
    @Override
    public void open(Configuration parameters) {
        eventsProcessed = getRuntimeContext()
            .getMetricGroup()
            .counter("eventsProcessed");
            
        processingLatency = getRuntimeContext()
            .getMetricGroup()
            .histogram("processingLatency", new DropwizardHistogramWrapper(
                new com.codahale.metrics.Histogram(new UniformReservoir())));
    }
    
    @Override
    public Accumulator add(ClickEvent event, Accumulator acc) {
        long startTime = System.currentTimeMillis();
        
        // Your aggregation logic here
        acc.count++;
        
        // Update metrics
        eventsProcessed.inc();
        processingLatency.update(System.currentTimeMillis() - startTime);
        
        return acc;
    }
}
```

#### Accessing Metrics

```bash
# Flink metrics via REST API
curl http://localhost:8081/jobs/<job-id>/metrics

# Key metrics to monitor:
# - numRecordsInPerSec: Input rate
# - numRecordsOutPerSec: Output rate  
# - currentInputWatermark: Event time progress
# - latency: Processing latency
```

### 6.2 Checkpointing and Fault Tolerance

```java
// Enable checkpointing
env.enableCheckpointing(30000); // Every 30 seconds

// Configure checkpoint behavior
CheckpointConfig config = env.getCheckpointConfig();
config.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
config.setMinPauseBetweenCheckpoints(500);
config.setCheckpointTimeout(60000);
config.setMaxConcurrentCheckpoints(1);

// Enable externalized checkpoints for recovery
config.enableExternalizedCheckpoints(
    CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
```

### 6.3 Error Handling Strategies

```java
// Handle deserialization errors gracefully
public class RobustClickEventDeserializationSchema implements DeserializationSchema<ClickEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(RobustClickEventDeserializationSchema.class);
    private final Counter deserializationErrors;
    
    @Override
    public ClickEvent deserialize(byte[] message) throws IOException {
        try {
            return objectMapper.readValue(message, ClickEvent.class);
        } catch (Exception e) {
            LOG.warn("Failed to deserialize message: {}", new String(message), e);
            deserializationErrors.inc();
            return null; // Or return a default/error event
        }
    }
}

// Filter out null events from deserialization errors
DataStream<ClickEvent> cleanEvents = events
    .filter(Objects::nonNull);
```

### 6.4 Scaling Your Application

#### Horizontal Scaling

```bash
# Scale task managers
docker-compose up --scale taskmanager=3

# Or adjust parallelism
env.setParallelism(8);  // Match available task slots
```

#### Partitioning Strategy

```java
// Ensure good key distribution
keyedStream = events
    .keyBy(event -> {
        // Good: Evenly distributed keys
        return event.getUserId();
        
        // Bad: Skewed distribution
        // return event.getPageUrl(); // if few pages dominate
    });
```

### 6.5 Performance Optimization

```java
// Optimize for throughput
env.setBufferTimeout(100); // Batch records for 100ms

// Optimize network serialization
env.getConfig().enableObjectReuse(); // Reuse objects to reduce GC

// Tune watermark intervals
WatermarkStrategy.<ClickEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
    .withWatermarkAlignment("alignment-group", Duration.ofSeconds(20));
```

### 6.6 Production Deployment Checklist

#### Configuration Management

```yaml
# application.yml
flink:
  parallelism: ${FLINK_PARALLELISM:4}
  checkpoint-interval: ${CHECKPOINT_INTERVAL:30000}
  
kafka:
  bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
  consumer-group: ${CONSUMER_GROUP:clickstream-analytics}
```

#### Health Checks

```bash
#!/bin/bash
# health-check.sh

# Check if job is running
JOB_STATUS=$(curl -s http://localhost:8081/jobs | jq -r '.jobs[0].status')
if [ "$JOB_STATUS" != "RUNNING" ]; then
    echo "Flink job not running: $JOB_STATUS"
    exit 1
fi

# Check if processing events
RECENT_EVENTS=$(docker logs taskmanager --since 30s | grep "RECEIVED EVENT" | wc -l)
if [ "$RECENT_EVENTS" -eq 0 ]; then
    echo "No events processed in last 30 seconds"
    exit 1
fi

echo "Health check passed"
```

**Exercise 6.1**: Set up comprehensive monitoring:
1. Configure custom metrics for your aggregation functions
2. Create alerts for high processing latency
3. Monitor checkpoint success rates

**Exercise 6.2**: Test fault tolerance:
1. Submit a job with checkpointing enabled
2. Kill a TaskManager while processing events
3. Verify the job recovers and continues processing
4. Check that no events are lost

**Exercise 6.3**: Performance testing:
1. Increase event generation to 1000 events/second
2. Monitor resource usage and processing latency
3. Identify bottlenecks and optimize configuration
4. Scale horizontally if needed

---

## Summary & Next Steps

### What You've Learned

✅ **Flink Fundamentals**: Cluster architecture, DataStream API, parallelism
✅ **Stream Processing**: Event time, watermarks, late event handling  
✅ **Kafka Integration**: Consuming real-time data, serialization schemas
✅ **Windowing**: Sliding windows, tumbling windows, aggregation functions
✅ **Advanced Analytics**: Stateful processing, side outputs, complex event processing
✅ **Production Skills**: Monitoring, fault tolerance, scaling, error handling

### Advanced Topics to Explore

1. **Complex Event Processing (CEP)**: Pattern detection in event streams
2. **Machine Learning**: Real-time model scoring with Flink ML
3. **Table API & SQL**: Stream processing with SQL syntax
4. **State Backends**: RocksDB for large state, savepoints
5. **Kubernetes Deployment**: Cloud-native Flink deployment

### Real-World Applications

- **E-commerce**: Real-time recommendation engines, fraud detection
- **IoT**: Sensor data processing, predictive maintenance
- **Financial Services**: Risk management, algorithmic trading
- **Gaming**: Real-time leaderboards, cheat detection
- **Ad Tech**: Real-time bidding, campaign optimization

### Resources for Continued Learning

- [Flink Documentation](https://flink.apache.org/docs/)
- [Flink Training Exercises](https://github.com/apache/flink-training)
- [Ververica Academy](https://www.ververica.com/academy)
- [Stream Processing with Apache Flink (O'Reilly)](https://www.oreilly.com/library/view/stream-processing-with/9781491974285/)

---

**Congratulations!** You've built a production-ready real-time analytics system using Apache Flink. You now have the skills to tackle complex stream processing challenges in any domain.