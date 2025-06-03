package org.jdkxx.commons.configuration;

import java.util.Objects;

public class ConfigOption<T> {
    /**
     * The current key for that config option.
     */
    private final String key;

    /**
     * The default value for this option.
     */
    private final T defaultValue;

    /**
     * Type of the value that this ConfigOption describes.
     *
     * <ul>
     *   <li>typeClass == atomic class (e.g. {@code Integer.class}) -> {@code ConfigOption<Integer>}
     *   <li>typeClass == {@code Map.class} -> {@code ConfigOption<Map<String, String>>}
     *   <li>typeClass == atomic class and isList == true for {@code ConfigOption<List<Integer>>}
     * </ul>
     */
    private final Class<?> clazz;

    private final boolean isList;

    // ------------------------------------------------------------------------

    Class<?> getClazz() {
        return clazz;
    }

    boolean isList() {
        return isList;
    }

    /**
     * Creates a new config option with fallback keys.
     *
     * @param key          The current key for that config option
     * @param clazz        describes a type of the ConfigOption, see the description of the clazz field
     * @param defaultValue The default value for this option
     * @param isList       tells if the ConfigOption describes a list option, see the description of the clazz
     *                     field
     */
    ConfigOption(
            String key,
            Class<?> clazz,
            T defaultValue,
            boolean isList) {
        this.key = Objects.requireNonNull(key);
        this.defaultValue = defaultValue;
        this.clazz = Objects.requireNonNull(clazz);
        this.isList = isList;
    }

    /**
     * Gets the configuration key.
     *
     * @return The configuration key
     */
    public String key() {
        return key;
    }

    /**
     * Checks if this option has a default value.
     *
     * @return True if it has a default value, false if not.
     */
    public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    /**
     * Returns the default value, or null, if there is no default value.
     *
     * @return The default value, or null.
     */
    public T defaultValue() {
        return defaultValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && o.getClass() == ConfigOption.class) {
            ConfigOption<?> that = (ConfigOption<?>) o;
            return this.key.equals(that.key)
                    && (this.defaultValue == null
                    ? that.defaultValue == null
                    : (that.defaultValue != null
                    && this.defaultValue.equals(that.defaultValue)));
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return 31 * key.hashCode()
                + 17 * (defaultValue != null ? defaultValue.hashCode() : 0);
    }

    @Override
    public String toString() {
        return String.format("Key: '%s' , default: %s", key, defaultValue);
    }
}
