package org.jdkxx.commons.configuration;

import com.google.common.base.Preconditions;
import org.jdkxx.commons.configuration.options.CoreOptions;

import javax.annotation.Nonnull;
import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.jdkxx.commons.configuration.GlobalConfiguration.HIDDEN_CONTENT;
import static org.jdkxx.commons.configuration.StructuredOptionsSplitter.*;

/**
 * Utility class for {@link Configuration} related helper functions.
 */
public class ConfigurationUtils {
    private static final String[] EMPTY = new String[0];

    /**
     * Extracts the task manager directories for temporary files as defined by {@link
     * org.jdkxx.commons.configuration.options.CoreOptions#TMP_DIRS}.
     *
     * @param configuration configuration object
     * @return array of configured directories (in order)
     */
    @Nonnull
    public static String[] parseTempDirectories(Configuration configuration) {
        return splitPaths(configuration.get(CoreOptions.TMP_DIRS));
    }

    @Nonnull
    public static String[] splitPaths(@Nonnull String separatedPaths) {
        return !separatedPaths.isEmpty()
                ? separatedPaths.split(",|" + File.pathSeparator)
                : EMPTY;
    }

    /**
     * Picks a temporary directory randomly from the given configuration.
     *
     * @param configuration to extract the temp directory from
     * @return a randomly picked temporary directory
     */
    @Nonnull
    public static File getRandomTempDirectory(Configuration configuration) {
        final String[] tmpDirectories = parseTempDirectories(configuration);

        Preconditions.checkState(
                tmpDirectories.length > 0,
                String.format(
                        "No temporary directory has been specified for %s",
                        CoreOptions.TMP_DIRS.key()));

        final int randomIndex = ThreadLocalRandom.current().nextInt(tmpDirectories.length);

        return new File(tmpDirectories[randomIndex]);
    }

    /**
     * Parses a string as a map of strings. The expected format of the map to be parsed` by FLINK
     * parser is:
     *
     * <pre>
     * key1:value1,key2:value2
     * </pre>
     *
     * <p>The expected format of the map to be parsed by standard YAML parser is:
     *
     * <pre>
     * {key1: value1, key2: value2}
     * </pre>
     *
     * <p>Parts of the string can be escaped by wrapping with single or double quotes.
     *
     * @param stringSerializedMap a string to parse
     * @return parsed map
     */
    public static Map<String, String> parseStringToMap(String stringSerializedMap, boolean standardYaml) {
        return convertToProperties(stringSerializedMap, standardYaml);
    }

    public static String parseMapToString(Map<String, String> map, boolean standardYaml) {
        return convertToString(map, standardYaml);
    }

    /**
     * Creates a new {@link Configuration} from the given {@link Properties}.
     *
     * @param properties to convert into a {@link Configuration}
     * @return {@link Configuration} which has been populated by the values of the given {@link
     * Properties}
     */
    @Nonnull
    public static Configuration createConfiguration(Properties properties) {
        final Configuration configuration = new Configuration();

        final Set<String> propertyNames = properties.stringPropertyNames();

        for (String propertyName : propertyNames) {
            configuration.setString(propertyName, properties.getProperty(propertyName));
        }

        return configuration;
    }

    /**
     * Replaces values whose keys are sensitive according to {@link
     * GlobalConfiguration#isSensitive(String)} with {@link GlobalConfiguration#HIDDEN_CONTENT}.
     *
     * <p>This can be useful when displaying configuration values.
     *
     * @param keyValuePairs for which to hide sensitive values
     * @return A map where all sensitive value is hidden
     */
    @Nonnull
    public static Map<String, String> hideSensitiveValues(Map<String, String> keyValuePairs) {
        final HashMap<String, String> result = new HashMap<>();

        for (Map.Entry<String, String> keyValuePair : keyValuePairs.entrySet()) {
            if (GlobalConfiguration.isSensitive(keyValuePair.getKey())) {
                result.put(keyValuePair.getKey(), HIDDEN_CONTENT);
            } else {
                result.put(keyValuePair.getKey(), keyValuePair.getValue());
            }
        }

        return result;
    }

