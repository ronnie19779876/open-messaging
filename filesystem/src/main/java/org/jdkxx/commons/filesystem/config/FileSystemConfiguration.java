package org.jdkxx.commons.filesystem.config;

import com.jcraft.jsch.Proxy;
import lombok.Data;
import org.jdkxx.commons.configuration.Configuration;
import org.jdkxx.commons.filesystem.config.options.FileSystemOptions;
import org.jdkxx.commons.filesystem.pool.PoolConfig;
import org.jdkxx.commons.filesystem.utils.URISupport;

import java.io.Serializable;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.jdkxx.commons.filesystem.config.options.FileSystemOptions.*;

public class FileSystemConfiguration extends Configuration {
    private FileSystemConfiguration(String scheme) {
        set(SCHEME, scheme);
    }

    public URI getURI() {
        return URISupport.create(
                get(SCHEME),
                null,
                get(HOST),
                get(PORT),
                null,
                null,
                null);
    }

    public URI getURIWithUsername() {
        return URISupport.create(
                get(SCHEME),
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

    public static Builder builder(String scheme) {
        return new Builder(scheme);
    }

    public static class Builder {
        private final FileSystemConfiguration configuration;

        public Builder(String scheme) {
            this.configuration = new FileSystemConfiguration(scheme);
        }

        public Builder withHost(String host) {
            configuration.set(HOST, host);
            return this;
        }

        public Builder withPort(int port) {
            configuration.set(PORT, port);
            return this;
        }

        public Builder withUsername(String username) {
            configuration.set(USERNAME, username);
            return this;
        }

        public Builder withPassword(char[] password) {
            configuration.set(PASSWORD, String.valueOf(password));
            return this;
        }

        public Builder withConnectTimeout(int timeout) {
            configuration.set(CONNECT_TIMEOUT, timeout);
            return this;
        }

        public Builder withTimeout(int timeout) {
            configuration.set(TIMEOUT, timeout);
            return this;
        }

        public Builder withIdentity(String identity) {
            configuration.set(IDENTITIES, identity);
            return this;
        }

        public Builder withKnownHosts(String knownHosts) {
            configuration.set(KNOWN_HOSTS, knownHosts);
            return this;
        }

        public Builder withPoolConfig(PoolConfig config) {
            configuration.set(POOL_CONFIG, config);
            return this;
        }


        public Builder withProxy(Proxy proxy) {
            configuration.set(PROXY, proxy);
            return this;
        }

        public Builder withProperties(Properties properties) {
            if (properties == null) properties = new Properties();
            Map<String, String> config = properties.entrySet().stream()
                    .collect(Collectors.toMap(entry -> String.valueOf(entry.getKey()),
                            entry -> String.valueOf(entry.getValue())));
            configuration.set(CONFIG, config);
            return this;
        }

        public Builder withBucket(String bucket) {
            configuration.set(BUCKET, bucket);
            return this;
        }

        public Builder withDefaultDirectory(String path) {
            configuration.set(DEFAULT_DIR, path);
            return this;
        }

        public Builder withProtocol(String protocol) {
            configuration.set(PROTOCOL, protocol);
            return this;
        }

        public FileSystemConfiguration build() {
            return this.configuration;
        }
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
