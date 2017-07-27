package ai.labs.testing.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.StringWriter;

/**
 * @author ginccc
 */
class JsonSerialization {
    private static JsonSerialization instance;
    private ObjectMapper objectMapper;

    static JsonSerialization getInstance() {
        if (instance == null) {
            instance = new JsonSerialization();
        }

        return instance;
    }

    private JsonSerialization() {
        init();
    }

    private void init() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    String toJson(Object obj) throws IOException {
        StringWriter writer = new StringWriter();
        objectMapper.writeValue(writer, obj);
        return writer.toString();
    }

    Object toObject(String json, Class type) throws IOException {
        return objectMapper.readerFor(type).readValue(json);
    }
}
