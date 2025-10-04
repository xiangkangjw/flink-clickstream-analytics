# Implementation Roadmap & TODO List

**Last Updated**: 2025-09-30
**Status**: Planning Phase

This document provides a prioritized, actionable roadmap for completing the Real-Time Clickstream Analytics project based on gaps identified in [implementation-gaps.md](implementation-gaps.md).

---

## Phase 1: MVP Core Features (2-3 weeks)

**Goal**: Achieve functional MVP that meets core PRD/TRD requirements

### Sprint 1: Event Schema & Time Semantics (Week 1)

#### TODO-1.1: Expand ClickEvent Schema ⭐⭐⭐
**Priority**: CRITICAL
**Effort**: 4 hours
**Dependencies**: None

**Tasks**:
- [ ] Add `session_id` field to ClickEvent
- [ ] Add `event_type` enum (VIEW, CLICK, ADD_TO_CART, PURCHASE, SEARCH)
- [ ] Add `product_id` (nullable String)
- [ ] Add `user_agent` field
- [ ] Add `referrer` (nullable String)
- [ ] Add `session_duration` (nullable Integer)
- [ ] Update constructor and getters/setters
- [ ] Add Jackson annotations for all new fields
- [ ] Update toString() method

**Files**:
- `src/main/java/com/example/model/ClickEvent.java`

**Acceptance Criteria**:
- Schema matches TRD Section 3.2.1
- All fields properly serialized/deserialized
- No breaking changes to existing code

---

#### TODO-1.2: Implement Event-Time Processing ⭐⭐⭐
**Priority**: CRITICAL
**Effort**: 3 hours
**Dependencies**: None

**Tasks**:
- [ ] Replace `WatermarkStrategy.noWatermarks()` with bounded out-of-orderness strategy
- [ ] Set watermark delay to 30 seconds
- [ ] Add timestamp assigner extracting from `ClickEvent.timestamp`
- [ ] Replace `SlidingProcessingTimeWindows` with `SlidingEventTimeWindows`
- [ ] Test late event handling
- [ ] Verify window boundaries are correct

**Files**:
- `src/main/java/com/example/ClickstreamJob.java` (lines 46, 60-62)

**Code Snippet**:
```java
WatermarkStrategy<ClickEvent> watermarkStrategy = WatermarkStrategy
    .<ClickEvent>forBoundedOutOfOrderness(Duration.ofSeconds(30))
    .withTimestampAssigner((event, timestamp) -> event.getTimestamp());

// Apply to source:
DataStream<ClickEvent> clickEvents = env
    .fromSource(kafkaSource, watermarkStrategy, "Kafka Source");

// Update window:
.window(SlidingEventTimeWindows.of(
    Time.minutes(JobConfig.WINDOW_SIZE_MINUTES),
    Time.seconds(JobConfig.WINDOW_SLIDE_SECONDS)))
```

**Acceptance Criteria**:
- Windows based on event timestamps, not arrival time
- Late events (within 30s) properly included
- System handles restarts without incorrect aggregations

---

#### TODO-1.3: Enhance Event Producer Realism ⭐⭐
**Priority**: HIGH
**Effort**: 6 hours
**Dependencies**: TODO-1.1

**Tasks**:
- [ ] Create EventType enum (VIEW, CLICK, ADD_TO_CART, PURCHASE, SEARCH)
- [ ] Expand page catalog to 100 pages
- [ ] Create product catalog with 500 products
- [ ] Expand user pool to 1000 users
- [ ] Implement event type distribution (70% VIEW, 20% CLICK, 7% CART, 3% PURCHASE)
- [ ] Add session ID generation and tracking
- [ ] Implement session duration (15-45 minutes)
- [ ] Generate realistic user journeys (home → product → cart → checkout)
- [ ] Add user agent strings (5 browser types)
- [ ] Add referrer URLs

**Files**:
- `src/main/java/com/example/EventProducer.java`
- Create: `src/main/java/com/example/model/EventType.java`
- Create: `src/main/java/com/example/generators/SessionManager.java` (optional)

**Acceptance Criteria**:
- Events follow realistic user behavior patterns
- Sessions are consistent (same session_id for user journey)
- Event type distribution matches 70/20/7/3 split
- Catalog sizes match TRD requirements

---

### Sprint 2: Analytics & Output (Week 2)

#### TODO-2.1: Enhance PageAnalytics Schema ⭐⭐⭐
**Priority**: CRITICAL
**Effort**: 3 hours
**Dependencies**: TODO-1.1

**Tasks**:
- [ ] Rename `PageViewCount` → `PageAnalytics`
- [ ] Add `window_start` (long timestamp)
- [ ] Add `window_end` (long timestamp)
- [ ] Add `unique_users` (int)
- [ ] Add `click_count` (long)
- [ ] Add `conversion_count` (long)
- [ ] Add `engagement_rate` (double)
- [ ] Add `bounce_rate` (double)
- [ ] Add `avg_session_duration` (double)
- [ ] Update constructor and getters
- [ ] Add proper toString() formatting

