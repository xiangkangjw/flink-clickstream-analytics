package com.example.functions;

import com.example.model.ClickEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ClickEventDeserializer implements DeserializationSchema<ClickEvent> {
    
    private static final Logger LOG = LoggerFactory.getLogger(ClickEventDeserializer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public ClickEvent deserialize(byte[] message) throws IOException {
        try {
            return objectMapper.readValue(message, ClickEvent.class);
        } catch (Exception e) {
            LOG.warn("Failed to deserialize message: {}", new String(message), e);
            // Return a default event for invalid messages
            return new ClickEvent("invalid", "unknown", System.currentTimeMillis(), "0.0.0.0");
        }
    }
    
    @Override
    public boolean isEndOfStream(ClickEvent nextElement) {
        return false;
    }
    
    @Override
    public TypeInformation<ClickEvent> getProducedType() {
        return TypeInformation.of(ClickEvent.class);
    }
}