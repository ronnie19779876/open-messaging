package org.jdkxx.commons.lang;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class YamlResourceParser<T> {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final Class<T> clazz;

    private YamlResourceParser(Class<T> clazz) {
        this.clazz = clazz;
    }

    public T parse(String resource) throws IOException {
        try (InputStream is = ResourceLoader.getResourceAsStream(resource)) {
            return mapper.readValue(is, clazz);
        }
    }

    public T parse(String resource, Map<String, String> props) throws Exception {
        try (InputStream is = ResourceLoader.getResourceAsStream(resource)) {
            //we need to replace all placeholders in this configuration.
            StringSubstitutor sub = new StringSubstitutor(props);
            String text = sub.replace(IOUtils.toString(is, StandardCharsets.UTF_8));
            return mapper.readValue(text, clazz);
        }
    }

    public static <T> YamlResourceParser<T> create(Class<T> clazz) {
        return new YamlResourceParser<>(clazz);
    }
}
