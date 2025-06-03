package org.jdkxx.commons.filesystem.api;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public interface FileAttributes {
    boolean isDir();

    long getMTime();

    long getLastAccessTime();

    long getCreationTime();

    long getSize();

    Set<PosixFilePermission> getPermissions();

    default <T extends FileAttributes> T as(Class<T> type) {
        return type.cast(this);
    }
}