    /**
     * Creates a dynamic parameter list {@code String} of the passed configuration map.
     *
     * @param config A {@code Map} containing parameter/value entries that shall be used in the
     *               dynamic parameter list.
     * @return The dynamic parameter list {@code String}.
     */
    public static String assembleDynamicConfigsStr(final Map<String, String> config) {
        return config.entrySet().stream()
                .map(e -> String.format("-D %s=%s", e.getKey(), e.getValue()))
                .collect(Collectors.joining(" "));
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
    public static boolean canBePrefixMap(ConfigOption<?> configOption) {
        return configOption.getClazz() == Map.class && !configOption.isList();
    }

    /**
     * Filter condition for prefix map keys.
     */
    public static boolean filterPrefixMapKey(String key, String candidate) {
        final String prefixKey = key + ".";
        return candidate.startsWith(prefixKey);
    }

    static Map<String, String> convertToPropertiesPrefixed(
            Map<String, Object> confData, String key, boolean standardYaml) {
        final String prefixKey = key + ".";
        return confData.keySet().stream()
                .filter(k -> k.startsWith(prefixKey))
                .collect(
                        Collectors.toMap(
                                k -> k.substring(prefixKey.length()),
                                k -> convertToString(confData.get(k), standardYaml)));
    }

    static boolean containsPrefixMap(Map<String, Object> confData, String key) {
        return confData.keySet().stream().anyMatch(candidate -> filterPrefixMapKey(key, candidate));
    }

    static boolean removePrefixMap(Map<String, Object> confData, String key) {
        final List<String> prefixKeys =
                confData.keySet().stream()
                        .filter(candidate -> filterPrefixMapKey(key, candidate))
                        .collect(Collectors.toList());
        prefixKeys.forEach(confData::remove);
        return !prefixKeys.isEmpty();
    }

    // Make sure that we cannot instantiate this class
    private ConfigurationUtils() {
    }

    // --------------------------------------------------------------------------------------------
    //  Type conversion
    // --------------------------------------------------------------------------------------------

    /**
     * Tries to convert the raw value into the provided type.
     *
     * @param rawValue rawValue to convert into the provided type clazz
     * @param clazz    clazz specifying the target type
     * @param <T>      type of the result
     * @return the converted value if rawValue is of type clazz
     * @throws IllegalArgumentException if the rawValue cannot be converted in the specified target
     *                                  type clazz
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertValue(Object rawValue, Class<?> clazz, boolean standardYaml) {
        if (Integer.class.equals(clazz)) {
            return (T) convertToInt(rawValue);
        } else if (Long.class.equals(clazz)) {
            return (T) convertToLong(rawValue);
        } else if (Boolean.class.equals(clazz)) {
            return (T) convertToBoolean(rawValue);
        } else if (Float.class.equals(clazz)) {
            return (T) convertToFloat(rawValue);
        } else if (Double.class.equals(clazz)) {
            return (T) convertToDouble(rawValue);
        } else if (String.class.equals(clazz)) {
            return (T) convertToString(rawValue, standardYaml);
        } else if (clazz.isEnum()) {
            return (T) convertToEnum(rawValue, (Class<? extends Enum<?>>) clazz);
        } else if (clazz == Duration.class) {
            return (T) convertToDuration(rawValue);
        } else if (clazz == Map.class) {
            return (T) convertToProperties(rawValue, standardYaml);
        } else if (clazz == rawValue.getClass()) {
            return (T) rawValue;
        }

        throw new IllegalArgumentException("Unsupported type: " + clazz);
    }

    @SuppressWarnings("unchecked")
    static <T> T convertToList(Object rawValue, Class<?> atomicClass, boolean standardYaml) {
        if (rawValue instanceof List) {
            return (T) rawValue;
        } else if (standardYaml) {
            try {
                List<Object> data =
                        YamlParserUtils.convertToObject(rawValue.toString(), List.class);
                // The YAML parser conversion results in data of type List<Map<Object, Object>>,
                // such as List<Map<Object, Boolean>>. However, ConfigOption currently requires that
                // the data for a Map type be strictly of the type Map<String, String>. Therefore, we
                // convert each map in the list to Map<String, String>.
                if (atomicClass == Map.class) {
                    return (T)
                            data.stream()
                                    .map(map -> convertToStringMap((Map<Object, Object>) map, true))
                                    .collect(Collectors.toList());
                }

                return (T)
                        data.stream()
                                .map(s -> convertValue(s, atomicClass, true))
                                .collect(Collectors.toList());
            } catch (Exception e) {
                // Fallback to a legacy pattern
                return convertToListWithLegacyProperties(rawValue, atomicClass);
            }
        } else {
            return convertToListWithLegacyProperties(rawValue, atomicClass);
        }
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static <T> T convertToListWithLegacyProperties(Object rawValue, Class<?> atomicClass) {
        return (T)
                StructuredOptionsSplitter.splitEscaped(rawValue.toString(), ';').stream()
                        .map(s -> convertValue(s, atomicClass, false))
                        .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> convertToProperties(Object o, boolean standardYaml) {
        if (o instanceof Map) {
            return (Map<String, String>) o;
        } else if (standardYaml) {
            try {
                Map<Object, Object> map = YamlParserUtils.convertToObject(o.toString(), Map.class);
                return convertToStringMap(map, true);
            } catch (Exception e) {
                // Fallback to a legacy pattern
                return convertToPropertiesWithLegacyPattern(o);
            }
        } else {
            return convertToPropertiesWithLegacyPattern(o);
        }
    }

    private static Map<String, String> convertToStringMap(
            Map<Object, Object> map, boolean standardYaml) {
        return map.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                entry -> convertToString(entry.getKey(), standardYaml),
                                entry -> convertToString(entry.getValue(), standardYaml)));
    }

    static String convertToString(Object o, boolean standardYaml) {
        if (standardYaml) {
            if (o.getClass() == String.class) {
                return (String) o;
            } else {
                return YamlParserUtils.toYAMLString(o);
            }
        }

        if (o.getClass() == String.class) {
            return (String) o;
        } else if (o.getClass() == Duration.class) {
            Duration duration = (Duration) o;
            return TimeUtils.formatWithHighestUnit(duration);
        } else if (o instanceof List) {
            return ((List<?>) o)
                    .stream()
                    .map(e -> escapeWithSingleQuote(convertToString(e, false), ";"))
                    .collect(Collectors.joining(";"));
        } else if (o instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) o;
            return map
                    .entrySet().stream()
                    .map(
                            e -> {
                                String escapedKey =
                                        escapeWithSingleQuote(e.getKey().toString(), ":");
                                String escapedValue =
                                        escapeWithSingleQuote(e.getValue().toString(), ":");

                                return escapeWithSingleQuote(
                                        escapedKey + ":" + escapedValue, ",");
                            })
                    .collect(Collectors.joining(","));
        }

        return o.toString();
    }

    @Nonnull
    private static Map<String, String> convertToPropertiesWithLegacyPattern(Object o) {
        List<String> listOfRawProperties =
                splitEscaped(o.toString(), ',');
        return listOfRawProperties.stream()
                .map(s -> splitEscaped(s, ':'))
                .peek(
                        pair -> {
                            if (pair.size() != 2) {
                                throw new IllegalArgumentException(
                                        "Map item is not a key-value pair (missing ':'?)");
                            }
                        })
                .collect(Collectors.toMap(a -> a.get(0), a -> a.get(1)));
    }

    @SuppressWarnings("unchecked")
    public static <E extends Enum<?>> E convertToEnum(Object o, Class<E> clazz) {
        if (o.getClass().equals(clazz)) {
            return (E) o;
        }

        return Arrays.stream(clazz.getEnumConstants())
                .filter(
                        e ->
                                e.toString()
                                        .toUpperCase(Locale.ROOT)
                                        .equals(o.toString().toUpperCase(Locale.ROOT)))
                .findAny()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        String.format(
                                                "Could not parse value for enum %s. Expected one of: [%s]",
                                                clazz, Arrays.toString(clazz.getEnumConstants()))));
    }

    static Duration convertToDuration(Object o) {
        if (o.getClass() == Duration.class) {
            return (Duration) o;
        }

        return TimeUtils.parseDuration(o.toString());
    }

    static Integer convertToInt(Object o) {
        if (o.getClass() == Integer.class) {
            return (Integer) o;
        } else if (o.getClass() == Long.class) {
            long value = (Long) o;
            if (value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE) {
                return (int) value;
            } else {
                throw new IllegalArgumentException(
                        String.format(
                                "Configuration value %s overflows/underflow's the integer type.",
                                value));
            }
        }

        return Integer.parseInt(o.toString());
    }

    static Long convertToLong(Object o) {
        if (o.getClass() == Long.class) {
            return (Long) o;
        } else if (o.getClass() == Integer.class) {
            return ((Integer) o).longValue();
        }

        return Long.parseLong(o.toString());
    }

    static Boolean convertToBoolean(Object o) {
        if (o.getClass() == Boolean.class) {
            return (Boolean) o;
        }
        switch (o.toString().toUpperCase()) {
            case "TRUE":
                return true;
            case "FALSE":
                return false;
            default:
                throw new IllegalArgumentException(
                        String.format(
                                "Unrecognized option for boolean: %s. Expected either true or false(case insensitive)",
                                o));
        }
    }

    static Float convertToFloat(Object o) {
        if (o.getClass() == Float.class) {
            return (Float) o;
        } else if (o.getClass() == Double.class) {
            double value = ((Double) o);
            if (value == 0.0
                    || (value >= Float.MIN_VALUE && value <= Float.MAX_VALUE)
                    || (value >= -Float.MAX_VALUE && value <= -Float.MIN_VALUE)) {
                return (float) value;
            } else {
                throw new IllegalArgumentException(
                        String.format(
                                "Configuration value %s overflows/underflow's the float type.",
                                value));
            }
        }

        return Float.parseFloat(o.toString());
    }

    static Double convertToDouble(Object o) {
        if (o.getClass() == Double.class) {
            return (Double) o;
        } else if (o.getClass() == Float.class) {
            return ((Float) o).doubleValue();
        }

        return Double.parseDouble(o.toString());
    }
}
