package org.jdkxx.commons.filesystem.api;

import org.jdkxx.commons.filesystem.FileEntry;
import org.jdkxx.commons.filesystem.config.OpenOptions;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.OpenOption;
import java.nio.file.attribute.AclEntry;
import java.util.Collection;
import java.util.List;

public interface FileSystemChannel extends Closeable {
    void storeFile(String path, InputStream local, Collection<? extends OpenOption> openOptions) throws IOException;

    boolean exists(String path);

    InputStream newInputStream(String path, OpenOptions options) throws IOException;

    OutputStream newOutputStream(String path, OpenOptions options) throws IOException;

    void rename(String source, String target) throws IOException;

    void mkdir(String path) throws IOException;

    void chown(String path, String id) throws IOException;

    void chmod(String path, List<AclEntry> acl) throws IOException;

    void delete(String path, boolean isDirectory) throws IOException;

    default void delete(String path) throws IOException {
        delete(path, false);
    }

    List<FileEntry> listFiles(String path) throws IOException;

    FileAttributes readAttributes(String path, boolean followLinks) throws IOException;

    void setMtime(String path, long time) throws IOException;

    void setAtime(String path, long time) throws IOException;

    void setCtime(String path, long time) throws IOException;

    default <T> T as(Class<T> type) {
        return type.cast(this);
    }
}
