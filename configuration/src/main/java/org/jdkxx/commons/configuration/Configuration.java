package org.jdkxx.commons.configuration;

import com.google.common.base.Preconditions;

import java.io.Serializable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.jdkxx.commons.configuration.ConfigurationUtils.*;

public class Configuration implements Serializable, Cloneable {
    static final float HASH_MAP_DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * Stores the concrete key/value pairs of this configuration object.
     *
     * <p>NOTE: This map stores the values that are actually used and does not include any escaping
     * that is required by the standard YAML syntax.
     */
    protected final Map<String, Object> confData;

    /**
     * Creates a new empty configuration.
     */
    public Configuration() {
        this.confData = new HashMap<>();
    }

    /**
     * Creates a new configuration with the copy of the given configuration.
     *
     * @param other The configuration to copy the entries from.
     */
    public Configuration(Configuration other) {
        this.confData = new HashMap<>(other.confData);
    }

    /**
     * Creates a new configuration initialized with the options of the given map.
     */
    public static Configuration fromMap(Map<String, String> map) {
        final Configuration configuration = new Configuration();
        map.forEach(configuration::setString);
        return configuration;
    }

    /**
     * Adds the given key/value pair to the configuration object. We encourage users and developers
     * to always use ConfigOption for setting the configurations if possible, for its rich
     * description, type, default-value and other supports. The string-key-based setter should only
     * be used when ConfigOption is not applicable, e.g., the key is programmatically generated in
     * runtime.
     *
     * @param key   the key of the key/value pair to be added
     * @param value the value of the key/value pair to be added
     */
    public void setString(String key, String value) {
        setValueInternal(key, value);
    }

    /**
     * Returns the value associated with the given key as a string. We encourage users and
     * developers to always use ConfigOption for getting the configurations if possible, for its
     * rich description, type, default-value and other supports. The string-key-based getter should
     * only be used when ConfigOption is not applicable, e.g., the key is programmatically generated
     * in runtime.
     *
     * @param key          the key pointing to the associated value
     * @param defaultValue the default value which is returned in case there is no value associated
     *                     with the given key
     * @return the (default) value associated with the given key
     */
    public String getString(String key, String defaultValue) {
        return getRawValue(key)
                .map(o -> convertToString(o, false))
                .orElse(defaultValue);
    }

    /**
     * Returns the value associated with the given key as a byte array.
     *
     * @param key          The key pointing to the associated value.
     * @param defaultValue The default value which is returned in case there is no value associated
     *                     with the given key.
     * @return the (default) value associated with the given key.
     */
    public byte[] getBytes(String key, byte[] defaultValue) {
        return getRawValue(key)
                .map(
                        o -> {
                            if (o.getClass().equals(byte[].class)) {
                                return (byte[]) o;
                            } else {
                                throw new IllegalArgumentException(
                                        String.format(
                                                "Configuration cannot evaluate value %s as a byte[] value",
                                                o));
                            }
                        })
                .orElse(defaultValue);
    }

    /**
     * Adds the given byte array to the configuration object. If key is <code>null</code> then
     * nothing is added.
     *
     * @param key   The key under which the bytes are added.
     * @param bytes The bytes to be added.
     */
    public void setBytes(String key, byte[] bytes) {
        setValueInternal(key, bytes);
    }

    /**
     * Returns the value associated with the given config option as a string.
     *
     * @param configOption The configuration option
     * @return the (default) value associated with the given config option
     */
    public String getValue(ConfigOption<?> configOption) {
        return Optional.ofNullable(
                        getRawValueFromOption(configOption).orElseGet(configOption::defaultValue))
                .map(String::valueOf)
                .orElse(null);
    }

