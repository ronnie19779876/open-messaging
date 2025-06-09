package org.jdkxx.commons.filesystem.sftp;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Objects;

public class SftpFileStore extends FileStore {
    private final SftpFilePath path;

    SftpFileStore(SftpFilePath path) {
        this.path = Objects.requireNonNull(path);
    }

    @Override
    public String name() {
        return "/";
    }

    @Override
    public String type() {
        return "sftp";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public long getTotalSpace() throws IOException {
        return path.getTotalSpace();
    }

    @Override
    public long getUsableSpace() throws IOException {
        return path.getUsableSpace();
    }

    @Override
    public long getUnallocatedSpace() throws IOException {
        return path.getUnallocatedSpace();
    }

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return type == BasicFileAttributeView.class || type == PosixFileAttributeView.class;
    }

    @Override
    @SuppressWarnings("nls")
    public boolean supportsFileAttributeView(String name) {
        return "basic".equals(name) || "owner".equals(name) || "posix".equals(name);
    }

    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        Objects.requireNonNull(type);
        return null;
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
        if ("totalSpace".equals(attribute)) {
            return getTotalSpace();
        }
        if ("usableSpace".equals(attribute)) {
            return getUsableSpace();
        }
        if ("unallocatedSpace".equals(attribute)) {
            return getUnallocatedSpace();
        }
        throw new UnsupportedOperationException("SftpFileStore not support attribute (" + attribute + ")");
    }
}
