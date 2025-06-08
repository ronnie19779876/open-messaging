package org.jdkxx.commons.filesystem.s3;

import org.jdkxx.commons.filesystem.config.Messages;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Objects;

public class S3FileChannel implements SeekableByteChannel {
    private final Channel channel;
    private final long size;
    private long position;

    private S3FileChannel(Channel channel, long size, long position) {
        this.channel = channel;
        this.size = size;
        this.position = position;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int read = ((ReadableByteChannel) channel).read(dst);
        if (read > 0) {
            position += read;
        }
        return read;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int written = ((WritableByteChannel) channel).write(src);
        position += written;
        return written;
    }

    @Override
    public long position() throws IOException {
        return position;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        throw Messages.unsupportedOperation(SeekableByteChannel.class, "position");
    }

    @Override
    public long size() throws IOException {
        return size;
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        throw Messages.unsupportedOperation(SeekableByteChannel.class, "truncate");
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InputStream is;
        private OutputStream os;
        private long size;
        private long position;

        public Builder inputStream(InputStream is) {
            Objects.requireNonNull(is);
            this.is = is;
            return this;
        }

        public Builder outputStream(OutputStream os) {
            Objects.requireNonNull(os);
            this.os = os;
            return this;
        }

        public Builder size(long size) {
            if (size < 0) {
                throw new IllegalArgumentException(size + " < 0");
            }
            this.size = size;
            return this;
        }

        public Builder position(long position) {
            if (position < 0) {
                throw new IllegalArgumentException(position + " < 0");
            }
            this.position = position;
            return this;
        }

        public S3FileChannel build() {
            Channel channel = null;
            if (is != null) {
                channel = Channels.newChannel(is);
            } else if (os != null) {
                channel = Channels.newChannel(os);
            }
            if (channel == null) {
                throw new IllegalArgumentException("Channel is null");
            }
            return new S3FileChannel(channel, size, position);
        }
    }
}
