package org.jdkxx.commons.filesystem.sftp;

import com.jcraft.jsch.SftpATTRS;
import org.jdkxx.commons.filesystem.api.FileAttributes;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Set;

public class SftpFileAttributes implements FileAttributes {
    private final SftpATTRS attrs;

    public SftpFileAttributes(SftpATTRS attrs) {
        this.attrs = attrs;
    }

    @Override
    public boolean isReg() {
        return attrs.isReg();
    }

    @Override
    public boolean isLink() {
        return attrs.isLink();
    }

    @Override
    public boolean isDir() {
        return attrs.isDir();
    }

    @Override
    public long getMTime() {
        return attrs.getMTime();
    }

    @Override
    public long getLastAccessTime() {
        return attrs.getATime();
    }

    @Override
    public long getCreationTime() {
        return 0;
    }

    @Override
    public long getSize() {
        return attrs.getSize();
    }

    @Override
    public Set<PosixFilePermission> getPermissions() {
        return Collections.emptySet();
    }

    @Override
    public int getPermissionMask() {
        return this.attrs.getPermissions();
    }

    @Override
    public int uid() {
        return this.attrs.getUId();
    }

    @Override
    public int gid() {
        return this.attrs.getGId();
    }
}
