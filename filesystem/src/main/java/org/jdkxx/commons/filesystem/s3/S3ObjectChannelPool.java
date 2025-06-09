package org.jdkxx.commons.filesystem.s3;

import org.jdkxx.commons.filesystem.api.ChannelPool;
import org.jdkxx.commons.filesystem.api.FileSystemChannel;
import org.jdkxx.commons.filesystem.config.FileSystemEnvironment;
import org.jdkxx.commons.filesystem.pool.Pool;

import java.io.IOException;
import java.io.InterruptedIOException;

public class S3ObjectChannelPool implements ChannelPool {
    private final Pool<S3ObjectChannel, IOException> pool;

    S3ObjectChannelPool(String endpoint, FileSystemEnvironment environment) throws IOException {
        this.pool = new S3ObjectChannelPoolBuilder().endpoint(endpoint).build(environment);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends FileSystemChannel> T get() throws IOException {
        try {
            return (T) pool.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            InterruptedIOException ioe = new InterruptedIOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends FileSystemChannel> T getOrCreate() throws IOException {
        return (T) pool.acquireOrCreate();
    }

    @Override
    public void keepAlive() throws IOException {
        // Actually, no need to do anything; channels are validated using a keep-alive signal by the forAllIdleObjects call
        pool.forAllIdleObjects(channel -> {
            // does nothing
        });
    }

    @Override
    public void close() throws IOException {
        pool.shutdown();
    }
}
