package org.jdkxx.commons.filesystem.s3;

import com.amazonaws.Protocol;
import org.jdkxx.commons.filesystem.config.FileSystemEnvironment;
import org.jdkxx.commons.filesystem.pool.Pool;

import java.io.IOException;

public class S3ChannelPoolBuilder {
    private String endpoint;

    public S3ChannelPoolBuilder endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public Pool<S3Channel, IOException> build(FileSystemEnvironment environment) throws IOException {
        return new Pool<>(environment.getPoolConfig(), () -> new S3Channel(endpoint, environment));
    }
}
