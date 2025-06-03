package org.jdkxx.commons.filesystem.config;

import org.jdkxx.commons.configuration.ConfigOption;
import org.jdkxx.commons.configuration.Configuration;
import org.jdkxx.commons.filesystem.pool.PoolConfig;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class FileSystemEnvironment implements Map<String, Object> {
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String CONNECT_TIMEOUT = "connectTimeout";
    private static final String IDENTITIES = "identities";
    private static final String KNOWN_HOSTS = "knownHosts";
    private static final String PROXY = "proxy";
    private static final String CONFIG = "config";
    private static final String TIMEOUT = "timeOut";
    private static final String DEFAULT_DIR = "defaultDir";
    private static final String POOL_CONFIG = "poolConfig";
    private static final String BUCKET = "bucket";
    private static final String PROTOCOL = "protocol";
    private static final String PORT = "port";
    private final Map<String, Object> origin;

    /**
     * Creates a new environment.
     */
    public FileSystemEnvironment(Map<String, ?> map) {
        this.origin = Objects.requireNonNull(map).entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public String getUsername() {
        return (String) origin.get(USERNAME);
    }

    public String getPassword() {
        Object value = get(PASSWORD);
        return value != null ? String.valueOf(value) : null;
    }

    public String getBucketName() {
        Object value = get(BUCKET);
        return value != null ? String.valueOf(value) : null;
    }

    public PoolConfig getPoolConfig() {
        Object value = get(POOL_CONFIG);
        if (value instanceof PoolConfig) {
            return (PoolConfig) value;
        }
        return null;
    }

    public String getProtocol() {
        Object value = get(PROTOCOL);
        return value != null ? String.valueOf(value) : null;
    }

    public int getPort() {
        Object value = get(PORT);
        return value != null ? Integer.parseInt(String.valueOf(value)) : 80;
    }

    public String endpoint(String host) {
        return String.format("%s:%s", host, getPort());
    }

    @SuppressWarnings("unchecked")
    public <T> T getProperties(ConfigOption<T> option) {
        Object value = get(CONFIG);
        if (value instanceof Map) {
            Map<String, String> map = (Map<String, String>) value;
            return Configuration.fromMap(map).get(option);
        }
        return option.defaultValue();
    }

    @Override
    public int size() {
        return origin.size();
    }

    @Override
    public boolean isEmpty() {
        return origin.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return origin.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return origin.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        return origin.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        return origin.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        return origin.remove(key);
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ?> m) {
        origin.putAll(m);
    }

    @Override
    public void clear() {
        origin.clear();
    }

    @Override
    public @NotNull Set<String> keySet() {
        return origin.keySet();
    }

    @Override
    public @NotNull Collection<Object> values() {
        return origin.values();
    }

    @Override
    public @NotNull Set<Entry<String, Object>> entrySet() {
        return origin.entrySet();
    }

    public static FileSystemEnvironment copy(Map<String, ?> env) {
        return new FileSystemEnvironment(new HashMap<>(env));
    }
}
