package parser;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.kafka.common.serialization.Serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufes.soe.model.NbaPrimitiveEvent;

public class NbaEventSerializer implements Serializer<Optional<NbaPrimitiveEvent>> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public byte[] serialize(String topic, Optional<NbaPrimitiveEvent> data) {
        if (data == null || data.isEmpty()) {
            return null;
    }

    try {
        return mapper.writeValueAsString(data.get())
        .getBytes(StandardCharsets.UTF_8);
    } catch (JsonProcessingException e) {
        throw new RuntimeException("Erro serializando evento NBA", e);
    }
    }

    @Override
    public void close() {
    }
}
