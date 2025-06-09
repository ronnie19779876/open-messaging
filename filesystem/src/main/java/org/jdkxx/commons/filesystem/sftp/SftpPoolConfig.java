package org.jdkxx.commons.filesystem.sftp;

import org.jdkxx.commons.filesystem.pool.PoolConfig;

import java.time.Duration;
import java.util.Optional;

public final class SftpPoolConfig {
    private static final SftpPoolConfig DEFAULT_CONFIG = custom().build();

    // By wrapping PoolConfig and not using it directly, we are not tied to one specific pool implementation

    private final PoolConfig config;

    private SftpPoolConfig(Builder builder) {
        config = builder.configBuilder.build();
    }

    /**
     * Returns the maximum time to wait when acquiring client connections.
     *
     * @return An {@link Optional} describing the maximum time to wait when acquiring client connections,
     * or {@code Optional#empty()} to wait indefinitely.
     */
    public Optional<Duration> maxWaitTime() {
        return config.maxWaitTime();
    }

    /**
     * Returns the maximum time that client connections can be idle.
     *
     * @return An {@link Optional} describing the maximum time that client connections can be idle,
     * or {@link Optional#empty()} if client connections can be idle indefinitely.
     */
    public Optional<Duration> maxIdleTime() {
        return config.maxIdleTime();
    }

    /**
     * Returns the initial pool size. This is the number of idle client connections to start with.
     *
     * @return The initial pool size.
     */
    public int initialSize() {
        return config.initialSize();
    }

    /**
     * Returns the maximum pool size. This is the maximum number of client connections, both idle and currently in use.
     *
     * @return The maximum pool size.
     */
    public int maxSize() {
        return config.maxSize();
    }

    PoolConfig config() {
        return config;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getSimpleName()
                + "[maxWaitTime=" + maxWaitTime().orElse(null)
                + ",maxIdleTime=" + maxIdleTime().orElse(null)
                + ",initialSize=" + initialSize()
                + ",maxSize=" + maxSize()
                + "]";
    }

    /**
     * Returns a default {@link SftpPoolConfig} object. This has the same configuration as an object returned by {@code custom().build()}.
     *
     * @return A default {@link SftpPoolConfig} object.
     * @see #custom()
     */
    public static SftpPoolConfig defaultConfig() {
        return DEFAULT_CONFIG;
    }

    /**
     * Returns a new builder for creating {@link SftpPoolConfig} objects.
     *
     * @return A new builder for creating {@link SftpPoolConfig} objects.
     */
    public static Builder custom() {
        return new Builder();
    }

    /**
     * A builder for {@link SftpPoolConfig} objects.
     *
     * @author Rob Spoor
     * @since 3.0
     */
    public static final class Builder {

        private final PoolConfig.Builder configBuilder;

        private Builder() {
            configBuilder = PoolConfig.custom();
        }

        /**
         * Sets the maximum time to wait when acquiring client connections.
         * If {@code null} or {@linkplain Duration#isNegative() negative}, acquiring client connections should block until a connection is available.
         * The default is to wait indefinitely.
         *
         * @param maxWaitTime The maximum wait time.
         * @return This builder.
         */
        public Builder withMaxWaitTime(Duration maxWaitTime) {
            configBuilder.withMaxWaitTime(maxWaitTime);
            return this;
        }

        /**
         * Sets the maximum time that client connections can be idle. The default is indefinite.
         *
         * @param maxIdleTime The maximum idle time, or {@code null} if client connections can be idle indefinitely.
         * @return This builder.
         */
        public Builder withMaxIdleTime(Duration maxIdleTime) {
            configBuilder.withMaxIdleTime(maxIdleTime);
            return this;
        }

        /**
         * Sets the initial pool size. This is the number of idle client connections to start with. The default is 1.
         * <p>
         * If the {@linkplain #withMaxSize(int) maximum pool size} is smaller than the given initial size, it will be set to be equal to the given
         * initial size.
         *
         * @param initialSize The initial pool size.
         * @return This builder.
         * @throws IllegalArgumentException If the initial size is negative.
         */
        public Builder withInitialSize(int initialSize) {
            configBuilder.withInitialSize(initialSize);
            return this;
        }

        /**
         * Sets the maximum pool size. This is the maximum number of client connections, both idle and currently in use. The default is 5.
         * <p>
         * If the {@linkplain #withInitialSize(int) initial pool size} is larger than the given maximum size, it will be set to be equal to the given
         * maximum size.
         *
         * @param maxSize The maximum pool size.
         * @return This builder.
         * @throws IllegalArgumentException If the given size is not positive.
         */
        public Builder withMaxSize(int maxSize) {
            configBuilder.withMaxSize(maxSize);
            return this;
        }

        /**
         * Creates a new {@link SftpPoolConfig} object based on the settings of this builder.
         *
         * @return The created {@link SftpPoolConfig} object.
         */
        public SftpPoolConfig build() {
            return new SftpPoolConfig(this);
        }
    }
}
