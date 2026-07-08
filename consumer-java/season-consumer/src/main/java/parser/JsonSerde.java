package parser;

import java.io.IOException;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSerde<T> implements Serde<T> {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Class<T> targetClass;

    public JsonSerde(Class<T> targetClass) {
        this.targetClass = targetClass;
    }

    @Override
    public Serializer<T> serializer() {
        return (topic, data) -> {
            try { return objectMapper.writeValueAsBytes(data); } 
            catch (IOException e) { throw new RuntimeException(e); }
        };
    }

    @Override
    public Deserializer<T> deserializer() {
        return (topic, data) -> {
            try { return objectMapper.readValue(data, targetClass); } 
            catch (IOException e) { throw new RuntimeException(e); }
        };
    }
}