package org.jdkxx.commons.filesystem.register;

import com.jcraft.jsch.Proxy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jdkxx.commons.filesystem.config.FileSystemConfiguration;
import org.jdkxx.commons.filesystem.config.options.FileSystemOptions;
import org.jdkxx.commons.filesystem.pool.PoolConfig;
import org.jdkxx.commons.lang.ClassUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.jdkxx.commons.filesystem.config.options.FileSystemOptions.*;

@Slf4j
public class FileSystemRegistry implements AutoCloseable {
    private final Map<URI, FileSystem> fileSystems = new ConcurrentHashMap<>(3);
    private final FileSystemConfiguration configuration;

    private FileSystemRegistry(FileSystemConfiguration configuration) {
        this.configuration = configuration;
    }

    private void register(ClassLoader classLoader) throws IOException {
        Objects.requireNonNull(configuration, "configuration must not be null");
        URI uri = configuration.getURI();
        FileSystem fs = FileSystems.newFileSystem(uri, configuration.getEnvironment(), classLoader);
        if (fs != null) {
            fileSystems.put(configuration.getURIWithUsername(), fs);
        }
    }

    public Path resolve(URI uri) throws URISyntaxException {
        String scheme = uri.getScheme();
        if (StringUtils.equalsIgnoreCase(scheme, "s3") || StringUtils.equalsIgnoreCase(scheme, "s3a")) {
            scheme = "s3";
        }
        String userInfo = uri.getUserInfo();
        String bucket;
        if (StringUtils.isNotBlank(userInfo)) {
            bucket = getBucket(uri.getPath());
        } else if (StringUtils.isBlank(userInfo) && StringUtils.isBlank(uri.getHost())) {
            userInfo = getUserInfo(uri.getAuthority());
            bucket = getBucket(uri.getPath());
        } else {
            bucket = uri.getHost();
        }

        for (URI key : fileSystems.keySet()) {
            if (StringUtils.equalsIgnoreCase(scheme, key.getScheme()) &&
                    (StringUtils.equalsIgnoreCase(bucket, getBucket(key.getPath())) ||
                            StringUtils.equalsIgnoreCase(userInfo, key.getUserInfo()))) {
                URI fact = new URI(key.getScheme(),
                        key.getUserInfo(),
                        key.getHost(),
                        key.getPort(),
                        getPath(uri.getPath(), bucket),
                        null,
                        null);
                return Paths.get(fact);
            }
        }
        throw new URISyntaxException(uri.toString(), "No registered FileSystem found for " + uri);
    }

    private String getBucket(String path) {
        if (StringUtils.startsWith(path, File.separator)) {
            path = path.substring(1);
        }
        String[] split = StringUtils.split(path, File.separator);
        if (split.length > 0) {
            return split[0];
        }
        return null;
    }

    private String getUserInfo(String authority) {
        String[] split = StringUtils.split(authority, "@");
        if (split.length > 0) {
            return split[0];
        }
        return null;
    }

    private String getPath(String path, String bucket) {
        List<String> token = new ArrayList<>();
        String[] parts = StringUtils.split(path, File.separator);
        for (String part : parts) {
            if (StringUtils.isNotBlank(part) && !StringUtils.equalsIgnoreCase(part, bucket)) {
                token.add(part);
            }
        }
        token.add(0, "");
        if (StringUtils.endsWith(path, File.separator)) {
            token.add("");
        }
        return StringUtils.join(token, File.separator);
    }

    public static Builder builder(String scheme) {
        return new Builder(scheme);
    }

    @Override
    public void close() throws Exception {
        for (FileSystem fileSystem : fileSystems.values()) {
            fileSystem.close();
        }
    }

    public static class Builder {
        private final FileSystemConfiguration configuration;

        Builder(String scheme) {
            configuration = new FileSystemConfiguration(scheme);
        }

        public Builder withHost(String host) {
            configuration.set(FileSystemOptions.HOST, host);
            return this;
        }

        public Builder withPort(int port) {
            configuration.set(FileSystemOptions.PORT, port);
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
            if (properties != null) {
                Map<String, String> map = new HashMap<>();
                properties.forEach((key, value) -> map.put(String.valueOf(key), String.valueOf(value)));
                configuration.set(CONFIG, map);
            }
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

        public FileSystemRegistry build() throws IOException {
            ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
            FileSystemRegistry registry = new FileSystemRegistry(configuration);
            registry.register(classLoader);
            return registry;
        }
    }
}
