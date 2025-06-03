package org.jdkxx.commons.configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code ConfigOptions} are used to build a {@link ConfigOption}. The option is typically built in
 * one of the following patterns:
 *
 * <pre>{@code
 * // simple string-valued option with a default value
 * ConfigOption<String> tempDirs = ConfigOptions
 *     .key("tmp.dir")
 *     .stringType()
 *     .defaultValue("/tmp");
 *
 * // simple integer-valued option with a default value
 * ConfigOption<Integer> parallelism = ConfigOptions
 *     .key("application.parallelism")
 *     .intType()
 *     .defaultValue(100);
 *
 * // option of list of integers with a default value
 * ConfigOption<Integer> parallelism = ConfigOptions
 *     .key("application.ports")
 *     .intType()
 *     .asList()
 *     .defaultValue(8000, 8001, 8002);
 *
 * // option with no default value
 * ConfigOption<String> userName = ConfigOptions
 *     .key("user.name")
 *     .stringType()
 *     .noDefaultValue();
 *
 * // option with deprecated keys to check
 * ConfigOption<Double> threshold = ConfigOptions
 *     .key("cpu.utilization.threshold")
 *     .doubleType()
 *     .defaultValue(0.9)
 *     .withDeprecatedKeys("cpu.threshold");
 * }</pre>
 */
public class ConfigOptions {

    /**
     * Not intended to be instantiated.
     */
    private ConfigOptions() {
    }

    public static OptionBuilder key(String key) {
        Objects.requireNonNull(key, "key must not be null.");
        return new OptionBuilder(key);
    }

    public static final class OptionBuilder {
        @SuppressWarnings("unchecked")
        private static final Class<Map<String, String>> PROPERTIES_MAP_CLASS =
                (Class<Map<String, String>>) (Class<?>) Map.class;

        private final String key;

        OptionBuilder(String key) {
            this.key = key;
        }

        public TypedConfigOptionBuilder<Boolean> booleanType() {
            return new TypedConfigOptionBuilder<>(key, Boolean.class);
        }

        public TypedConfigOptionBuilder<Integer> intType() {
            return new TypedConfigOptionBuilder<>(key, Integer.class);
        }

        public TypedConfigOptionBuilder<Long> longType() {
            return new TypedConfigOptionBuilder<>(key, Long.class);
        }

        public TypedConfigOptionBuilder<Float> floatType() {
            return new TypedConfigOptionBuilder<>(key, Float.class);
        }

        public TypedConfigOptionBuilder<Double> doubleType() {
            return new TypedConfigOptionBuilder<>(key, Double.class);
        }

        public TypedConfigOptionBuilder<String> stringType() {
            return new TypedConfigOptionBuilder<>(key, String.class);
        }

        public <T extends Enum<T>> TypedConfigOptionBuilder<T> enumType(Class<T> enumClass) {
            return new TypedConfigOptionBuilder<>(key, enumClass);
        }

        public TypedConfigOptionBuilder<Map<String, String>> mapType() {
            return new TypedConfigOptionBuilder<>(key, PROPERTIES_MAP_CLASS);
        }

        public <T> TypedConfigOptionBuilder<T> beanType(Class<T> beanClass) {
            return new TypedConfigOptionBuilder<>(key, beanClass);
        }

        /**
         * Creates an ConfigOption with the given default value.
         *
         * <p>This method does not accept "null". For options with no default value, choose one of
         * the {@code noDefaultValue} methods.
         *
         * @param value The default value for the config option
         * @param <T>   The type of the default value.
         * @return The config option with the default value.
         * @deprecated define the type explicitly first with one of the intType(), stringType(),
         * etc.
         */
        @Deprecated
        public <T> ConfigOption<T> defaultValue(T value) {
            Objects.requireNonNull(value);
            return new ConfigOption<>(key, value.getClass(), value, false);
        }

        /**
         * Creates a string-valued option with no default value. String-valued options are the only
         * ones that can have no default value.
         *
         * @return The created ConfigOption.
         * @deprecated define the type explicitly first with one of the intType(), stringType(),
         * etc.
         */
        @Deprecated
        public ConfigOption<String> noDefaultValue() {
            return new ConfigOption<>(key, String.class, null, false);
        }
    }

    public static class TypedConfigOptionBuilder<T> {
        private final String key;
        private final Class<T> clazz;

        TypedConfigOptionBuilder(String key, Class<T> clazz) {
            this.key = key;
            this.clazz = clazz;
        }

        public ListConfigOptionBuilder<T> asList() {
            return new ListConfigOptionBuilder<>(key, clazz);
        }

        /**
         * Creates an ConfigOption with the given default value.
         *
         * @param value The default value for the config option
         * @return The config option with the default value.
         */
        public ConfigOption<T> defaultValue(T value) {
            return new ConfigOption<>(key, clazz, value, false);
        }

        /**
         * Creates an ConfigOption without a default value.
         *
         * @return The config option without a default value.
         */
        public ConfigOption<T> noDefaultValue() {
            return new ConfigOption<>(key, clazz, null, false);
        }
    }

    public static class ListConfigOptionBuilder<E> {
        private final String key;
        private final Class<E> clazz;

        ListConfigOptionBuilder(String key, Class<E> clazz) {
            this.key = key;
            this.clazz = clazz;
        }

        /**
         * Creates an ConfigOption with the given default value.
         *
         * @param values The list of default values for the config option
         * @return The config option with the default value.
         */
        @SafeVarargs
        public final ConfigOption<List<E>> defaultValues(E... values) {
            return new ConfigOption<>(key, clazz, Arrays.asList(values), true);
        }

        /**
         * Creates an ConfigOption without a default value.
         *
         * @return The config option without a default value.
         */
        public ConfigOption<List<E>> noDefaultValue() {
            return new ConfigOption<>(key, clazz, null, true);
        }
    }
}
