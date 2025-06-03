package org.jdkxx.commons.filesystem.config;

import lombok.Data;
import org.jdkxx.commons.configuration.Configuration;
import org.jdkxx.commons.filesystem.config.options.FileSystemOptions;
import org.jdkxx.commons.filesystem.pool.PoolConfig;
import org.jdkxx.commons.filesystem.utils.URISupport;

import java.io.Serializable;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.jdkxx.commons.filesystem.config.options.FileSystemOptions.*;

public class FileSystemConfiguration extends Configuration {
    public FileSystemConfiguration(String scheme) {
        set(FileSystemOptions.SCHEME, scheme);
    }

    public URI getURI() {
        return URISupport.create(get(FileSystemOptions.SCHEME),
                null,
                get(HOST),
                get(PORT),
                null,
                null,
                null);
    }

    public URI getURIWithUsername() {
        return URISupport.create(get(FileSystemOptions.SCHEME),
                get(USERNAME),
                get(HOST),
                get(PORT),
                get(BUCKET),
                null,
                null);
    }

    public Map<String, ?> getEnvironment() {
        FileSystemEnvironment environment = FileSystemEnvironment.copy(confData);
        if (!contains(POOL_CONFIG)) {
            PoolConfig poolConfig = PoolConfigBuilder.create().build();
            environment.put(POOL_CONFIG.key(), poolConfig);
        }
        return environment;
    }


    @Data
    static class PoolConfigBuilder implements Serializable {
        private Duration maxWaitTime;
        private Duration maxIdleTime;
        private int initialSize = 5;
        private int maxSize = 10;

        PoolConfigBuilder() {
        }

        public static PoolConfigBuilder create() {
            return new PoolConfigBuilder();
        }

        public PoolConfig build() {
            PoolConfig.Builder builder = new PoolConfig.Builder()
                    .withInitialSize(initialSize)
                    .withMaxSize(maxSize);
            if (maxWaitTime != null) {
                builder.withMaxWaitTime(maxWaitTime);
            }
            if (maxIdleTime != null) {
                builder.withMaxIdleTime(maxIdleTime);
            }
            return builder.build();
        }
    }
}
