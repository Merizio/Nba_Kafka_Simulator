package parser;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.common.serialization.Deserializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufes.soe.model.NbaPrimitiveEvent;
import br.ufes.soe.parse.NbaMessageParser;

public class NbaEventDeserializer implements Deserializer<Optional<NbaPrimitiveEvent>> {

    private final NbaMessageParser parser;

    // Construtor padrão inicializando o ObjectMapper
    public NbaEventDeserializer() {
        this.parser = new NbaMessageParser(new ObjectMapper());
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // Configurações adicionais se necessário
    }

    @Override
    public Optional<NbaPrimitiveEvent> deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }
        try {
            String rawJson = new String(data, StandardCharsets.UTF_8);
            JsonNode root = parser.parseToTree(rawJson);
            return parser.toEvent(root);
        } catch (Exception e) {
            // Log de erro ou retornar Optional.empty() para ignorar mensagens malformadas (Poison Pills)
            System.err.println("Erro ao desserializar mensagem no tópico " + topic + ": " + e.getMessage());
            return Optional.empty(); 
        }
    }

    @Override
    public void close() {
        // Fechamento de recursos se necessário
    }
}