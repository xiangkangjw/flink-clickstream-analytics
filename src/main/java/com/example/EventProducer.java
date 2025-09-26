package com.example;

import com.example.model.ClickEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class EventProducer {
    
    private static final Logger LOG = LoggerFactory.getLogger(EventProducer.class);
    private static final String[] PAGES = {
        "/home", "/products", "/about", "/contact", "/login", "/checkout", "/profile", "/search"
    };
    private static final String[] IPS = {
        "192.168.1.1", "10.0.0.1", "172.16.0.1", "203.0.113.1", "198.51.100.1"
    };
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final Random random;
    
    public EventProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        
        this.producer = new KafkaProducer<>(props);
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
    }
    
    public void sendEvent(ClickEvent event, String topic) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getUserId(), eventJson);
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    LOG.error("Failed to send event", exception);
                } else {
                    LOG.debug("Sent event to topic {} partition {} offset {}", 
                             metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
        } catch (Exception e) {
            LOG.error("Error serializing event", e);
        }
    }
    
    public ClickEvent generateRandomEvent() {
        return new ClickEvent(
            PAGES[random.nextInt(PAGES.length)],
            "user_" + (1000 + random.nextInt(9000)),
            System.currentTimeMillis(),
            IPS[random.nextInt(IPS.length)]
        );
    }
    
    public void startProducing(String topic, int eventsPerSecond, int durationSeconds) {
        LOG.info("Starting to produce {} events per second for {} seconds to topic {}", 
                 eventsPerSecond, durationSeconds, topic);
        
        int totalEvents = eventsPerSecond * durationSeconds;
        long intervalMs = 1000 / eventsPerSecond;
        
        for (int i = 0; i < totalEvents; i++) {
            ClickEvent event = generateRandomEvent();
            sendEvent(event, topic);
            
            if ((i + 1) % 100 == 0) {
                LOG.info("Sent {} events", i + 1);
            }
            
            try {
                TimeUnit.MILLISECONDS.sleep(intervalMs);
            } catch (InterruptedException e) {
                LOG.warn("Producer interrupted", e);
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        LOG.info("Finished producing {} events", totalEvents);
    }
    
    public void close() {
        producer.close();
    }
    
    public static void main(String[] args) {
        String bootstrapServers = args.length > 0 ? args[0] : "localhost:9092";
        String topic = args.length > 1 ? args[1] : "click-stream";
        int eventsPerSecond = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        int durationSeconds = args.length > 3 ? Integer.parseInt(args[3]) : 60;
        
        EventProducer producer = new EventProducer(bootstrapServers);
        
        try {
            producer.startProducing(topic, eventsPerSecond, durationSeconds);
        } finally {
            producer.close();
        }
    }
}