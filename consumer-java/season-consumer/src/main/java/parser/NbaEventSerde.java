package parser;

import java.util.Map;
import java.util.Optional;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import br.ufes.soe.model.NbaPrimitiveEvent;

public class NbaEventSerde implements Serde<Optional<NbaPrimitiveEvent>> {

    private final NbaEventDeserializer deserializer = new NbaEventDeserializer();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        deserializer.configure(configs, isKey);
    }

    @Override
    public void close() {
        deserializer.close();
    }

    @Override
    public Serializer<Optional<NbaPrimitiveEvent>> serializer() {
        // Como o seu app só consome, podemos retornar null ou um serializer dummy
        return null; 
    }

    @Override
    public Deserializer<Optional<NbaPrimitiveEvent>> deserializer() {
        return this.deserializer;
    }
}
