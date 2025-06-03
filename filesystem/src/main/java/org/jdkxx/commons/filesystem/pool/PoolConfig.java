package org.jdkxx.commons.filesystem.pool;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

public final class PoolConfig {
    private static final PoolConfig DEFAULT_CONFIG = custom().build();
    private final Duration maxWaitTime;
    private final long maxWaitTimeInNanos;
    private final Duration maxIdleTime;
    private final long maxIdleTimeInNanos;
    private final int initialSize;
    private final int maxSize;

    private PoolConfig(Builder builder) {
        maxWaitTime = builder.maxWaitTime;
        maxWaitTimeInNanos = maxWaitTime != null ? maxWaitTime.toNanos() : -1;
        maxIdleTime = builder.maxIdleTime;
        maxIdleTimeInNanos = maxIdleTime != null ? maxIdleTime.toNanos() : -1;
        initialSize = builder.initialSize;
        maxSize = builder.maxSize;
    }

    /**
     * Returns the maximum time to wait when acquiring objects.
     *
     * @return An {@link Optional} describing the maximum time to wait when acquiring objects, or {@code Optional#empty()} to wait indefinitely.
     */
    public Optional<Duration> maxWaitTime() {
        return Optional.ofNullable(maxWaitTime);
    }

    long maxWaitTimeInNanos() {
        return maxWaitTimeInNanos;
    }

    /**
     * Returns the maximum time that objects can be idle.
     *
     * @return An {@link Optional} describing the maximum time that objects can be idle,
     * or {@link Optional#empty()} if objects can be idle indefinitely.
     */
    public Optional<Duration> maxIdleTime() {
        return Optional.ofNullable(maxIdleTime);
    }

    boolean maxIdleTimeExceeded(PoolingObject<?> object) {
        return maxIdleTime != null && System.nanoTime() - object.idleSince() > maxIdleTimeInNanos;
    }

    /**
     * Returns the initial pool size. This is the number of idle objects to start with.
     *
     * @return The initial pool size.
     */
    public int initialSize() {
        return initialSize;
    }

    /**
     * Returns the maximum pool size. This is the maximum number of objects, both idle and currently in use.
     *
     * @return The maximum pool size.
     */
    public int maxSize() {
        return maxSize;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getSimpleName()
                + "[maxWaitTime=" + maxWaitTime
                + ",maxIdleTime=" + maxIdleTime
                + ",initialSize=" + initialSize
                + ",maxSize=" + maxSize
                + "]";
    }

    /**
     * Returns a default {@link PoolConfig} object. This has the same configuration as an object returned by {@code custom().build()}.
     *
     * @return A default {@link PoolConfig} object.
     * @see #custom()
     */
    public static PoolConfig defaultConfig() {
        return DEFAULT_CONFIG;
    }

    /**
     * Returns a new builder for creating {@link PoolConfig} objects.
     *
     * @return A new builder for creating {@link PoolConfig} objects.
     */
    public static Builder custom() {
        return new Builder();
    }

    /**
     * A builder for {@link PoolConfig} objects.
     *
     * @author Rob Spoor
     */
    public static final class Builder {

        private Duration maxWaitTime;
        private Duration maxIdleTime;
        private int initialSize;
        private int maxSize;

        public Builder() {
            maxWaitTime = null;
            maxIdleTime = null;
            initialSize = 5;
            maxSize = 10;
        }

        /**
         * Sets the maximum time to wait when acquiring objects using {@link Pool#acquire()} or {@link Pool#acquire(Supplier)}.
         * If {@code null} or {@linkplain Duration#isNegative() negative}, acquiring objects should block until an object is available.
         * The default is to wait indefinitely.
         *
         * @param maxWaitTime The maximum wait time.
         * @return This builder.
         */
        public Builder withMaxWaitTime(Duration maxWaitTime) {
            this.maxWaitTime = maxWaitTime == null || maxWaitTime.isNegative() ? null : maxWaitTime;
            return this;
        }

        /**
         * Sets the maximum time that objects can be idle. The default is indefinite.
         *
         * @param maxIdleTime The maximum idle time, or {@code null} if objects can be idle indefinitely.
         * @return This builder.
         */
        public Builder withMaxIdleTime(Duration maxIdleTime) {
            this.maxIdleTime = maxIdleTime;
            return this;
        }

        /**
         * Sets the initial pool size. This is the number of idle objects to start with. The default is 1.
         * <p>
         * If the {@linkplain #withMaxSize(int) maximum pool size} is smaller than the given initial size, it will be set to be equal to the given
         * initial size.
         *
         * @param initialSize The initial pool size.
         * @return This builder.
         * @throws IllegalArgumentException If the initial size is negative.
         */
        public Builder withInitialSize(int initialSize) {
            if (initialSize < 0) {
                throw new IllegalArgumentException(initialSize + " < 0");
            }
            this.initialSize = initialSize;
            maxSize = Math.max(initialSize, maxSize);
            return this;
        }

        /**
         * Sets the maximum pool size. This is the maximum number of objects, both idle and currently in use. The default is 5.
         * <p>
         * If the {@linkplain #withInitialSize(int) initial pool size} is larger than the given maximum size, it will be set to be equal to the given
         * maximum size.
         *
         * @param maxSize The maximum pool size.
         * @return This builder.
         * @throws IllegalArgumentException If the given size is not positive.
         */
        public Builder withMaxSize(int maxSize) {
            if (maxSize <= 0) {
                throw new IllegalArgumentException(initialSize + " <= 0");
            }
            this.maxSize = maxSize;
            initialSize = Math.min(initialSize, maxSize);
            return this;
        }

        /**
         * Creates a new {@link PoolConfig} object based on the settings of this builder.
         *
         * @return The created {@link PoolConfig} object.
         */
        public PoolConfig build() {
            return new PoolConfig(this);
        }
    }
}
