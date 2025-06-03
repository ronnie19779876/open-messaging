package org.jdkxx.commons.filesystem.api;

import java.io.IOException;

public interface ChannelPool {
    <T extends FileSystemChannel> T get() throws IOException;

    <T extends FileSystemChannel> T getOrCreate() throws IOException;

    void keepAlive() throws IOException;

    void close() throws IOException;
}
