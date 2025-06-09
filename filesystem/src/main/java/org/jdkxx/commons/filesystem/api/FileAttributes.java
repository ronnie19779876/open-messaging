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

    default int getPermissionMask() {
        throw new UnsupportedOperationException();
    }

    default boolean isLink() {
        return false;
    }

    default boolean isSymbolicLink() {
        return false;
    }

    default int uid() {
        throw new UnsupportedOperationException();
    }

    default int gid() {
        throw new UnsupportedOperationException();
    }

    default <T extends FileAttributes> T as(Class<T> type) {
        return type.cast(this);
    }

    default boolean isReg() {
        return false;
    }
}