**Files**:
- `src/main/java/com/example/model/PageViewCount.java` → `PageAnalytics.java`

**Acceptance Criteria**:
- Schema matches TRD Section 3.4.1
- All metrics have reasonable default values

---

#### TODO-2.2: Implement Rich Aggregation Logic ⭐⭐⭐
**Priority**: CRITICAL
**Effort**: 8 hours
**Dependencies**: TODO-2.1, TODO-1.1

**Tasks**:
- [ ] Update `PageViewAggregator` to track multiple event types
- [ ] Add unique user tracking (use Set<String> or HyperLogLog)
- [ ] Count VIEW events separately from CLICK events
- [ ] Track PURCHASE events (conversions)
- [ ] Calculate engagement_rate = clicks / views
- [ ] Implement bounce_rate logic (single-event sessions)
- [ ] Calculate avg_session_duration
- [ ] Extract window start/end timestamps in WindowResultProcessor
- [ ] Handle edge cases (division by zero, empty windows)

**Files**:
- `src/main/java/com/example/functions/PageViewAggregator.java`
- `src/main/java/com/example/functions/WindowResultProcessor.java`

**Acceptance Criteria**:
- Unique users counted correctly (no duplicates)
- Engagement rate between 0.0 and 1.0
- All metrics mathematically correct
- Window boundaries properly captured

---

#### TODO-2.3: Add Kafka Output Sink ⭐⭐⭐
**Priority**: CRITICAL
**Effort**: 4 hours
**Dependencies**: TODO-2.1

**Tasks**:
- [ ] Create Kafka sink for PageAnalytics
- [ ] Use topic `analytics-results`
- [ ] Implement JSON serializer for PageAnalytics
- [ ] Set delivery guarantee to AT_LEAST_ONCE
- [ ] Keep console print for debugging
- [ ] Add error handling for sink failures
- [ ] Test end-to-end flow

**Files**:
- `src/main/java/com/example/ClickstreamJob.java` (line 67)
- Create: `src/main/java/com/example/serializers/PageAnalyticsSerializer.java`

**Code Snippet**:
```java
// Add to pom.xml:
<dependency>
    <groupId>org.apache.flink</groupId>
    <artifactId>flink-connector-kafka</artifactId>
    <version>3.0.2-1.18</version>
</dependency>

// In ClickstreamJob:
KafkaSink<PageAnalytics> kafkaSink = KafkaSink.<PageAnalytics>builder()
    .setBootstrapServers(JobConfig.KAFKA_BOOTSTRAP_SERVERS)
    .setRecordSerializer(new PageAnalyticsSerializer("analytics-results"))
    .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
    .build();

pageViews.sinkTo(kafkaSink).name("Kafka Analytics Sink");
pageViews.print("Page View Counts").name("Print Results"); // Keep for debugging
```

**Acceptance Criteria**:
- Analytics appear in `analytics-results` Kafka topic
- Messages properly formatted as JSON
- No message loss under normal conditions

---

#### TODO-2.4: Implement Top N Aggregation ⭐⭐
**Priority**: HIGH
**Effort**: 6 hours
**Dependencies**: TODO-2.1

**Tasks**:
- [ ] Create `TopPages` model class
- [ ] Implement `TopNPagesFunction` (ProcessWindowFunction)
- [ ] Add global window for all pages
- [ ] Sort by view_count descending
- [ ] Take top 10 results
- [ ] Add to job topology
- [ ] Sink top pages to separate topic or include in console output

**Files**:
- Create: `src/main/java/com/example/model/TopPages.java`
- Create: `src/main/java/com/example/functions/TopNPagesFunction.java`
- `src/main/java/com/example/ClickstreamJob.java`

**Code Snippet**:
```java
DataStream<String> topPages = pageViews
    .windowAll(SlidingEventTimeWindows.of(
        Time.minutes(JobConfig.WINDOW_SIZE_MINUTES),
        Time.seconds(JobConfig.WINDOW_SLIDE_SECONDS)))
    .process(new TopNPagesFunction(10))
    .name("Top 10 Pages");

topPages.print("TOP PAGES").name("Print Top Pages");
```

**Acceptance Criteria**:
- Top 10 pages correctly ranked every 10 seconds
- Handles ties gracefully
- Output clearly formatted

---

### Sprint 3: Infrastructure & Configuration (Week 3)

#### TODO-3.1: Configure Kafka Topics Properly ⭐⭐
**Priority**: HIGH
**Effort**: 2 hours
**Dependencies**: None

