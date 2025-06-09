package org.jdkxx.commons.filesystem.s3;

import org.jdkxx.commons.filesystem.FileEntry;
import org.jdkxx.commons.filesystem.api.FileAttributes;

public final class S3ObjectFileEntry extends FileEntry {
    private final S3ObjectFileAttributes attrs;

    public S3ObjectFileEntry(String path, S3ObjectFileAttributes attrs) {
        super(path);
        this.attrs = attrs;
    }

    @Override
    public FileAttributes attributes() {
        return attrs;
    }
}
