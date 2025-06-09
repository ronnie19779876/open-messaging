package org.jdkxx.commons.filesystem.sftp;

import com.jcraft.jsch.SftpATTRS;
import org.jdkxx.commons.filesystem.FileEntry;
import org.jdkxx.commons.filesystem.api.FileAttributes;

public class SftpFileEntry extends FileEntry {
    private final FileAttributes attributes;

    public SftpFileEntry(String path, SftpATTRS attributes) {
        super(path);
        this.attributes = new SftpFileAttributes(attributes);
    }

    @Override
    public FileAttributes attributes() {
        return attributes;
    }
}