**Tasks**:
- [ ] Create `kafka-init.sh` script
- [ ] Add topic creation for `clickstream-events` (6 partitions, snappy, 2hr retention)
- [ ] Add topic creation for `analytics-results` (3 partitions, gzip, 24hr retention)
- [ ] Update docker-compose.yml to run init script
- [ ] Add health check to wait for Kafka before creating topics
- [ ] Update EventProducer and ClickstreamJob to use new topic names

**Files**:
- Create: `kafka-init.sh`
- `docker-compose.yml`
- `src/main/java/com/example/config/JobConfig.java`

**Script Template**:
```bash
#!/bin/bash
# kafka-init.sh

# Wait for Kafka to be ready
kafka-topics.sh --bootstrap-server kafka:9092 --list

# Create clickstream-events topic
kafka-topics.sh --create --if-not-exists \
  --bootstrap-server kafka:9092 \
  --topic clickstream-events \
  --partitions 6 \
  --replication-factor 1 \
  --config retention.ms=7200000 \
  --config compression.type=snappy

# Create analytics-results topic
kafka-topics.sh --create --if-not-exists \
  --bootstrap-server kafka:9092 \
  --topic analytics-results \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=86400000 \
  --config compression.type=gzip

echo "Kafka topics initialized successfully"
```

**Acceptance Criteria**:
- Topics created automatically on startup
- Correct partition/retention/compression settings
- Script idempotent (can run multiple times)

---

#### TODO-3.2: Update Configuration Management ⭐
**Priority**: MEDIUM
**Effort**: 2 hours
**Dependencies**: TODO-3.1

**Tasks**:
- [ ] Add environment variable support to JobConfig
- [ ] Externalize all configuration values
- [ ] Support dev/prod profiles
- [ ] Add validation for required configs
- [ ] Document all configuration options

**Files**:
- `src/main/java/com/example/config/JobConfig.java`

**Code Pattern**:
```java
public static final String KAFKA_BOOTSTRAP_SERVERS =
    System.getenv().getOrDefault("KAFKA_BROKERS", "kafka:9092");

public static final String KAFKA_TOPIC =
    System.getenv().getOrDefault("KAFKA_TOPIC", "clickstream-events");
```

**Acceptance Criteria**:
- All hardcoded values externalized
- Defaults work for local development
- Can override via environment variables

---

## Phase 2: Advanced Features (1-2 weeks)

### Sprint 4: Avro & Schema Evolution

#### TODO-4.1: Implement Avro Serialization ⭐
**Priority**: MEDIUM
**Effort**: 8 hours
**Dependencies**: Phase 1 complete

**Tasks**:
- [ ] Add Avro Maven plugin to pom.xml
- [ ] Create `ClickstreamEvent.avsc` schema definition
- [ ] Create `PageAnalytics.avsc` schema definition
- [ ] Generate Java classes from Avro schemas
- [ ] Replace JSON deserializer with Avro in Kafka source
- [ ] Replace JSON serializer with Avro in Kafka sink
- [ ] Update EventProducer to use Avro serialization
- [ ] Test schema compatibility

**Files**:
- `pom.xml`
- Create: `src/main/avro/ClickstreamEvent.avsc`
- Create: `src/main/avro/PageAnalytics.avsc`
- Update: All serializers/deserializers

**Acceptance Criteria**:
- Events serialized as Avro
- Smaller message sizes than JSON
- Schema validation working

---

#### TODO-4.2: Add Schema Registry ⚠️
**Priority**: LOW (Stretch)
**Effort**: 6 hours
**Dependencies**: TODO-4.1

**Tasks**:
- [ ] Add Confluent Schema Registry to docker-compose.yml
- [ ] Configure Avro serializers to use registry
- [ ] Register schemas on startup
- [ ] Test schema evolution scenarios

**Files**:
- `docker-compose.yml`

---

### Sprint 5: Monitoring & Observability

#### TODO-5.1: Add Application Metrics ⭐
**Priority**: MEDIUM
**Effort**: 4 hours
**Dependencies**: None

**Tasks**:
- [ ] Add SLF4J logging to all components
- [ ] Log key metrics (events/sec, window processing time)
- [ ] Add Flink metric reporters
- [ ] Expose JMX metrics for Kafka
- [ ] Create structured logging format

**Files**:
- All Java classes
- `src/main/resources/log4j2.properties`

---

#### TODO-5.2: Setup Monitoring Stack ⚠️
**Priority**: LOW (Stretch)
**Effort**: 8 hours
**Dependencies**: TODO-5.1

**Tasks**:
- [ ] Add Prometheus to docker-compose.yml
- [ ] Add Grafana to docker-compose.yml
- [ ] Configure Flink Prometheus reporter
- [ ] Configure Kafka JMX exporter
- [ ] Create Grafana dashboard for key metrics

**Files**:
- `docker-compose.yml`
- Create: `monitoring/prometheus.yml`
- Create: `monitoring/grafana/dashboards/clickstream-analytics.json`

