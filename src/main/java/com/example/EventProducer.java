package com.example;

import com.example.config.JobConfig;
import com.example.model.ClickEvent;
import com.example.model.EventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class EventProducer {
    
    private static final Logger LOG = LoggerFactory.getLogger(EventProducer.class);

    // Expanded catalog: 100 pages
    private static final List<String> PAGES = generatePages();

    // Product catalog: 500 products
    private static final List<String> PRODUCTS = generateProducts();

    // User pool: 1000 users
    private static final int USER_POOL_SIZE = 1000;

    // User agents: 5 browser types
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    };

    // Referrer URLs
    private static final String[] REFERRERS = {
        "https://google.com",
        "https://facebook.com",
        "https://twitter.com",
        "https://linkedin.com",
        "https://reddit.com",
        null // Direct traffic
    };

    private static final String[] IPS = {
        "192.168.1.1", "10.0.0.1", "172.16.0.1", "203.0.113.1", "198.51.100.1"
    };

    // Event type distribution: 70% VIEW, 20% CLICK, 7% CART, 3% PURCHASE
    private static final double[] EVENT_TYPE_PROBABILITIES = {0.70, 0.90, 0.97, 1.0}; // Cumulative
    
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final Random random;
    private final Map<String, UserSession> activeSessions;

    private static List<String> generatePages() {
        List<String> pages = new ArrayList<>();
        pages.add("/home");
        pages.add("/products");
        pages.add("/about");
        pages.add("/contact");
        pages.add("/login");
        pages.add("/checkout");
        pages.add("/profile");
        pages.add("/search");

        // Generate product pages
        for (int i = 1; i <= 50; i++) {
            pages.add("/products/category-" + i);
        }

        // Generate blog pages
        for (int i = 1; i <= 30; i++) {
            pages.add("/blog/post-" + i);
        }

        // Generate misc pages
        for (int i = 1; i <= 12; i++) {
            pages.add("/page-" + i);
        }

        return pages;
    }

    private static List<String> generateProducts() {
        List<String> products = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            products.add("product-" + String.format("%04d", i));
        }
        return products;
    }

    private static class UserSession {
        String sessionId;
        long sessionStart;
        int sessionDuration; // in seconds
        String userAgent;
        String referrer;

        UserSession(String sessionId, long sessionStart, int sessionDuration, String userAgent, String referrer) {
            this.sessionId = sessionId;
            this.sessionStart = sessionStart;
            this.sessionDuration = sessionDuration;
            this.userAgent = userAgent;
            this.referrer = referrer;
        }

        boolean isExpired(long currentTime) {
            return (currentTime - sessionStart) > (sessionDuration * 1000L);
        }
    }

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
        this.activeSessions = new HashMap<>();
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
    
    private UserSession getOrCreateSession(String userId, long currentTime) {
        UserSession session = activeSessions.get(userId);

        if (session == null || session.isExpired(currentTime)) {
            // Create new session: 15-45 minutes
            int sessionDuration = 900 + random.nextInt(1800); // 900-2700 seconds
            String sessionId = UUID.randomUUID().toString();
            String userAgent = USER_AGENTS[random.nextInt(USER_AGENTS.length)];
            String referrer = REFERRERS[random.nextInt(REFERRERS.length)];

            session = new UserSession(sessionId, currentTime, sessionDuration, userAgent, referrer);
            activeSessions.put(userId, session);
        }

        return session;
    }

    private EventType selectEventType() {
        double rand = random.nextDouble();
        if (rand < EVENT_TYPE_PROBABILITIES[0]) return EventType.VIEW;
        if (rand < EVENT_TYPE_PROBABILITIES[1]) return EventType.CLICK;
        if (rand < EVENT_TYPE_PROBABILITIES[2]) return EventType.ADD_TO_CART;
        return EventType.PURCHASE;
    }

    public ClickEvent generateRandomEvent() {
        long currentTime = System.currentTimeMillis();
        String userId = "user_" + (random.nextInt(USER_POOL_SIZE) + 1);
        UserSession session = getOrCreateSession(userId, currentTime);

        EventType eventType = selectEventType();
        String page = PAGES.get(random.nextInt(PAGES.size()));
        String productId = null;

        // Product-related events should have a product ID
        if (eventType == EventType.CLICK || eventType == EventType.ADD_TO_CART || eventType == EventType.PURCHASE) {
            productId = PRODUCTS.get(random.nextInt(PRODUCTS.size()));
        }

        return new ClickEvent(
            page,
            userId,
            currentTime,
            IPS[random.nextInt(IPS.length)],
            session.sessionId,
            eventType.name(),
            productId,
            session.userAgent,
            session.referrer,
            session.sessionDuration
        );
    }
    
    public void startProducing(String topic, int eventsPerSecond, int durationSeconds) {
        boolean continuous = durationSeconds >= Integer.MAX_VALUE / 2;

        if (continuous) {
            LOG.info("Starting continuous event production at {} events per second to topic {}",
                     eventsPerSecond, topic);
        } else {
            LOG.info("Starting to produce {} events per second for {} seconds to topic {}",
                     eventsPerSecond, durationSeconds, topic);
        }

        long intervalMs = 1000 / eventsPerSecond;
        int eventCount = 0;
        long startTime = System.currentTimeMillis();
        long endTime = continuous ? Long.MAX_VALUE : startTime + (durationSeconds * 1000L);

        while (System.currentTimeMillis() < endTime) {
            ClickEvent event = generateRandomEvent();
            sendEvent(event, topic);
            eventCount++;

            if (eventCount % 100 == 0) {
                LOG.info("Sent {} events", eventCount);
            }

            try {
                TimeUnit.MILLISECONDS.sleep(intervalMs);
            } catch (InterruptedException e) {
                LOG.warn("Producer interrupted", e);
                Thread.currentThread().interrupt();
                break;
            }
        }

        LOG.info("Finished producing {} events", eventCount);
    }
    
    public void close() {
        producer.close();
    }
    
    public static void main(String[] args) {
        String bootstrapServers = args.length > 0 ? args[0] : JobConfig.KAFKA_BOOTSTRAP_SERVERS;
        String topic = args.length > 1 ? args[1] : JobConfig.KAFKA_TOPIC;
        int eventsPerSecond = args.length > 2 ? Integer.parseInt(args[2]) : JobConfig.EVENTS_PER_SECOND;
        int durationSeconds = args.length > 3 ? Integer.parseInt(args[3]) : JobConfig.PRODUCER_DURATION_SECONDS;

        EventProducer producer = new EventProducer(bootstrapServers);

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down event producer gracefully...");
            producer.close();
        }));

        try {
            producer.startProducing(topic, eventsPerSecond, durationSeconds);
        } finally {
            producer.close();
        }
    }
}