package org.jdkxx.commons.filesystem.s3;

import org.jdkxx.commons.filesystem.config.FileSystemEnvironment;
import org.jdkxx.commons.filesystem.pool.Pool;

import java.io.IOException;

public class S3ObjectChannelPoolBuilder {
    private String endpoint;

    public S3ObjectChannelPoolBuilder endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public Pool<S3ObjectChannel, IOException> build(FileSystemEnvironment environment) throws IOException {
        return new Pool<>(environment.getPoolConfig(), () -> new S3ObjectChannel(endpoint, environment));
    }
}
