package org.jdkxx.commons.lang;

import com.google.common.io.Resources;
import org.jdkxx.commons.configuration.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public class ResourceLoader {
    private ResourceLoader() {
    }

    public static Properties getResourceAsProperties(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream is = getResourceAsStream(resource)) {
            properties.load(is);
        }
        return properties;
    }

    public static Configuration getResourceAsConfiguration(String resource) throws IOException {
        Configuration configuration = new Configuration();
        Properties properties = getResourceAsProperties(resource);
        properties.keySet().stream()
                .map(String::valueOf)
                .forEach(key -> configuration.setString(key, properties.getProperty(key)));
        return configuration;
    }

    public static InputStream getResourceAsStream(String resource) throws IOException {
        try {
            URL url = Resources.getResource(resource);
            return url.openStream();
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
    }
}
