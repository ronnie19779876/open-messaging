package org.jdkxx.commons.filesystem.test.resource;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jdkxx.commons.configuration.Configuration;
import org.jdkxx.commons.lang.ResourceLoader;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Properties;

@Slf4j
public class ResourceFileLoader {
    private final static String RESOURCE_NAME = "security.password";
    public final static String ALIPAY_PREFIX = "alipay";
    public final static String HCP_PREFIX = "hcp";
    public final static String MINIO_PREFIX = "minio";

    public static Configuration load(@NotNull String prefix) throws Exception {
        Configuration configuration = new Configuration();
        Properties properties = ResourceLoader.getResourceAsProperties(RESOURCE_NAME);
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (StringUtils.startsWithIgnoreCase(key, prefix)) {
                configuration.setString(StringUtils.substringAfter(key, prefix + "."),
                        String.valueOf(entry.getValue()));
            }
        }
        return configuration;
    }

    public static void main(String[] args) throws Exception {
        Configuration configuration = ResourceFileLoader.load(HCP_PREFIX);
        log.info("load properties {} from resource file {}", configuration, RESOURCE_NAME);
    }
}