---

## Phase 3: Polish & Documentation (3-5 days)

### Sprint 6: Testing & Validation

#### TODO-6.1: Add Unit Tests ⭐
**Priority**: MEDIUM
**Effort**: 6 hours
**Dependencies**: None

**Tasks**:
- [ ] Add JUnit 5 dependency
- [ ] Test EventProducer event generation logic
- [ ] Test PageViewAggregator accumulator logic
- [ ] Test TopNPagesFunction ranking
- [ ] Test serializers/deserializers
- [ ] Achieve 80%+ code coverage

**Files**:
- Create: `src/test/java/com/example/**/*Test.java`

---

#### TODO-6.2: Integration Testing ⚠️
**Priority**: LOW
**Effort**: 8 hours
**Dependencies**: TODO-6.1

**Tasks**:
- [ ] Create end-to-end test with embedded Kafka
- [ ] Test window boundary correctness
- [ ] Test late event handling
- [ ] Load test at 1000 events/sec

**Files**:
- Create: `src/test/java/com/example/integration/`

---

### Sprint 7: Documentation

#### TODO-7.1: Update Documentation ⭐
**Priority**: HIGH
**Effort**: 3 hours
**Dependencies**: Phase 1 complete

**Tasks**:
- [ ] Update CLAUDE.md with new commands
- [ ] Document new configuration options
- [ ] Add troubleshooting guide
- [ ] Update architecture diagrams
- [ ] Add performance tuning tips

**Files**:
- `CLAUDE.md`
- Create: `docs/troubleshooting.md`
- Create: `docs/performance-tuning.md`

---

## Quick Reference: Priority Matrix

| Task ID | Description | Priority | Effort | Phase |
|---------|-------------|----------|--------|-------|
| TODO-1.1 | Expand ClickEvent Schema | ⭐⭐⭐ Critical | 4h | 1 |
| TODO-1.2 | Event-Time Processing | ⭐⭐⭐ Critical | 3h | 1 |
| TODO-1.3 | Realistic Event Generator | ⭐⭐ High | 6h | 1 |
| TODO-2.1 | PageAnalytics Schema | ⭐⭐⭐ Critical | 3h | 1 |
| TODO-2.2 | Rich Aggregation Logic | ⭐⭐⭐ Critical | 8h | 1 |
| TODO-2.3 | Kafka Output Sink | ⭐⭐⭐ Critical | 4h | 1 |
| TODO-2.4 | Top N Aggregation | ⭐⭐ High | 6h | 1 |
| TODO-3.1 | Kafka Topic Config | ⭐⭐ High | 2h | 1 |
| TODO-3.2 | Config Management | ⭐ Medium | 2h | 1 |
| TODO-4.1 | Avro Serialization | ⭐ Medium | 8h | 2 |
| TODO-4.2 | Schema Registry | ⚠️ Stretch | 6h | 2 |
| TODO-5.1 | Application Metrics | ⭐ Medium | 4h | 2 |
| TODO-5.2 | Monitoring Stack | ⚠️ Stretch | 8h | 2 |
| TODO-6.1 | Unit Tests | ⭐ Medium | 6h | 3 |
| TODO-6.2 | Integration Tests | ⚠️ Stretch | 8h | 3 |
| TODO-7.1 | Update Documentation | ⭐ High | 3h | 3 |

**Total Estimated Effort (Critical + High)**: ~40 hours (1 week full-time)
**Total Estimated Effort (All MVP)**: ~67 hours (1.5 weeks full-time)

---

## Success Criteria Checklist

### MVP Completion (Phase 1)
- [ ] Event schema matches TRD requirements (6+ fields)
- [ ] Event-time windows with watermarks
- [ ] Realistic event generation (1000 users, 100 pages, 500 products)
- [ ] Rich analytics output (8+ metrics)
- [ ] Kafka sink for analytics results
- [ ] Top 10 pages aggregation
- [ ] Properly configured Kafka topics
- [ ] End-to-end data flow validated

### Full Success (Phase 2-3)
- [ ] Avro serialization implemented
- [ ] Monitoring stack deployed
- [ ] Unit test coverage >80%
- [ ] Integration tests passing
- [ ] Comprehensive documentation
- [ ] Performance targets met (1000 eps, <500ms latency)

---

## Notes

- **Dependencies**: Tasks can be parallelized within sprints if no dependencies exist
- **Testing**: Test after each TODO completion, not at the end
- **Documentation**: Update as you go, not at the end
- **Priorities**: ⭐⭐⭐ Critical → ⭐⭐ High → ⭐ Medium → ⚠️ Stretch

---

## Related Documents

- [Implementation Gaps Analysis](implementation-gaps.md)
- [Product Requirements (PRD)](product-requirements.md)
- [Technical Requirements (TRD)](technical-requirements.md)
