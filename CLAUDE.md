# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java-based Apache Flink project that demonstrates real-time clickstream analytics using Kafka as the message broker. The project consists of:

- **Event Producer** (`EventProducer.java`): Generates synthetic clickstream events and publishes them to Kafka
- **Flink Job** (`ClickstreamJob.java`): Consumes events from Kafka, performs windowed aggregations to count page views
- **Infrastructure**: Docker Compose setup with Kafka, standard Flink cluster, and Kafka UI

## Architecture

The system follows a typical streaming analytics pattern:
1. Event producer generates clickstream data → Kafka topic "click-stream"
2. Flink job consumes from Kafka → processes events in 5-minute sliding windows (10-second slide)
3. Results are printed to stdout (can be extended to sink to databases/files)

Key components:
- **Kafka**: Message broker running on port 9092
- **Flink JobManager**: Cluster coordinator on port 8081 (web UI)
- **Flink TaskManager**: Executes the actual processing
- **Kafka UI**: Web interface on port 8080 for monitoring Kafka

## Common Commands

### Development Environment Setup
```bash
# Start all services (Kafka, Flink cluster, Kafka UI)
docker-compose up -d

# Stop all services
docker-compose down

# View logs
docker logs jobmanager
docker logs taskmanager
docker logs kafka
```

### Building and Running the Application
```bash
# Build the project
mvn clean package

# Submit the Flink job to the cluster
docker exec jobmanager ./bin/flink run /opt/flink/usrlib/flink-clickstream-1.0-SNAPSHOT.jar

# Run the event producer (generates sample data)
docker exec jobmanager java -cp /opt/flink/usrlib/flink-clickstream-1.0-SNAPSHOT.jar com.example.EventProducer kafka:9092 click-stream 10 60
```

### Monitoring and Debugging
- Flink Web UI: http://localhost:8081
- Kafka UI: http://localhost:8080
- Check job output: `docker logs taskmanager --tail 20 | grep -E "(RECEIVED EVENT|PageViewCount|\>)"`

### Development Workflow
1. Start infrastructure: `docker-compose up -d`
2. Build project: `mvn clean package`
3. Submit job: `docker exec jobmanager ./bin/flink run /opt/flink/usrlib/flink-clickstream-1.0-SNAPSHOT.jar`
4. Generate test data: `docker exec jobmanager java -cp /opt/flink/usrlib/flink-clickstream-1.0-SNAPSHOT.jar com.example.EventProducer kafka:9092 click-stream 10 60`
5. Monitor results via web UIs or container logs

## Key Files
- `src/main/java/com/example/ClickstreamJob.java`: Main Flink application with windowed aggregation logic
- `src/main/java/com/example/EventProducer.java`: Kafka producer for generating test events
- `src/main/java/com/example/model/`: Data model classes (ClickEvent, PageViewCount)
- `src/main/java/com/example/functions/`: Processing functions and deserializers
- `pom.xml`: Maven build configuration with Flink and Kafka dependencies
- `docker-compose.yml`: Infrastructure definition using standard Flink images
- `jars/`: Kafka connector JARs for Flink-Kafka integration