package org.jdkxx.commons.configuration;

import lombok.Getter;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class TimeUtils {
    private static final Map<String, ChronoUnit> LABEL_TO_UNIT_MAP =
            Collections.unmodifiableMap(initMap());

    /**
     * Pretty prints the duration as the lowest granularity unit that does not lose precision.
     *
     * <p>Examples:
     *
     * <pre>{@code
     * Duration.ofMilliseconds(60000) will be printed as 1 min
     * Duration.ofHours(1).plusSeconds(1) will be printed as 3601 s
     * }</pre>
     *
     * <b>NOTE:</b> It supports only durations that fit into long.
     */
    public static String formatWithHighestUnit(Duration duration) {
        long nanos = duration.toNanos();

        TimeUnit highestIntegerUnit = getHighestIntegerUnit(nanos);
        return String.format(
                "%d %s",
                nanos / highestIntegerUnit.unit.getDuration().toNanos(),
                highestIntegerUnit.getLabels().get(0));
    }

    private static TimeUnit getHighestIntegerUnit(long nanos) {
        if (nanos == 0) {
            return TimeUnit.MILLISECONDS;
        }

        final List<TimeUnit> orderedUnits =
                Arrays.asList(
                        TimeUnit.NANOSECONDS,
                        TimeUnit.MICROSECONDS,
                        TimeUnit.MILLISECONDS,
                        TimeUnit.SECONDS,
                        TimeUnit.MINUTES,
                        TimeUnit.HOURS,
                        TimeUnit.DAYS);

        TimeUnit highestIntegerUnit = null;
        for (TimeUnit timeUnit : orderedUnits) {
            if (nanos % timeUnit.unit.getDuration().toNanos() != 0) {
                break;
            }
            highestIntegerUnit = timeUnit;
        }

        return checkNotNull(highestIntegerUnit, "Should find a highestIntegerUnit.");
    }

    /**
     * Parse the given string to a java {@link Duration}. The string is in format "{length
     * value}{time unit label}", e.g. "123ms", "321 s". If no time unit label is specified, it will
     * be considered as milliseconds.
     *
     * <p>Supported time unit labels are:
     *
     * <ul>
     *   <li>DAYS： "d", "day"
     *   <li>HOURS： "h", "hour"
     *   <li>MINUTES： "m", "min", "minute"
     *   <li>SECONDS： "s", "sec", "second"
     *   <li>MILLISECONDS： "ms", "milli", "millisecond"
     *   <li>MICROSECONDS： "µs", "micro", "microsecond"
     *   <li>NANOSECONDS： "ns", "nano", "nanosecond"
     * </ul>
     *
     * @param text string to parse.
     */
    public static Duration parseDuration(String text) {
        checkNotNull(text);

        final String trimmed = text.trim();
        checkArgument(!trimmed.isEmpty(), "argument is an empty- or whitespace-only string");

        final int len = trimmed.length();
        int pos = 0;

        char current;
        while (pos < len && (current = trimmed.charAt(pos)) >= '0' && current <= '9') {
            pos++;
        }

        final String number = trimmed.substring(0, pos);
        final String unitLabel = trimmed.substring(pos).trim().toLowerCase(Locale.US);

        if (number.isEmpty()) {
            throw new NumberFormatException("text does not start with a number");
        }

        final long value;
        try {
            value = Long.parseLong(number); // this throws a NumberFormatException on overflow
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "The value '"
                            + number
                            + "' cannot be re represented as 64bit number (numeric overflow).");
        }

        if (unitLabel.isEmpty()) {
            return Duration.of(value, ChronoUnit.MILLIS);
        }

        ChronoUnit unit = LABEL_TO_UNIT_MAP.get(unitLabel);
        if (unit != null) {
            return Duration.of(value, unit);
        } else {
            throw new IllegalArgumentException(
                    "Time interval unit label '"
                            + unitLabel
                            + "' does not match any of the recognized units: "
                            + TimeUnit.getAllUnits());
        }
    }

    private static Map<String, ChronoUnit> initMap() {
        Map<String, ChronoUnit> labelToUnit = new HashMap<>();
        for (TimeUnit timeUnit : TimeUnit.values()) {
            for (String label : timeUnit.getLabels()) {
                labelToUnit.put(label, timeUnit.getUnit());
            }
        }
        return labelToUnit;
    }

    /**
     * Enum, which defines time unit, mostly used to parse value from a configuration file.
     */
    @Getter
    private enum TimeUnit {
        DAYS(ChronoUnit.DAYS, singular("d"), plural("day")),
        HOURS(ChronoUnit.HOURS, singular("h"), plural("hour")),
        MINUTES(ChronoUnit.MINUTES, singular("min"), singular("m"), plural("minute")),
        SECONDS(ChronoUnit.SECONDS, singular("s"), plural("sec"), plural("second")),
        MILLISECONDS(ChronoUnit.MILLIS, singular("ms"), plural("milli"), plural("millisecond")),
        MICROSECONDS(ChronoUnit.MICROS, singular("µs"), plural("micro"), plural("microsecond")),
        NANOSECONDS(ChronoUnit.NANOS, singular("ns"), plural("nano"), plural("nanosecond"));

        private static final String PLURAL_SUFFIX = "s";

        private final List<String> labels;

        private final ChronoUnit unit;

        TimeUnit(ChronoUnit unit, String[]... labels) {
            this.unit = unit;
            this.labels =
                    Arrays.stream(labels)
                            .flatMap(Arrays::stream)
                            .collect(Collectors.toList());
        }

        /**
         * @param label the original label
         * @return the singular format of the original label
         */
        private static String[] singular(String label) {
            return new String[]{label};
        }

        /**
         * @param label the original label
         * @return both the singular format and plural format of the original label
         */
        private static String[] plural(String label) {
            return new String[]{label, label + PLURAL_SUFFIX};
        }

        public static String getAllUnits() {
            return Arrays.stream(TimeUnit.values())
                    .map(TimeUnit::createTimeUnitString)
                    .collect(Collectors.joining(", "));
        }

        private static String createTimeUnitString(TimeUnit timeUnit) {
            return timeUnit.name() + ": (" + String.join(" | ", timeUnit.getLabels()) + ")";
        }
    }

    private static ChronoUnit toChronoUnit(java.util.concurrent.TimeUnit timeUnit) {
        switch (timeUnit) {
            case NANOSECONDS:
                return ChronoUnit.NANOS;
            case MICROSECONDS:
                return ChronoUnit.MICROS;
            case MILLISECONDS:
                return ChronoUnit.MILLIS;
            case SECONDS:
                return ChronoUnit.SECONDS;
            case MINUTES:
                return ChronoUnit.MINUTES;
            case HOURS:
                return ChronoUnit.HOURS;
            case DAYS:
                return ChronoUnit.DAYS;
        }
        return null;
    }
}