    /**
     * Returns the value associated with the given config option as an enum.
     *
     * @param enumClass    The return enum class
     * @param configOption The configuration option
     * @throws IllegalArgumentException If the string associated with the given config option cannot
     *                                  be parsed as a value of the provided enum class.
     */
    public <T extends Enum<T>> T getEnum(
            final Class<T> enumClass, final ConfigOption<String> configOption) {
        Objects.requireNonNull(enumClass, "enumClass must not be null");
        Objects.requireNonNull(configOption, "configOption must not be null");

        Object rawValue = getRawValueFromOption(configOption).orElseGet(configOption::defaultValue);
        try {
            return convertToEnum(rawValue, enumClass);
        } catch (IllegalArgumentException ex) {
            final String errorMessage =
                    String.format(
                            "Value for config option %s must be one of %s (was %s)",
                            configOption.key(),
                            Arrays.toString(enumClass.getEnumConstants()),
                            rawValue);
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Returns the keys of all key/value pairs stored inside this configuration object.
     *
     * @return the keys of all key/value pairs stored inside this configuration object
     */
    public Set<String> keySet() {
        synchronized (this.confData) {
            return new HashSet<>(this.confData.keySet());
        }
    }

    /**
     * Adds all entries in this {@code Configuration} to the given {@link Properties}.
     */
    public void addAllToProperties(Properties props) {
        synchronized (this.confData) {
            props.putAll(this.confData);
        }
    }

    public void addAll(Configuration other) {
        synchronized (this.confData) {
            synchronized (other.confData) {
                this.confData.putAll(other.confData);
            }
        }
    }

    /**
     * Adds all entries from the given configuration into this configuration. The keys are prepended
     * with the given prefix.
     *
     * @param other  The configuration whose entries are added to this configuration.
     * @param prefix The prefix to prepend.
     */
    public void addAll(Configuration other, String prefix) {
        final StringBuilder bld = new StringBuilder();
        bld.append(prefix);
        final int pl = bld.length();

        synchronized (this.confData) {
            synchronized (other.confData) {
                for (Map.Entry<String, Object> entry : other.confData.entrySet()) {
                    bld.setLength(pl);
                    bld.append(entry.getKey());
                    this.confData.put(bld.toString(), entry.getValue());
                }
            }
        }
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Checks whether there is an entry with the specified key.
     *
     * @param key key of entry
     * @return true if the key is stored, false otherwise
     */
    public boolean containsKey(String key) {
        synchronized (this.confData) {
            return this.confData.containsKey(key);
        }
    }

    /**
     * Checks whether there is an entry for the given config option.
     *
     * @param configOption The configuration option
     * @return <tt>true</tt> if a valid (current or deprecated) key of the config option is stored,
     * <tt>false</tt> otherwise
     */
    public boolean contains(ConfigOption<?> configOption) {
        synchronized (this.confData) {
            final BiFunction<String, Boolean, Optional<Boolean>> applier =
                    (key, canBePrefixMap) -> {
                        if (canBePrefixMap && containsPrefixMap(this.confData, key)
                                || this.confData.containsKey(key)) {
                            return Optional.of(true);
                        }
                        return Optional.empty();
                    };
            return applyWithOption(configOption, applier).orElse(false);
        }
    }

    /**
     * Please check the Javadoc of {@link #getRawValueFromOption(ConfigOption)}. If no keys are
     * found in {@link Configuration}, default value of the given option will return. Please make
     * sure there will be at least one value available. Otherwise, an NPE will be thrown by Flink
     * when the value is used.
     *
     * <p>NOTE: current logic is not able to get the default value of the fallback key's
     * ConfigOption, in case the given ConfigOption has no default value. If you want to use
     * fallback key, please make sure its value could be found in {@link Configuration} at runtime.
     *
     * @param option metadata of the option to read
     * @return the value of the given option
     */
    public <T> T get(ConfigOption<T> option) {
        return getOptional(option).orElseGet(option::defaultValue);
    }

    /**
     * Returns the value associated with the given config option as a T. If no value is mapped under
     * any key of the option, it returns the specified default instead of the option's default
     * value.
     *
     * @param configOption    The configuration option
     * @param overrideDefault The value to return if no value was mapper for any key of the option
     * @return the configured value associated with the given config option, or the overrideDefault
     */
    public <T> T get(ConfigOption<T> configOption, T overrideDefault) {
        return getOptional(configOption).orElse(overrideDefault);
    }

    public <T> Optional<T> getOptional(ConfigOption<T> option) {
        Optional<Object> rawValue = getRawValueFromOption(option);
        Class<?> clazz = option.getClazz();

        try {
            if (option.isList()) {
                return rawValue.map(v -> convertToList(v, clazz, false));
            } else {
                return rawValue.map(v -> convertValue(v, clazz, false));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    GlobalConfiguration.isSensitive(option.key())
                            ? String.format("Could not parse value for key '%s'.", option.key())
                            : String.format(
                            "Could not parse value '%s' for key '%s'.",
                            rawValue.map(Object::toString).orElse(""), option.key()),
                    e);
        }
    }

    public <T> Configuration set(ConfigOption<T> option, T value) {
        setValueInternal(option.key(), value);
        return this;
    }

    public Map<String, String> toMap() {
        synchronized (this.confData) {
            Map<String, String> ret = newHashMapWithExpectedSize(this.confData.size());
            for (Map.Entry<String, Object> entry : confData.entrySet()) {
                ret.put(
                        entry.getKey(),
                        convertToString(entry.getValue(), false));
            }
            return ret;
        }
    }

    /**
     * Removes a given a config option from the configuration.
     *
     * @param configOption config option to remove
     * @param <T>          Type of the config option
     * @return true is config has been removed, false otherwise
     */
    public <T> boolean removeConfig(ConfigOption<T> configOption) {
        synchronized (this.confData) {
            final BiFunction<String, Boolean, Optional<Boolean>> applier =
                    (key, canBePrefixMap) -> {
                        if (canBePrefixMap && removePrefixMap(this.confData, key)
                                || this.confData.remove(key) != null) {
                            return Optional.of(true);
                        }
                        return Optional.empty();
                    };
            return applyWithOption(configOption, applier).orElse(false);
        }
    }

    /**
     * Removes the given key from the configuration.
     *
     * @param key key of a config option to remove
     * @return true is config has been removed, false otherwise
     */
    public boolean removeKey(String key) {
        synchronized (this.confData) {
            boolean removed = this.confData.remove(key) != null;
            removed |= removePrefixMap(confData, key);
            return removed;
        }
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (String s : this.confData.keySet()) {
            hash ^= s.hashCode();
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof Configuration) {
            Map<String, Object> otherConf = ((Configuration) obj).confData;

            for (Map.Entry<String, Object> e : this.confData.entrySet()) {
                Object thisVal = e.getValue();
                Object otherVal = otherConf.get(e.getKey());

                if (!thisVal.getClass().equals(byte[].class)) {
                    if (!thisVal.equals(otherVal)) {
                        return false;
                    }
                } else if (otherVal.getClass().equals(byte[].class)) {
                    if (!Arrays.equals((byte[]) thisVal, (byte[]) otherVal)) {
                        return false;
                    }
                } else {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return hideSensitiveValues(
                this.confData.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                                        entry -> entry.getValue().toString())))
                .toString();
    }

    static <K, V> HashMap<K, V> newHashMapWithExpectedSize(int expectedSize) {
        return new HashMap<>(
                computeRequiredCapacity(expectedSize, HASH_MAP_DEFAULT_LOAD_FACTOR),
                HASH_MAP_DEFAULT_LOAD_FACTOR);
    }

    static int computeRequiredCapacity(int expectedSize, float loadFactor) {
        Preconditions.checkArgument(expectedSize >= 0);
        Preconditions.checkArgument(loadFactor > 0f);
        if (expectedSize <= 2) {
            return expectedSize + 1;
        }
        return expectedSize < (Integer.MAX_VALUE / 2 + 1)
                ? (int) Math.ceil(expectedSize / loadFactor)
                : Integer.MAX_VALUE;
    }

    /**
     * This method will do the following steps to get the value of a config option:
     *
     * <p>1. get the value from {@link Configuration}. <br>
     * 2. If key is not found, try to get the value with fallback keys from {@link Configuration}
     * <br>
     * 3. if no fallback keys are found, return {@link Optional#empty()}. <br>
     *
     * @return the value of the configuration or {@link Optional#empty()}.
     */
    private Optional<Object> getRawValueFromOption(ConfigOption<?> configOption) {
        return applyWithOption(configOption, this::getRawValue);
    }

    private <T> Optional<T> applyWithOption(
            ConfigOption<?> option, BiFunction<String, Boolean, Optional<T>> applier) {
        final boolean canBePrefixMap = canBePrefixMap(option);
        return applier.apply(option.key(), canBePrefixMap);
    }

    /**
     * Maps can be represented in two ways.
     *
     * <p>With constant key space:
     *
     * <pre>
     *     avro-confluent.properties = schema: 1, other-prop: 2
     * </pre>
     *
     * <p>Or with variable key space (i.e., prefix notation):
     *
     * <pre>
     *     avro-confluent.properties.schema = 1
     *     avro-confluent.properties.other-prop = 2
     * </pre>
     */
    static boolean canBePrefixMap(ConfigOption<?> configOption) {
        return configOption.getClazz() == Map.class && !configOption.isList();
    }

    <T> void setValueInternal(String key, T value) {
        if (key == null) {
            throw new NullPointerException("Key must not be null.");
        }
        if (value == null) {
            throw new NullPointerException("Value must not be null.");
        }

        synchronized (this.confData) {
            this.confData.put(key, value);
        }
    }

    private Optional<Object> getRawValue(String key) {
        return getRawValue(key, false);
    }

    private Optional<Object> getRawValue(String key, boolean canBePrefixMap) {
        if (key == null) {
            throw new NullPointerException("Key must not be null.");
        }

        synchronized (this.confData) {
            final Object valueFromExactKey = this.confData.get(key);
            if (!canBePrefixMap || valueFromExactKey != null) {
                return Optional.ofNullable(valueFromExactKey);
            }
            final Map<String, String> valueFromPrefixMap =
                    convertToPropertiesPrefixed(confData, key, false);
            if (valueFromPrefixMap.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(valueFromPrefixMap);
        }
    }
}
