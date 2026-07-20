package parser;

import java.util.Map;
import java.util.Optional;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import br.ufes.soe.model.NbaPrimitiveEvent;

public class NbaEventSerde implements Serde<Optional<NbaPrimitiveEvent>> {

    private final NbaEventDeserializer deserializer = new NbaEventDeserializer();
    private final NbaEventSerializer serializer = new NbaEventSerializer();

    @Override
        public Serializer<Optional<NbaPrimitiveEvent>> serializer() {
        return serializer;
    }

    @Override
        public Deserializer<Optional<NbaPrimitiveEvent>> deserializer() {
        return deserializer;
    }

    @Override
        public void configure(Map<String, ?> configs, boolean isKey) {
        serializer.configure(configs, isKey);
        deserializer.configure(configs, isKey);
    }

    @Override
    public void close() {
        serializer.close();
        deserializer.close();
    }
}
